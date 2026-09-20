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
import android.widget.RemoteViews
import com.codezmr.nullflow.AppLog
import com.codezmr.nullflow.MainActivity
import com.codezmr.nullflow.R
import com.codezmr.nullflow.data.FocusDatabase
import com.codezmr.nullflow.data.InterceptLog
import java.io.FileInputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The "Blackhole" - NullFlow's Local Privacy Shield engine.
 *
 * HOW IT WORKS (crucial):
 * We do NOT use addDisallowedApplication. Instead we route ONLY the blocked
 * apps INTO the VPN tunnel, and the tunnel goes nowhere:
 *
 *   - addAddress("10.0.0.2", 32) + addRoute("0.0.0.0", 0)   [IPv4]
 *   - addAddress("fd00::2", 128) + addRoute("::", 0)         [IPv6]
 *       → everything that enters the VPN (v4 AND v6) is addressed into a
 *         dead-end. The v6 route is essential: Meta apps (Instagram/Facebook)
 *         default to IPv6 + QUIC, and without it their traffic bypasses the
 *         tunnel entirely.
 *   - addAllowedApplication(pkg) for each blocked package
 *       → ONLY these apps' traffic enters the tunnel.
 *   - setBlocking(true)
 *       → their packets are silently DROPPED (no RST, no error dialog -
 *         the app just sees "no internet", i.e. a single tick). This drops
 *         TCP and UDP/QUIC alike - the tunnel is protocol-agnostic.
 *
 * Every other app BYPASSES the VPN entirely and keeps full internet.
 * No data ever leaves the phone.
 */
class FocusVpnService : VpnService() {

    companion object {
        const val TAG = "NullFlow"
        const val ACTION_START = "com.codezmr.nullflow.action.START"
        const val ACTION_STOP = "com.codezmr.nullflow.action.STOP"
        // Quick Settings tile actions.
        const val ACTION_STOP_SHIELD = "com.codezmr.nullflow.action.STOP_SHIELD"
        const val ACTION_REFRESH_RULES = "com.codezmr.nullflow.action.REFRESH_RULES"
        // Tactical Pass: temporary 2-minute network leash.
        const val ACTION_EMERGENCY_PASS = "com.codezmr.nullflow.action.EMERGENCY_PASS"
        const val EXTRA_PROFILE_ID = "profile_id"

        /** Duration of the Tactical Pass in seconds. */
        const val EMERGENCY_PASS_SECONDS = 120

        /**
         * Extra on the notification content intent: when true, the dashboard
         * should auto-expand the Stats & History panel (the user tapped the
         * HUD to see details).
         */
        const val EXTRA_OPEN_STATS = "open_stats"

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

        /**
         * Build the ACTION_START intent for a given profile. Shared by the main
         * app hero toggle and the Quick Settings tile so both start the shield
         * identically.
         */
        fun startIntent(context: Context, profileId: Long): Intent =
            Intent(context, FocusVpnService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PROFILE_ID, profileId)

        /** Build the ACTION_STOP_SHIELD intent (used by the tile panel). */
        fun stopIntent(context: Context): Intent =
            Intent(context, FocusVpnService::class.java)
                .setAction(ACTION_STOP_SHIELD)

        /** Build the ACTION_REFRESH_RULES intent (used by the tile panel). */
        fun refreshIntent(context: Context): Intent =
            Intent(context, FocusVpnService::class.java)
                .setAction(ACTION_REFRESH_RULES)

        /** Build the ACTION_EMERGENCY_PASS intent (Tactical Pass). */
        fun emergencyPassIntent(context: Context): Intent =
            Intent(context, FocusVpnService::class.java)
                .setAction(ACTION_EMERGENCY_PASS)

        private val _heatState = kotlinx.coroutines.flow.MutableStateFlow(HeatState(0, 0f))

        /** Observed by the dashboard hero core (and any future surface). */
        val heatState: kotlinx.coroutines.flow.StateFlow<HeatState> = _heatState

        /**
         * Epoch-ms the current session started (0 when no session). Observed by
         * the QS tile panel to show a live focus timer. Updated when a session
         * starts and cleared on teardown.
         */
        private val _sessionStart = kotlinx.coroutines.flow.MutableStateFlow(0L)
        val sessionStart: kotlinx.coroutines.flow.StateFlow<Long> = _sessionStart

        /**
         * Remaining seconds in the Tactical Pass (0 when not active).
         * Observed by the notification HUD to show the countdown.
         */
        private val _passRemaining = kotlinx.coroutines.flow.MutableStateFlow(0)
        val passRemaining: kotlinx.coroutines.flow.StateFlow<Int> = _passRemaining

        /** True while the Tactical Pass is active (tunnel closed, countdown running). */
        @Volatile
        var isPassActive: Boolean = false
            private set

        /**
         * One-shot flag: when the user taps the notification HUD (which opens
         * the dashboard), the dashboard should auto-expand the Stats & History
         * panel so they land on the details they came for. Set to true by the
         * notification content intent, consumed (and reset) by MainScreen.
         */
        @Volatile
        var pendingStatsExpand: Boolean = false
    }

    private var interfaceFd: ParcelFileDescriptor? = null
    private var sessionStartedAt: Long = 0L
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Blocked-app Room IDs for the CURRENT tunnel, used for round-robin
     * attribution of deflected pings. Set when the tunnel is established
     * (startShield / refreshRules). Empty if the tunnel has no apps.
     */
    @Volatile
    private var blockedAppIds: List<Long> = emptyList()

    /**
     * Blocked-app package names for the CURRENT tunnel, parallel to
     * [blockedAppIds] (same order). Used to stamp the package name onto each
     * buffered [InterceptLog] for the Telemetry Console.
     */
    @Volatile
    private var blockedPackages: List<String> = emptyList()

    /**
     * Display name of the active mode for the CURRENT session (shown in the
     * notification HUD). Set in [readBlockedApps]. Defaults to "Focus" when
     * unknown so the HUD never shows a blank title.
     */
    @Volatile
    private var profileName: String = "Focus"

    /** Round-robin cursor for deflected-ping attribution (thread-safe). */
    private val attributionCursor = AtomicInteger(0)

    /**
     * In-memory buffer of intercepted-ping records, flushed to Room in batches.
     *
     * WHY BATCHING: the packet reader can observe many drops per second. A
     * per-packet Room insert would hammer the disk and stall the reader loop.
     * Instead the reader enqueues an [InterceptLog] (lock-free, O(1)) and a
     * dedicated flush coroutine drains the queue into a single multi-row
     * INSERT every [FLUSH_INTERVAL_MS]. The queue is unbounded but bounded in
     * practice: even 100 drops/s for an hour is only 360k small objects, and
     * the flush keeps it near-empty.
     */
    private val interceptBuffer = ConcurrentLinkedQueue<InterceptLog>()

    /** How often the intercept buffer is flushed to Room. */
    private val FLUSH_INTERVAL_MS = 2_000L

    /**
     * Live count of deflected connection attempts ("pings"). Incremented by the
     * packet-reader loop on each DETECTED connection attempt (2s-silence
     * heuristic - see startPacketReader). Thread-safe (the reader runs on IO,
     * the ticker reads it on the main/IO loop). Reset to 0 on every fresh
     * session start.
     */
    private val deflectedPings = AtomicInteger(0)

    /**
     * 60-second sliding window of intercept timestamps (epoch ms). Drives the
     * reactor-core HEAT level: the core's color/glow/pulse speed map to how
     * many connection attempts landed in the last minute. Entries older than
     * [HEAT_WINDOW_MS] are evicted on every timer tick (1s), so heat rises
     * under distraction pressure and cools when focus holds.
     *
     * Only the reader coroutine writes; the timer tick reads + evicts. Both
     * run on Dispatchers.IO but different coroutines, so the deque is guarded
     * by [heatLock].
     */
    private val heatWindow = ArrayDeque<Long>()
    private val heatLock = Any()

    /** Sliding-window length for the heat level. */
    private val HEAT_WINDOW_MS = 60_000L

    /**
     * Dedicated scope for the packet-reader loop. Kept SEPARATE from
     * serviceScope so a hot-swap (refreshRules) can restart the reader against
     * the new tunnel fd without cancelling the notification ticker.
     */
    private var readerScope: CoroutineScope? = null

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
        // 0) Cool the reactor core + clear the heat window + reset the timer.
        synchronized(heatLock) { heatWindow.clear() }
        _heatState.value = HeatState(0, 0f)
        _sessionStart.value = 0L
        sessionStartedAt = 0L
        // 1) Stop the timer loop + packet reader.
        serviceScope.cancel()
        readerScope?.cancel()
        readerScope = null
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
        // 4) Reconcile Room: end any running session + deactivate the profile.
        // This is the fix for the "End session" desync: the notification's
        // ACTION_STOP only stopped the service before, leaving a stale running
        // session in Room (so the tile showed OFF but the panel showed ON).
        // Now EVERY stop path cleans Room. Idempotent - no-op if no session.
        clearRoomSession()
        // 5) Stop the service.
        stopSelf()
        AppLog.d("teardown: COMPLETE - service stopping")
    }

    /**
     * End the running focus session (if any) and deactivate its profile in
     * Room. Runs on the service's IO scope. Safe to call when nothing is
     * running (it just no-ops). This keeps Room in lockstep with the service
     * no matter which surface (app toggle / notification / QS tile) stopped it.
     */
    private fun clearRoomSession() {
        // Use a FRESH scope: teardown() has already cancelled serviceScope, so
        // launching on it would be immediately cancelled. This one-shot scope
        // runs the Room cleanup to completion, then cancels itself.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val dao = FocusDatabase.get(this@FocusVpnService).focusDao()
                val running = dao.getRunningSession()
                if (running != null) {
                    dao.endSession(running.id, System.currentTimeMillis(), "completed")
                    dao.setActive(running.profileId, false)
                    AppLog.d("clearRoomSession: session ${running.id} ended, profile ${running.profileId} deactivated")
                } else {
                    AppLog.d("clearRoomSession: no running session (no-op)")
                }
            } catch (e: Exception) {
                AppLog.e("clearRoomSession FAILED", e)
            } finally {
                scope.cancel()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        AppLog.d("FocusVpnService.onStartCommand action=$action startId=$startId intent=${intent != null}")
        when (action) {
            ACTION_STOP, ACTION_STOP_SHIELD -> {
                AppLog.d("$action received → teardown (startId=$startId)")
                teardown()
                return START_NOT_STICKY
            }
            ACTION_REFRESH_RULES -> {
                // Hot-swap the blackhole rules WITHOUT tearing down the
                // foreground service / notification. Only meaningful while the
                // tunnel is live; if it's off this is a no-op (the panel only
                // sends this while the shield is ON).
                AppLog.d("ACTION_REFRESH_RULES received → hot-swap tunnel (startId=$startId)")
                refreshRules()
                return START_NOT_STICKY
            }
            ACTION_EMERGENCY_PASS -> {
                // Tactical Pass: temporarily close the tunnel for 2 minutes.
                AppLog.d("ACTION_EMERGENCY_PASS received → starting 2-min pass (startId=$startId)")
                handleEmergencyPass()
                return START_NOT_STICKY
            }
            // A NULL intent means the system is re-delivering after the process
            // died (sticky restart). We must NOT re-establish the tunnel in that
            // case - that's what kept bringing the VPN icon back after OFF.
            null -> {
                AppLog.w("onStartCommand with NULL intent (system re-delivery) - teardown, NOT re-establishing")
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
        // 1) Go foreground FIRST - satisfies the 5s contract no matter what.
        sessionStartedAt = System.currentTimeMillis()
        _sessionStart.value = sessionStartedAt
        try {
            startForeground(NOTIF_ID, buildNotification())
            AppLog.d("startForeground OK - notification posted (before tunnel)")
        } catch (e: Exception) {
            AppLog.e("startForeground FAILED (notification may not show)", e)
        }

        // 2) Now read the blocked apps (packages + Room IDs for attribution).
        AppLog.d("startShield: reading blocked packages for profileId=$profileId")
        val (packages, appIds) = try {
            readBlockedApps(profileId)
        } catch (e: Exception) {
            AppLog.e("startShield: readBlockedApps FAILED", e)
            teardown()
            return
        }
        AppLog.d("startShield: profileId=$profileId blocked=${packages.size} pkgs → $packages")

        if (packages.isEmpty()) {
            AppLog.w("No blocked apps for this profile - nothing to shield. Stopping. " +
                "(UI should tell the user to add apps first.)")
            teardown()
            return
        }
        // Capture the Room IDs + package names for round-robin deflected-ping
        // attribution (the Telemetry Console logs the package per intercept).
        blockedAppIds = appIds
        blockedPackages = packages
        attributionCursor.set(0)

        // 3) Build + establish the blackhole tunnel.
        val fd = establishTunnel(packages) ?: run {
            teardown()
            return
        }

        interfaceFd = fd
        shouldRun = true
        isShieldRunning = true
        deflectedPings.set(0) // fresh session → reset the HUD counter
        synchronized(heatLock) { heatWindow.clear() } // fresh session → cool core
        _heatState.value = HeatState(0, 0f)
        startPacketReader(fd)
        startInterceptFlusher()
        startTimerUpdates()
        startAutoStopTimer()
        AppLog.d("Shield ACTIVE - ${packages.size} apps blackholed. fd=$fd")
    }

    /**
     * If the user set an auto-stop duration in Settings, schedule a teardown
     * after that many minutes. No-op if autoStopMinutes == 0 (disabled).
     */
    private fun startAutoStopTimer() {
        val minutes = com.codezmr.nullflow.data.Settings.get(this).autoStopMinutes
        if (minutes <= 0) return
        AppLog.d("Auto-stop timer: ${minutes}min")
        serviceScope.launch {
            delay(minutes * 60_000L)
            if (shouldRun) {
                AppLog.d("Auto-stop: time's up, tearing down")
                teardown()
            }
        }
    }

    /**
     * Build a fresh blackhole tunnel for the given package list and establish
     * it. Returns the new interface fd, or null if establishment failed.
     * Shared by startShield() (initial start) and refreshRules() (hot-swap).
     */
    private fun establishTunnel(packages: List<String>): ParcelFileDescriptor? {
        val builder = Builder()
        builder.setSession("NullFlow")
        // IPv4: route the entire v4 internet into the blackhole tunnel.
        builder.addAddress("10.0.0.2", 32)
        builder.addRoute("0.0.0.0", 0)
        // IPv6: Meta apps (Instagram, Facebook) aggressively default to IPv6 +
        // QUIC (HTTP/3 over UDP). Without an explicit v6 route the OS sends all
        // IPv6 traffic straight out the real network, bypassing the tunnel -
        // which is exactly how Instagram "bypasses" the shield. Capture v6 too.
        // The dummy address is never used (setBlocking drops everything); it
        // just satisfies the builder's requirement for a v6 interface address.
        try {
            builder.addAddress("fd00::2", 128)
            builder.addRoute("::", 0)
            AppLog.d("tunnel: IPv6 route added (::/0 → blackhole)")
        } catch (e: Exception) {
            // Some OEM kernels reject a v6 route; degrade to IPv4-only rather
            // than failing the whole tunnel. Log loudly so the leak is visible.
            AppLog.w("tunnel: IPv6 route FAILED (falling back to IPv4-only) - " +
                "IPv6/QUIC traffic may bypass the shield :: ${e.message}")
        }
        for (pkg in packages) {
            try {
                builder.addAllowedApplication(pkg)
                AppLog.d("  allowed app: $pkg")
            } catch (e: PackageManager.NameNotFoundException) {
                AppLog.w("Blocked app no longer installed: $pkg")
            }
        }
        builder.setBlocking(true) // silently DROP their packets (TCP + UDP/QUIC)

        AppLog.d("calling builder.establish() ...")
        return try {
            builder.establish()
        } catch (e: Exception) {
            AppLog.e("VPN establish() FAILED", e)
            null
        }
    }

    /**
     * Launch the packet-reader loop for a given tunnel fd.
     *
     * The blackhole tunnel drops packets silently - but the OS still hands us
     * the byte stream on the interface fd. By actively READING that stream we
     * can count connection attempts a blocked app makes ("distractions
     * intercepted") and then discard the payload (strict zero-data privacy:
     * we never inspect, log, or store the bytes).
     *
     * CONNECTION HEURISTIC (Phase 4 counter refinement):
     * The tunnel fd is ONE multiplexed byte stream - a single TCP connection's
     * handshake + retries can produce many reads within milliseconds. Counting
     * every read inflates the metric (one background sync = dozens of "pings").
     * Instead we count a connection attempt only on a NEW burst: the first
     * read after >= [CONNECTION_SILENCE_MS] of quiet. A retry storm from one
     * connection stays inside the silence window and counts once.
     *
     * Runs on a dedicated IO scope so a hot-swap can restart it against the new
     * fd without touching the notification ticker.
     */
    private fun startPacketReader(fd: ParcelFileDescriptor) {
        // Cancel any previous reader (hot-swap case).
        readerScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        readerScope = scope
        scope.launch {
            val input = FileInputStream(fd.fileDescriptor)
            val buffer = ByteArray(32_767) // 32 KB - typical max UDP/TCP segment
            var lastReadAt = 0L
            AppLog.d("packet reader started")
            try {
                while (shouldRun) {
                    val read = input.read(buffer)
                    if (read == -1) {
                        // EOF - tunnel closed (teardown or revoke). Exit cleanly.
                        AppLog.d("packet reader: EOF (tunnel closed) - exiting")
                        break
                    }
                    if (read > 0) {
                        val now = System.currentTimeMillis()
                        val isNewBurst = now - lastReadAt >= CONNECTION_SILENCE_MS
                        lastReadAt = now
                        if (isNewBurst) {
                            // One detected connection attempt.
                            deflectedPings.incrementAndGet()
                            // Feed the 60s heat window (drives the reactor core).
                            synchronized(heatLock) { heatWindow.addLast(now) }
                            if (com.codezmr.nullflow.data.Settings.get(this@FocusVpnService).verboseLogging) {
                                AppLog.d("packet: new burst, ${read} bytes (total=${deflectedPings.get()})")
                            }
                            bufferDeflectedPing()
                        }
                    }
                    // read == 0 is rare on a FileInputStream; just loop.
                }
            } catch (e: Exception) {
                // fd closed underneath us (teardown) - expected, not an error.
                AppLog.d("packet reader stopped: ${e.message}")
            } finally {
                try { input.close() } catch (_: Exception) {}
                AppLog.d("packet reader loop exited (deflected=${deflectedPings.get()})")
            }
        }
    }

    /**
     * Quiet period (ms) that separates one connection attempt from the next.
     * Reads closer together than this are treated as the SAME connection's
     * handshake/retries and do not increment the counter or the heat window.
     */
    private val CONNECTION_SILENCE_MS = 2_000L

    /**
     * Attribute one deflected ping to a blocked app via round-robin and buffer
     * it for the Telemetry Console (flushed to Room in batches).
     *
     * WHY ROUND-ROBIN: the blackhole tunnel drops packets silently and the
     * reader sees only the raw byte stream - parsing IP headers to learn WHICH
     * app sent a packet would leak per-app usage (a privacy violation). So we
     * rotate attribution across the blocked apps. This is a privacy-correct
     * proxy for "which apps are pulling the user in": it reflects aggregate
     * distraction pressure without ever inspecting payloads.
     *
     * MUST be called from the reader coroutine (Dispatchers.IO). The enqueue
     * is non-blocking; the actual Room write happens in [flushInterceptBuffer].
     */
    private fun bufferDeflectedPing() {
        val ids = blockedAppIds
        if (ids.isEmpty()) return
        val idx = Math.floorMod(attributionCursor.getAndIncrement(), ids.size)
        val targetId = ids[idx]
        // Resolve the package name for this Room id (the tunnel was built from
        // the same list, so the index is stable for the life of this tunnel).
        val pkg = blockedPackages.getOrNull(idx) ?: return
        interceptBuffer.offer(InterceptLog(packageName = pkg, timestamp = System.currentTimeMillis()))
    }

    /**
     * Dedicated flush loop: drains [interceptBuffer] into Room in batches every
     * [FLUSH_INTERVAL_MS]. Runs on the service's IO scope so it dies with the
     * service (teardown cancels serviceScope). A final flush on shutdown makes
     * sure the last <2s of intercepts are not lost.
     */
    private fun startInterceptFlusher() {
        serviceScope.launch {
            while (shouldRun) {
                delay(FLUSH_INTERVAL_MS)
                flushInterceptBuffer()
            }
            // Final drain on shutdown - don't lose the tail of the session.
            flushInterceptBuffer()
            AppLog.d("intercept flusher exited")
        }
    }

    /**
     * Drain the in-memory intercept buffer into a single multi-row Room insert.
     * Safe to call when the buffer is empty (no-op).
     */
    private suspend fun flushInterceptBuffer() {
        val batch = mutableListOf<InterceptLog>()
        var item = interceptBuffer.poll()
        while (item != null && batch.size < 5000) {
            batch.add(item)
            item = interceptBuffer.poll()
        }
        if (batch.isEmpty()) return
        try {
            FocusDatabase.get(this).focusDao().insertInterceptLogs(batch)
            AppLog.d("flushed ${batch.size} intercept logs to Room")
        } catch (e: Exception) {
            AppLog.e("flushInterceptBuffer FAILED (${batch.size} records lost)", e)
        }
    }

    /**
     * Hot-swap the blackhole rules while the shield is already running.
     *
     * Android's VpnService tunnel CANNOT be edited after establish(), so the
     * only correct way to change the blocked-app set is: close the old fd,
     * re-establish a new tunnel with the new package list. The foreground
     * service + notification stay up the whole time (no flicker, no OS churn).
     *
     * Runs on the service's IO scope. If the new profile has no blocked apps,
     * we tear down (nothing to shield).
     */
    private fun refreshRules() {
        if (!isShieldRunning) {
            AppLog.w("refreshRules: shield not running - no-op (panel should only send this while ON)")
            return
        }
        serviceScope.launch {
            // 1) Read the new active profile's blocked apps (packages + IDs).
            val (packages, appIds) = try {
                readBlockedApps(-1L) // -1 → resolve the currently-active profile
            } catch (e: Exception) {
                AppLog.e("refreshRules: readBlockedApps FAILED", e)
                return@launch
            }
            AppLog.d("refreshRules: new active profile → ${packages.size} pkgs → $packages")

            if (packages.isEmpty()) {
                AppLog.w("refreshRules: new profile has 0 blocked apps → tearing down")
                teardown()
                return@launch
            }
            // Update the attribution set for the new tunnel.
            blockedAppIds = appIds
            blockedPackages = packages
            attributionCursor.set(0)

            // 2) Close the old tunnel, then establish a new one.
            try {
                interfaceFd?.close()
                AppLog.d("refreshRules: old tunnel fd closed")
            } catch (e: Exception) {
                AppLog.e("refreshRules: closing old fd failed", e)
            }
            interfaceFd = null

            val fd = establishTunnel(packages)
            if (fd == null) {
                AppLog.e("refreshRules: re-establish FAILED → tearing down")
                teardown()
                return@launch
            }
            interfaceFd = fd
            AppLog.d("refreshRules: tunnel hot-swapped - ${packages.size} apps blackholed. fd=$fd")
        }
    }

    /**
     * Tactical Pass: temporarily close the tunnel for [EMERGENCY_PASS_SECONDS]
     * seconds, giving the user a "network leash" to handle one urgent errand
     * without ending the entire focus session.
     *
     * - Closes the tunnel fd (restores normal network routing).
     * - Starts a 120s countdown (StateFlow observed by the notification HUD).
     * - At 0:00, re-establishes the tunnel (snap back).
     * - Session timer, heat window, and intercept counter stay alive.
     * - The packet reader is suspended (no fd to read from).
     */
    private fun handleEmergencyPass() {
        if (!isShieldRunning) {
            AppLog.w("handleEmergencyPass: shield not running - no-op")
            return
        }
        if (isPassActive) {
            AppLog.w("handleEmergencyPass: pass already active - no-op")
            return
        }

        // 1) Close the tunnel fd (restores normal network routing).
        try {
            interfaceFd?.close()
            AppLog.d("emergencyPass: tunnel fd closed - network restored")
        } catch (e: Exception) {
            AppLog.e("emergencyPass: closing fd failed", e)
        }
        interfaceFd = null

        // 2) Stop the packet reader (no fd to read from).
        readerScope?.cancel()
        readerScope = null

        // 3) Mark the pass as active and start the countdown.
        isPassActive = true
        _passRemaining.value = EMERGENCY_PASS_SECONDS
        AppLog.d("emergencyPass: STARTED - ${EMERGENCY_PASS_SECONDS}s leash active")

        // 4) Launch the countdown coroutine.
        serviceScope.launch {
            var remaining = EMERGENCY_PASS_SECONDS
            while (remaining > 0 && shouldRun && isPassActive) {
                delay(1_000)
                remaining--
                _passRemaining.value = remaining
                // Update the notification with the countdown.
                try {
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                        .notify(NOTIF_ID, buildNotification())
                } catch (e: Exception) {
                    AppLog.e("emergencyPass: notification update failed", e)
                }
            }

            // 5) Snap back: re-establish the tunnel.
            if (shouldRun && isPassActive) {
                AppLog.d("emergencyPass: countdown complete - snapping back")
                snapBack()
            }
        }
    }

    /**
     * Re-establish the tunnel after the Tactical Pass countdown expires.
     * The VpnService is still running and holds the system's active VPN slot,
     * so no re-authorization is needed.
     */
    private fun snapBack() {
        serviceScope.launch {
            // Re-establish the tunnel with the same blocked apps.
            val fd = establishTunnel(blockedPackages)
            if (fd == null) {
                AppLog.e("snapBack: re-establish FAILED → tearing down")
                isPassActive = false
                _passRemaining.value = 0
                teardown()
                return@launch
            }
            interfaceFd = fd
            isPassActive = false
            _passRemaining.value = 0
            // Restart the packet reader against the new fd.
            startPacketReader(fd)
            AppLog.d("snapBack: tunnel re-established - shield active again. fd=$fd")
            // Update the notification to show the normal HUD.
            try {
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(NOTIF_ID, buildNotification())
            } catch (e: Exception) {
                AppLog.e("snapBack: notification update failed", e)
            }
        }
    }

    /**
     * Read a profile's blocked apps. Returns a pair of (package names, Room IDs)
     * - the names build the tunnel, the IDs drive round-robin deflected-ping
     * attribution.
     */
    private fun readBlockedApps(profileId: Long): Pair<List<String>, List<Long>> {
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
            AppLog.w("readBlockedApps: no profile found (id=$profileId, no active profile)")
            return emptyList<String>() to emptyList<Long>()
        }
        val apps = runBlocking { dao.getBlockedApps(profile.id) }
        AppLog.d("readBlockedApps: profile='${profile.name}' (id=${profile.id}) → ${apps.size} apps")
        // Capture the mode name for the notification HUD.
        profileName = profile.name.ifBlank { "Focus" }
        // (package names, Room IDs) - same order, so the reader can index both
        // with the same round-robin cursor.
        return apps.map { it.packageName } to apps.map { it.id }
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
        val pings = deflectedPings.get()
        val modeName = profileName
        val passRemaining = _passRemaining.value

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(EXTRA_OPEN_STATS, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Distinct request code (2) so this never coalesces with the toggle's
        // stop intent (request code 1) - that coalescing is why "End session"
        // from the notification sometimes did nothing.
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, FocusVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // ---- Custom Focus Session HUD (RemoteViews) ----
        val views = RemoteViews(packageName, R.layout.notification_focus_hud)

        if (isPassActive && passRemaining > 0) {
            // Tactical Pass active: show countdown.
            val passMins = passRemaining / 60
            val passSecs = passRemaining % 60
            val passTimer = String.format("%02d:%02d", passMins, passSecs)
            views.setTextViewText(R.id.tv_title, "$modeName (Pass)")
            views.setTextViewText(R.id.tv_timer, "Resumes in $passTimer")
            views.setTextViewText(R.id.tv_pings, "$pings blocked")
        } else {
            // Normal mode: show session timer.
            views.setTextViewText(R.id.tv_title, modeName)
            views.setTextViewText(R.id.tv_timer, timer)
            views.setTextViewText(R.id.tv_pings, "$pings")
        }

        // Tapping the HUD body (title / timer / count / details hint) opens the
        // app dashboard where the user sees full stats.
        views.setOnClickPendingIntent(R.id.tv_title, contentIntent)
        views.setOnClickPendingIntent(R.id.tv_timer, contentIntent)
        views.setOnClickPendingIntent(R.id.tv_pings, contentIntent)
        views.setOnClickPendingIntent(R.id.tv_details_hint, contentIntent)
        // The "End Session" button stops the shield (works during pass too).
        views.setOnClickPendingIntent(R.id.btn_end_session, stopIntent)

        val contentText = if (isPassActive && passRemaining > 0) {
            "Pass active · resumes in ${passRemaining / 60}:${String.format("%02d", passRemaining % 60)}"
        } else {
            "$pings blocked · $timer"
        }

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_hud)
            .setContentTitle(modeName)
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        AppLog.d("buildNotification: HUD mode='$modeName' timer='$timer' pings='$pings' pass=$passRemaining ongoing=true")
        return notification
    }

    /**
     * Refresh the notification timer every 1s while active (live ticking).
     * Also maintains the 60s heat window: evicts stale timestamps and
     * publishes the current [HeatState] for the reactor core.
     */
    private fun startTimerUpdates() {
        serviceScope.launch {
            while (shouldRun) {
                delay(1_000)
                if (shouldRun) {
                    // During Tactical Pass: skip heat updates (no tunnel = no packets),
                    // but still update the notification (the pass countdown handles
                    // its own notification updates, so we skip here too).
                    if (isPassActive) {
                        continue
                    }

                    if (interfaceFd != null) {
                        // Evict heat-window entries older than the window and
                        // publish the live heat level (0..1).
                        val cutoff = System.currentTimeMillis() - HEAT_WINDOW_MS
                        val windowSize: Int
                        synchronized(heatLock) {
                            while (heatWindow.isNotEmpty() && heatWindow.first() < cutoff) {
                                heatWindow.removeFirst()
                            }
                            windowSize = heatWindow.size
                        }
                        // Saturation curve: 15 intercepts in the last minute = full
                        // heat. sqrt keeps low counts visibly cool and high counts
                        // from slamming to red on a single burst.
                        val heat = (kotlin.math.sqrt(windowSize.toFloat() / 15f)).coerceIn(0f, 1f)
                        _heatState.value = HeatState(deflectedPings.get(), heat)

                        try {
                            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                                .notify(NOTIF_ID, buildNotification())
                        } catch (e: Exception) {
                            AppLog.e("timer notification refresh failed", e)
                        }
                    }
                }
            }
            AppLog.d("timer loop exited (shouldRun=$shouldRun)")
        }
    }

    override fun onDestroy() {
        AppLog.d("FocusVpnService.onDestroy - Shield OFF, releasing tunnel (fd=${interfaceFd != null}, wasRunning=$isShieldRunning)")
        // Full deterministic cleanup (idempotent - safe if teardown() already ran).
        shouldRun = false
        isShieldRunning = false
        serviceScope.cancel()
        readerScope?.cancel()
        readerScope = null
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
        AppLog.d("FocusVpnService.onDestroy COMPLETE - isShieldRunning=false")
        super.onDestroy()
    }

    /** Called by the system if the VPN is revoked (e.g. user disables it in settings). */
    override fun onRevoke() {
        AppLog.w("VPN revoked by system (onRevoke) - teardown")
        teardown()
    }
}

/**
 * Live heat state exposed to the UI (dashboard hero core).
 *  - [count] session-total detected connection attempts
 *  - [heat]  0..1 normalized load from the 60s sliding window
 *
 * Top-level so the UI can reference it without a service instance.
 * The single service instance updates the companion [FocusVpnService.heatState]
 * flow on its 1s timer tick; it resets to (0, 0) on teardown.
 */
data class HeatState(val count: Int, val heat: Float)
