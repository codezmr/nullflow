package com.codezmr.nullflow.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.codezmr.nullflow.AppLog
import com.codezmr.nullflow.MainActivity
import com.codezmr.nullflow.R
import com.codezmr.nullflow.data.FocusDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The "Blackhole" — NullFlow's Local Privacy Shield engine.
 *
 * HOW IT WORKS (crucial):
 * We do NOT use addDisallowedApplication. Instead we route ONLY the blocked
 * apps INTO the VPN tunnel, and the tunnel goes nowhere:
 *
 *   - addAddress("10.0.0.2", 32) + addRoute("0.0.0.0", 0)
 *       → everything that enters the VPN is addressed into a dead-end.
 *   - addAllowedApplication(pkg) for each blocked package
 *       → ONLY these apps' traffic enters the tunnel.
 *   - setBlocking(true)
 *       → their packets are silently DROPPED (no RST, no error dialog —
 *         the app just sees "no internet", i.e. a single tick).
 *
 * Every other app BYPASSES the VPN entirely and keeps full internet.
 * No data ever leaves the phone.
 */
class FocusVpnService : VpnService() {

    companion object {
        const val TAG = "NullFlow"
        const val ACTION_START = "com.codezmr.nullflow.action.START"
        const val ACTION_STOP = "com.codezmr.nullflow.action.STOP"
        const val EXTRA_PROFILE_ID = "profile_id"

        private const val CHANNEL_ID = "focus_session"
        private const val NOTIF_ID = 42

        /**
         * True while the shield tunnel is actually established in a LIVE process.
         * Used to reconcile stale Room state on app restart: if the app is killed
         * while the shield is on, the service dies with it, so this resets to
         * false on the next process start and the UI can clear the stale session.
         */
        @Volatile
        var isShieldRunning: Boolean = false
            private set
    }

    private var interfaceFd: ParcelFileDescriptor? = null
    private var sessionStartedAt: Long = 0L
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Instance-level "should the shield be alive" flag. Unlike the static
     * isShieldRunning (which survives across the process), this is checked by
     * the timer loop so a STOP intent immediately halts notification updates
     * even if the service process lingers for a moment.
     */
    @Volatile
    private var shouldRun: Boolean = false

    override fun onCreate() {
        super.onCreate()
        AppLog.d("FocusVpnService.onCreate")
        createChannel()
    }

    /**
     * Deterministic teardown: stops the timer, closes the tunnel, removes the
     * foreground notification + VPN icon, and stops the service. Safe to call
     * multiple times (idempotent). This is the ONLY place we tear down, so the
     * notification can't linger no matter how many STOP intents arrive.
     */
    private fun teardown() {
        if (!shouldRun && interfaceFd == null) {
            AppLog.d("teardown: already stopped, no-op")
            return
        }
        AppLog.d("teardown: stopping shield (shouldRun=$shouldRun, fd=${interfaceFd != null})")
        shouldRun = false
        isShieldRunning = false
        // 1) Stop the timer loop.
        serviceScope.cancel()
        // 2) Close the tunnel.
        try {
            interfaceFd?.close()
            AppLog.d("teardown: tunnel fd closed")
        } catch (e: Exception) {
            AppLog.e("teardown: closing fd failed", e)
        }
        interfaceFd = null
        // 3) Remove foreground notification + VPN status-bar icon.
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
            AppLog.d("teardown: stopForeground(REMOVE) called")
        } catch (e: Exception) {
            AppLog.e("teardown: stopForeground failed", e)
        }
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(NOTIF_ID)
            AppLog.d("teardown: notification $NOTIF_ID cancelled")
        } catch (e: Exception) {
            AppLog.e("teardown: cancel notification failed", e)
        }
        // 4) Stop the service.
        stopSelf()
        AppLog.d("teardown: COMPLETE — service stopping")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        AppLog.d("FocusVpnService.onStartCommand action=$action startId=$startId intent=${intent != null}")
        when (action) {
            ACTION_STOP -> {
                AppLog.d("ACTION_STOP received → teardown (startId=$startId)")
                teardown()
                return START_NOT_STICKY
            }
            // A NULL intent means the system is re-delivering after the process
            // died (sticky restart). We must NOT re-establish the tunnel in that
            // case — that's what kept bringing the VPN icon back after OFF.
            null -> {
                AppLog.w("onStartCommand with NULL intent (system re-delivery) — teardown, NOT re-establishing")
                teardown()
                return START_NOT_STICKY
            }
            else -> startShield(intent.getLongExtra(EXTRA_PROFILE_ID, -1L))
        }
        // START_NOT_STICKY: if the system kills us, do NOT auto-restart the
        // shield. The user must explicitly toggle it back on.
        return START_NOT_STICKY
    }

    /**
     * Build the blackhole tunnel and go foreground.
     *
     * CRITICAL: startForeground() MUST be called within 5 seconds of
     * startForegroundService() or Android kills the app with
     * ForegroundServiceDidNotStartInTimeException. So we go foreground
     * IMMEDIATELY (before reading packages / establishing the tunnel),
     * then decide whether to stay active or stop.
     */
    private fun startShield(profileId: Long) {
        // 1) Go foreground FIRST — satisfies the 5s contract no matter what.
        sessionStartedAt = System.currentTimeMillis()
        try {
            startForeground(NOTIF_ID, buildNotification())
            AppLog.d("startForeground OK — notification posted (before tunnel)")
        } catch (e: Exception) {
            AppLog.e("startForeground FAILED (notification may not show)", e)
        }

        // 2) Now read the blocked apps.
        AppLog.d("startShield: reading blocked packages for profileId=$profileId")
        val packages = try {
            readBlockedPackages(profileId)
        } catch (e: Exception) {
            AppLog.e("startShield: readBlockedPackages FAILED", e)
            teardown()
            return
        }
        AppLog.d("startShield: profileId=$profileId blocked=${packages.size} pkgs → $packages")

        if (packages.isEmpty()) {
            AppLog.w("No blocked apps for this profile — nothing to shield. Stopping. " +
                "(UI should tell the user to add apps first.)")
            teardown()
            return
        }

        // 3) Build + establish the blackhole tunnel.
        val builder = Builder()
        builder.setSession("NullFlow")
        builder.addAddress("10.0.0.2", 32)
        builder.addRoute("0.0.0.0", 0) // route all VPN traffic to nowhere
        for (pkg in packages) {
            try {
                builder.addAllowedApplication(pkg)
                AppLog.d("  allowed app: $pkg")
            } catch (e: PackageManager.NameNotFoundException) {
                AppLog.w("Blocked app no longer installed: $pkg")
            }
        }
        builder.setBlocking(true) // silently DROP their packets

        AppLog.d("calling builder.establish() ...")
        val fd = try {
            builder.establish()
        } catch (e: Exception) {
            AppLog.e("VPN establish() FAILED", e)
            teardown()
            return
        }

        interfaceFd = fd
        shouldRun = true
        isShieldRunning = true
        startTimerUpdates()
        AppLog.d("Shield ACTIVE — ${packages.size} apps blackholed. fd=$fd")
    }

    private fun readBlockedPackages(profileId: Long): List<String> {
        val dao = FocusDatabase.get(this).focusDao()
        val profile = if (profileId > 0) {
            runBlocking { dao.getProfile(profileId) }
        } else {
            null
        } ?: runBlocking {
            // Fall back to the currently-active profile.
            dao.observeActiveProfile().first()
        }
        if (profile == null) {
            AppLog.w("readBlockedPackages: no profile found (id=$profileId, no active profile)")
            return emptyList()
        }
        val apps = runBlocking { dao.getBlockedApps(profile.id) }
        AppLog.d("readBlockedPackages: profile='${profile.name}' (id=${profile.id}) → ${apps.size} apps")
        return apps.map { it.packageName }
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val elapsed = if (sessionStartedAt > 0)
            System.currentTimeMillis() - sessionStartedAt else 0L
        val mins = elapsed / 60_000
        val secs = (elapsed / 1000) % 60
        val timer = String.format("%02d:%02d", mins, secs)

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Distinct request code (2) so this never coalesces with the toggle's
        // stop intent (request code 1) — that coalescing is why "End session"
        // from the notification sometimes did nothing.
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, FocusVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.vpn_service_label))
            .setContentText("$timer · ${getString(R.string.vpn_service_text)}")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(0, "End session", stopIntent)
            .build()
        AppLog.d("buildNotification: title='${getString(R.string.vpn_service_label)}' text='$timer · ${getString(R.string.vpn_service_text)}' ongoing=true")
        return notification
    }

    /** Refresh the notification timer every 30s while active. */
    private fun startTimerUpdates() {
        serviceScope.launch {
            while (shouldRun) {
                delay(30_000)
                if (shouldRun && interfaceFd != null) {
                    try {
                        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                            .notify(NOTIF_ID, buildNotification())
                    } catch (e: Exception) {
                        AppLog.e("timer notification refresh failed", e)
                    }
                }
            }
            AppLog.d("timer loop exited (shouldRun=$shouldRun)")
        }
    }

    override fun onDestroy() {
        AppLog.d("FocusVpnService.onDestroy — Shield OFF, releasing tunnel (fd=${interfaceFd != null}, wasRunning=$isShieldRunning)")
        // Full deterministic cleanup (idempotent — safe if teardown() already ran).
        shouldRun = false
        isShieldRunning = false
        serviceScope.cancel()
        try {
            interfaceFd?.close()
            AppLog.d("  tunnel fd closed")
        } catch (e: Exception) {
            AppLog.e("closing VPN fd failed", e)
        }
        interfaceFd = null
        // CRITICAL: remove the foreground notification + VPN status-bar icon.
        // Without this the "Local Privacy Shield is active" notification and the
        // VPN icon linger after the shield is turned off.
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
            AppLog.d("  stopForeground(REMOVE) called")
        } catch (e: Exception) {
            AppLog.e("stopForeground failed", e)
        }
        // Belt-and-suspenders: also explicitly cancel the notification in case
        // stopForeground didn't clear it on this device/OS version.
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(NOTIF_ID)
            AppLog.d("  notification $NOTIF_ID cancelled")
        } catch (e: Exception) {
            AppLog.e("cancel notification failed", e)
        }
        AppLog.d("FocusVpnService.onDestroy COMPLETE — isShieldRunning=false")
        super.onDestroy()
    }

    /** Called by the system if the VPN is revoked (e.g. user disables it in settings). */
    override fun onRevoke() {
        AppLog.w("VPN revoked by system (onRevoke) — teardown")
        teardown()
    }
}
