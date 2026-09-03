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
    }

    private var interfaceFd: ParcelFileDescriptor? = null
    private var sessionStartedAt: Long = 0L
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startShield(intent?.getLongExtra(EXTRA_PROFILE_ID, -1L) ?: -1L)
        }
        return START_STICKY
    }

    /** Build the blackhole tunnel and go foreground. */
    private fun startShield(profileId: Long) {
        val packages = readBlockedPackages(profileId)
        Log.i(TAG, "startShield: profileId=$profileId blocked=${packages.size} pkgs")

        if (packages.isEmpty()) {
            Log.w(TAG, "No blocked apps for this profile — nothing to shield. Stopping.")
            stopSelf()
            return
        }

        val builder = Builder()
        builder.setSession("NullFlow")
        builder.addAddress("10.0.0.2", 32)
        builder.addRoute("0.0.0.0", 0) // route all VPN traffic to nowhere
        for (pkg in packages) {
            try {
                builder.addAllowedApplication(pkg)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Blocked app no longer installed: $pkg")
            }
        }
        builder.setBlocking(true) // silently DROP their packets

        val fd = try {
            builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "VPN establish failed", e)
            stopSelf()
            return
        }

        interfaceFd = fd
        sessionStartedAt = System.currentTimeMillis()
        startForeground(NOTIF_ID, buildNotification())
        startTimerUpdates()
        Log.i(TAG, "Shield ACTIVE — ${packages.size} apps blackholed")
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
        if (profile == null) return emptyList()
        return runBlocking { dao.getBlockedApps(profile.id) }.map { it.packageName }
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
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FocusVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.vpn_service_label))
            .setContentText("$timer · ${getString(R.string.vpn_service_text)}")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(0, "End session", stopIntent)
            .build()
    }

    /** Refresh the notification timer every 30s while active. */
    private fun startTimerUpdates() {
        serviceScope.launch {
            while (true) {
                delay(30_000)
                if (interfaceFd != null) {
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                        .notify(NOTIF_ID, buildNotification())
                }
            }
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "Shield OFF — releasing tunnel")
        interfaceFd?.close()
        interfaceFd = null
        serviceScope.cancel()
        super.onDestroy()
    }

    /** Called by the system if the VPN is revoked (e.g. user disables it in settings). */
    override fun onRevoke() {
        Log.w(TAG, "VPN revoked by system")
        stopSelf()
    }
}
