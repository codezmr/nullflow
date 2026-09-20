package com.codezmr.nullflow.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.codezmr.nullflow.AppLog
import com.codezmr.nullflow.MainActivity
import com.codezmr.nullflow.R
import com.codezmr.nullflow.data.FocusDatabase
import com.codezmr.nullflow.data.FocusSession
import com.codezmr.nullflow.vpn.FocusVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives the exact alarms fired by [ScheduleManager] and starts/stops the
 * shield on schedule.
 *
 * Two actions, two distinct PendingIntents (see ScheduleManager request codes):
 *  - ACTION_START_SESSION: arm the shield for the scheduled profile.
 *  - ACTION_STOP_SESSION:  tear the shield down at the window's end.
 *
 * SAFETY GATES (in order, on START):
 *  1. Idempotency - if the shield is already running, drop the intent. We must
 *     never double-establish the tunnel (the VpnService fd is single-instance).
 *  2. VPN permission - VpnService.prepare() must return null. If the user
 *     revoked the VPN permission, establish() would throw SecurityException in
 *     the background with no UI to recover. Instead we post a notification so
 *     the user knows the scheduled session did not start.
 *
 * RE-ARM: AlarmManager alarms are one-shot. At the end of onReceive we ask
 * [ScheduleManager] to compute and arm the NEXT occurrence of this schedule,
 * so a recurring window keeps firing day after day.
 */
class SessionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_START_SESSION = "com.codezmr.nullflow.action.START_SESSION"
        const val ACTION_STOP_SESSION = "com.codezmr.nullflow.action.STOP_SESSION"
        const val EXTRA_SCHEDULE_ID = "schedule_id"
        const val EXTRA_PROFILE_ID = "profile_id"

        private const val CHANNEL_ID = "schedule_status"
        private const val NOTIF_ID = 43
    }

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)
        val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
        AppLog.d("SessionReceiver: action=${intent.action} scheduleId=$scheduleId profileId=$profileId")

        when (intent.action) {
            ACTION_START_SESSION -> handleStart(context, scheduleId, profileId)
            ACTION_STOP_SESSION -> handleStop(context, scheduleId)
            else -> {
                AppLog.w("SessionReceiver: unknown action ${intent.action}")
                return
            }
        }
    }

    private fun handleStart(context: Context, scheduleId: Long, profileId: Long) {
        // GATE 1 - Idempotency. Never double-bind the tunnel.
        if (FocusVpnService.isShieldRunning) {
            AppLog.d("SessionReceiver: shield already running - dropping START (idempotent)")
            reArm(scheduleId)
            return
        }
        if (profileId <= 0L) {
            AppLog.w("SessionReceiver: START with no valid profileId - dropping")
            return
        }

        // GATE 2 - VPN permission. prepare() == null means already authorized.
        val prepareResult = VpnService.prepare(context)
        if (prepareResult != null) {
            AppLog.w("SessionReceiver: VPN permission NOT granted - cannot start scheduled session")
            postPermissionRevokedNotification(context)
            // Still re-arm so the schedule retries on its next occurrence.
            reArm(scheduleId)
            return
        }

        // Authorized + not running: start the shield exactly like BootReceiver.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val dao = FocusDatabase.get(context).focusDao()
                dao.clearActive()
                dao.setActive(profileId, true)
                dao.insertSession(
                    FocusSession(profileId = profileId, startTime = System.currentTimeMillis())
                )
                context.startForegroundService(FocusVpnService.startIntent(context, profileId))
                AppLog.d("SessionReceiver: scheduled shield started for profile $profileId")
            } catch (e: Exception) {
                AppLog.e("SessionReceiver: FAILED to start scheduled shield", e)
            } finally {
                // RE-ARM - schedule the next occurrence of this window.
                reArm(scheduleId)
            }
        }
    }

    private fun handleStop(context: Context, scheduleId: Long) {
        // Only stop if we're actually running (a manual stop may have already
        // happened, or the start may have failed - in which case there's nothing
        // to stop and we must not tear down an unrelated manual session).
        if (!FocusVpnService.isShieldRunning) {
            AppLog.d("SessionReceiver: shield not running - dropping STOP (idempotent)")
            reArm(scheduleId)
            return
        }
        context.startService(FocusVpnService.stopIntent(context))
        AppLog.d("SessionReceiver: scheduled shield stopped")
        reArm(scheduleId)
    }

    /**
     * Ask the scheduler to compute and arm the NEXT occurrence of this
     * schedule. No-op if the schedule was deleted or disabled in the meantime.
     */
    private fun reArm(scheduleId: Long) {
        if (scheduleId <= 0L) return
        try {
            ScheduleManager.reArmNext(scheduleId)
        } catch (e: Exception) {
            AppLog.e("SessionReceiver: re-arm failed for schedule $scheduleId", e)
        }
    }

    private fun postPermissionRevokedNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Schedule status",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                )
            }
            val contentIntent = PendingIntent.getActivity(
                context, 4,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield_hud)
                .setContentTitle("NullFlow Schedule Failed")
                .setContentText("VPN permission was revoked. The scheduled focus session did not start.")
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(android.app.Notification.CATEGORY_STATUS)
                .build()
            nm.notify(NOTIF_ID, notification)
            AppLog.d("SessionReceiver: 'permission revoked' notification posted")
        } catch (e: Exception) {
            AppLog.e("SessionReceiver: failed to post permission-revoked notification", e)
        }
    }
}
