package com.codezmr.nullflow.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusDao {

    // ---------- FocusProfile ----------

    @Insert
    suspend fun insertProfile(profile: FocusProfile): Long

    @Query("UPDATE focus_profiles SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Query("UPDATE focus_profiles SET isActive = 0")
    suspend fun clearActive()

    @Query("SELECT * FROM focus_profiles ORDER BY id ASC")
    fun observeProfiles(): Flow<List<FocusProfile>>

    @Query("SELECT * FROM focus_profiles WHERE isActive = 1 LIMIT 1")
    fun observeActiveProfile(): Flow<FocusProfile?>

    @Query("SELECT * FROM focus_profiles WHERE id = :id")
    suspend fun getProfile(id: Long): FocusProfile?

    /**
     * Reactive list of every profile with its blocked-app count, for the
     * Quick Settings tile panel. One row per profile, ordered by id.
     */
    @Query(
        "SELECT p.id AS id, p.name AS name, p.isActive AS isActive, " +
            "(SELECT COUNT(*) FROM blocked_apps b WHERE b.profileId = p.id) AS appCount " +
            "FROM focus_profiles p ORDER BY p.id ASC"
    )
    fun observeProfilesWithAppCount(): Flow<List<ProfileWithCount>>

    /**
     * Reactive list of every profile with its blocked apps' package names,
     * for the Quick Settings tile panel's icon rows.
     */
    @Query(
        "SELECT p.id AS id, p.name AS name, p.isActive AS isActive, " +
            "b.packageName AS packageName, b.appName AS appName " +
            "FROM focus_profiles p " +
            "LEFT JOIN blocked_apps b ON b.profileId = p.id " +
            "ORDER BY p.id ASC, b.appName COLLATE NOCASE ASC"
    )
    fun observeProfilesWithApps(): Flow<List<ProfileWithAppsRow>>

    @Query("UPDATE focus_profiles SET name = :name WHERE id = :id")
    suspend fun renameProfile(id: Long, name: String)

    @Delete
    suspend fun deleteProfile(profile: FocusProfile)

    @Query("DELETE FROM blocked_apps WHERE profileId = :profileId")
    suspend fun deleteBlockedAppsByProfile(profileId: Long)

    @Query("DELETE FROM focus_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Long)

    // ---------- BlockedApp ----------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBlockedApps(apps: List<BlockedApp>): List<Long>

    @Query("DELETE FROM blocked_apps WHERE id = :id")
    suspend fun deleteBlockedApp(id: Long)

    @Query("SELECT * FROM blocked_apps WHERE profileId = :profileId ORDER BY appName COLLATE NOCASE ASC")
    fun observeBlockedApps(profileId: Long): Flow<List<BlockedApp>>

    @Query("SELECT * FROM blocked_apps WHERE profileId = :profileId")
    suspend fun getBlockedApps(profileId: Long): List<BlockedApp>

    @Query("DELETE FROM blocked_apps WHERE profileId = :profileId")
    suspend fun clearBlockedApps(profileId: Long)

    // ---------- InterceptLog (Focus Telemetry Console) ----------

    /**
     * Insert a batch of intercepted-ping records. Called by the VPN packet
     * reader's flush loop (buffered in memory, flushed every few seconds) so
     * the hot packet path never does per-packet disk I/O.
     */
    @Insert
    suspend fun insertInterceptLogs(logs: List<InterceptLog>)

    /**
     * Top [limit] most-intercepted apps, for the interception donut + threat
     * ledger. One row per package, ordered by intercept count DESC.
     */
    @Query(
        "SELECT i.packageName AS packageName, " +
            "COALESCE(b.appName, i.packageName) AS appName, " +
            "COUNT(i.id) AS interceptCount " +
            "FROM intercept_logs i " +
            "LEFT JOIN blocked_apps b ON b.packageName = i.packageName " +
            "GROUP BY i.packageName " +
            "ORDER BY interceptCount DESC, appName ASC " +
            "LIMIT :limit"
    )
    fun getInterceptionsByApp(limit: Int = 5): Flow<List<AppInterceptStats>>

    /** Total intercepted pings across all time ("Threats Neutralized" metric). */
    @Query("SELECT COUNT(id) FROM intercept_logs")
    fun getTotalIntercepts(): Flow<Int>

    /**
     * Per-app interception counts for ALL shielded apps (not just the top 5),
     * for the "Blocked by App" detail list. Joins blocked_apps to recover the
     * friendly display name (intercept_logs only stores the package name).
     * Apps with zero intercepts are included (shown as 0) so the user sees the
     * full picture of what's shielded. Ordered by count DESC, then name ASC.
     */
    @Query(
        "SELECT i.packageName AS packageName, " +
            "COALESCE(b.appName, i.packageName) AS appName, " +
            "COUNT(i.id) AS interceptCount " +
            "FROM intercept_logs i " +
            "LEFT JOIN blocked_apps b ON b.packageName = i.packageName " +
            "GROUP BY i.packageName " +
            "ORDER BY interceptCount DESC, appName ASC"
    )
    fun getInterceptionsByAppAll(): Flow<List<AppInterceptStats>>

    /**
     * The hour of day (0-23, local time) with the most intercepted pings -
     * the "Peak Focus Time" metric. Null when there are no intercepts yet.
     */
    @Query(
        "SELECT CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hourOfDay, " +
            "COUNT(id) AS cnt " +
            "FROM intercept_logs " +
            "GROUP BY hourOfDay " +
            "ORDER BY cnt DESC, hourOfDay ASC " +
            "LIMIT 1"
    )
    fun getPeakInterceptHour(): Flow<PeakHourStats?>

    /**
     * Per-day telemetry for the last 7 days (including today), for the 7-day
     * activity heatmap. [todayStart] is the local-midnight epoch-ms of TODAY;
     * the query emits exactly 7 rows covering [todayStart - 6 days] → today
     * (oldest→newest). Focus time comes from completed focus sessions,
     * intercepted pings from the intercept log. Days with no activity come
     * back as zeros (so the strip shows blank days, not future dates).
     */
    @Query(
        "SELECT d.ts AS dayStart, " +
            "COALESCE((SELECT SUM(s.endTime - s.startTime) FROM focus_sessions s " +
            "  WHERE s.endTime IS NOT NULL AND s.startTime >= d.ts AND s.startTime < d.ts + 86400000), 0) AS focusMs, " +
            "COALESCE((SELECT COUNT(i.id) FROM intercept_logs i " +
            "  WHERE i.timestamp >= d.ts AND i.timestamp < d.ts + 86400000), 0) AS interceptCount " +
            "FROM (SELECT :todayStart - 6 * 86400000 + (v.n * 86400000) AS ts " +
            "  FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 " +
            "  UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) v) d " +
            "ORDER BY d.ts ASC"
    )
    fun getDailyTelemetry(todayStart: Long): Flow<List<DailyFocusStats>>

    // ---------- FocusSession ----------

    @Insert
    suspend fun insertSession(session: FocusSession): Long

    @Query("UPDATE focus_sessions SET endTime = :endTime, endReason = :endReason WHERE id = :id")
    suspend fun endSession(id: Long, endTime: Long, endReason: String? = null)

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM focus_sessions WHERE endTime IS NULL LIMIT 1")
    fun observeRunningSession(): Flow<FocusSession?>

    @Query("SELECT * FROM focus_sessions WHERE endTime IS NULL LIMIT 1")
    suspend fun getRunningSession(): FocusSession?

    @Query("SELECT * FROM focus_sessions ORDER BY startTime DESC LIMIT :limit")
    fun observeRecentSessions(limit: Int): Flow<List<FocusSession>>

    /** Total focused milliseconds across all completed sessions. */
    @Query("SELECT COALESCE(SUM(endTime - startTime), 0) FROM focus_sessions WHERE endTime IS NOT NULL")
    fun observeTotalFocusedMs(): Flow<Long>

    /** Number of completed sessions (interruptions "allowed" = 0 by design). */
    @Query("SELECT COUNT(*) FROM focus_sessions WHERE endTime IS NOT NULL")
    fun observeCompletedCount(): Flow<Int>

    // ---------- FocusSchedule (recurring focus windows) ----------

    @Insert
    suspend fun insertSchedule(schedule: FocusSchedule): Long

    @Query("UPDATE focus_schedules SET startMinute = :startMinute, endMinute = :endMinute, " +
        "daysBitmask = :daysBitmask, isEnabled = :isEnabled WHERE id = :id")
    suspend fun updateSchedule(
        id: Long,
        startMinute: Int,
        endMinute: Int,
        daysBitmask: Int,
        isEnabled: Boolean
    )

    @Query("UPDATE focus_schedules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setScheduleEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM focus_schedules WHERE id = :id")
    suspend fun deleteSchedule(id: Long)

    @Query("DELETE FROM focus_schedules WHERE profileId = :profileId")
    suspend fun deleteSchedulesByProfile(profileId: Long)

    @Query("SELECT * FROM focus_schedules WHERE id = :id")
    suspend fun getSchedule(id: Long): FocusSchedule?

    /**
     * Synchronous variant for callers that already run off the main thread
     * (e.g. ScheduleManager arming alarms from a background context).
     */
    @Query("SELECT * FROM focus_schedules WHERE id = :id")
    fun getScheduleSync(id: Long): FocusSchedule?

    @Query("SELECT * FROM focus_schedules WHERE profileId = :profileId ORDER BY startMinute ASC")
    fun observeSchedules(profileId: Long): Flow<List<FocusSchedule>>

    /** Every schedule (all profiles), for the full-screen schedule manager. */
    @Query("SELECT * FROM focus_schedules ORDER BY startMinute ASC")
    fun observeAllSchedules(): Flow<List<FocusSchedule>>

    /**
     * Every ENABLED schedule, for re-arming alarms after a device reboot
     * (AlarmManager schedules are wiped by the kernel on boot).
     */
    @Query("SELECT * FROM focus_schedules WHERE isEnabled = 1")
    suspend fun getEnabledSchedules(): List<FocusSchedule>

    /** Synchronous variant for ScheduleManager (runs off the main thread). */
    @Query("SELECT * FROM focus_schedules WHERE isEnabled = 1")
    fun getEnabledSchedulesSync(): List<FocusSchedule>

    // ---------- Data management ----------

    @Query("DELETE FROM focus_sessions")
    suspend fun clearAllSessions()

    @Query("DELETE FROM intercept_logs")
    suspend fun clearAllIntercepts()

    @Query("SELECT * FROM focus_sessions WHERE endTime IS NOT NULL ORDER BY startTime DESC")
    suspend fun getAllCompletedSessions(): List<FocusSession>

    @Query("SELECT * FROM intercept_logs ORDER BY timestamp DESC LIMIT 1000")
    suspend fun getRecentIntercepts(): List<InterceptLog>
}
