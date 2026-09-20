package com.codezmr.nullflow.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.codezmr.nullflow.AppLog
import com.codezmr.nullflow.data.FocusDatabase
import com.codezmr.nullflow.data.FocusSchedule
import java.util.Calendar

/**
 * Computes the next occurrence of a [FocusSchedule] and arms exact
 * AlarmManager alarms for its start and stop.
 *
 * WHY AlarmManager (not WorkManager): WorkManager batches work and is subject
 * to Doze, so a "6:00 PM" block could fire at 6:15 PM. setExactAndAllowWhileIdle()
 * wakes the CPU and fires on the exact minute - required for a strict focus tool.
 *
 * WHY minutes-from-midnight (not epoch): the schedule is "6:00 PM in whatever
 * timezone the device is in when it fires". We resolve the wall-clock time to
 * an epoch at ARM time using the device's current Calendar, so travel and DST
 * are handled by the OS clock, not by us.
 *
 * ONE-SHOT + RE-ARM: each alarm fires once. When it fires, SessionReceiver calls
 * [reArmNext] to schedule the following occurrence. This keeps the logic simple
 * and self-healing (a deleted/disabled schedule simply stops re-arming).
 */
object ScheduleManager {

    // Distinct request codes so the OS never coalesces the start and stop
    // PendingIntents (same action+component would otherwise overwrite).
    private const val REQ_START_BASE = 10_000
    private const val REQ_STOP_BASE = 20_000

    @Volatile
    private var appContext: Context? = null

    private val alarmManager: AlarmManager
        get() = requireNotNull(appContext)
            .getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val ctx: Context
        get() = requireNotNull(appContext) { "ScheduleManager.init() not called" }

    /** Idempotent init - caches the app context. Call once at app start. */
    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    /**
     * Arm the next start+stop pair for a schedule. Called when a schedule is
     * created/edited/enabled, and after every reboot (BootReceiver).
     */
    fun armNext(scheduleId: Long) {
        val schedule = FocusDatabase.get(ctx).focusDao().getScheduleSync(scheduleId)
            ?: run {
                AppLog.w("armNext: schedule $scheduleId not found - nothing to arm")
                return
            }
        if (!schedule.isEnabled) {
            AppLog.d("armNext: schedule $scheduleId disabled - not arming")
            return
        }
        val now = System.currentTimeMillis()
        val nextStart = nextOccurrence(schedule, now, isStart = true)
        val nextStop = nextOccurrence(schedule, now, isStart = false)
        if (nextStart == null || nextStop == null) {
            AppLog.w("armNext: schedule $scheduleId has no valid next occurrence")
            return
        }
        AppLog.d("armNext: schedule $scheduleId → start=$nextStart stop=$nextStop")
        setExact(scheduleId, isStart = true, atMillis = nextStart)
        setExact(scheduleId, isStart = false, atMillis = nextStop)
    }

    /**
     * Re-arm after an alarm fired. SessionReceiver calls this at the end of
     * onReceive so a recurring window keeps firing. No-op if the schedule was
     * deleted or disabled since the alarm was set.
     */
    fun reArmNext(scheduleId: Long) = armNext(scheduleId)

    /** Cancel both alarms for a schedule (on delete / disable). */
    fun cancel(scheduleId: Long) {
        cancelExact(scheduleId, isStart = true)
        cancelExact(scheduleId, isStart = false)
        AppLog.d("cancel: alarms cleared for schedule $scheduleId")
    }

    /** Re-arm every enabled schedule (called from BootReceiver after reboot). */
    fun reArmAll() {
        val schedules = FocusDatabase.get(ctx).focusDao().getEnabledSchedulesSync()
        AppLog.d("reArmAll: ${schedules.size} enabled schedule(s) to re-arm")
        schedules.forEach { armNext(it.id) }
    }

    // ---------- internals ----------

    private fun setExact(scheduleId: Long, isStart: Boolean, atMillis: Long) {
        val action = if (isStart) SessionReceiver.ACTION_START_SESSION
        else SessionReceiver.ACTION_STOP_SESSION
        val requestCode = if (isStart) REQ_START_BASE + scheduleId.toInt()
        else REQ_STOP_BASE + scheduleId.toInt()
        val intent = Intent(ctx, SessionReceiver::class.java)
            .setAction(action)
            .putExtra(SessionReceiver.EXTRA_SCHEDULE_ID, scheduleId)
            .putExtra(SessionReceiver.EXTRA_PROFILE_ID, profileIdFor(scheduleId))
        val pi = PendingIntent.getBroadcast(
            ctx, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        AppLog.d("setExact: $action schedule=$scheduleId at=$atMillis (in ${atMillis - System.currentTimeMillis()}ms)")
    }

    private fun cancelExact(scheduleId: Long, isStart: Boolean) {
        val action = if (isStart) SessionReceiver.ACTION_START_SESSION
        else SessionReceiver.ACTION_STOP_SESSION
        val requestCode = if (isStart) REQ_START_BASE + scheduleId.toInt()
        else REQ_STOP_BASE + scheduleId.toInt()
        val intent = Intent(ctx, SessionReceiver::class.java).setAction(action)
        val pi = PendingIntent.getBroadcast(
            ctx, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
    }

    private fun profileIdFor(scheduleId: Long): Long =
        FocusDatabase.get(ctx).focusDao().getScheduleSync(scheduleId)?.profileId ?: -1L

    /**
     * Find the next wall-clock occurrence of [schedule] strictly after [now].
     *
     * Scans up to 7 days ahead. For each candidate day in the bitmask, builds a
     * Calendar at the schedule's start (or end) minute and returns the first
     * timestamp that is in the future. Returns null if no day matches (e.g. a
     * malformed bitmask) - the caller treats that as "nothing to arm".
     */
    private fun nextOccurrence(schedule: FocusSchedule, now: Long, isStart: Boolean): Long? {
        val targetMinute = if (isStart) schedule.startMinute else schedule.endMinute
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, targetMinute / 60)
            set(Calendar.MINUTE, targetMinute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Try today first, then up to 6 more days.
        for (dayOffset in 0..6) {
            val candidate = cal.clone() as Calendar
            candidate.add(Calendar.DAY_OF_YEAR, dayOffset)
            if (!FocusSchedule.maskIncludes(schedule.daysBitmask, candidate.get(Calendar.DAY_OF_WEEK) - 1)) {
                continue
            }
            val ts = candidate.timeInMillis
            if (ts > now) return ts
        }
        return null
    }
}
