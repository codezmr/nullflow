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

    /**
     * Top [limit] most-intercepted apps (by cumulative deflected count), for
     * the Distraction Radar. Apps with 0 interceptions are excluded (a radar
     * of all-zeros is meaningless). Ordered by deflectedCount DESC.
     */
    @Query(
        "SELECT * FROM blocked_apps " +
            "WHERE deflectedCount > 0 " +
            "ORDER BY deflectedCount DESC, appName COLLATE NOCASE ASC " +
            "LIMIT :limit"
    )
    fun observeTopIntercepted(limit: Int): Flow<List<BlockedApp>>

    /**
     * Atomically increment a blocked app's deflected count by [delta]. Called
     * by the VPN packet reader (round-robin attribution).
     */
    @Query("UPDATE blocked_apps SET deflectedCount = deflectedCount + :delta WHERE id = :id")
    suspend fun incrementDeflected(id: Long, delta: Long)

    /** Total deflected pings across ALL blocked apps (for the shareable dossier). */
    @Query("SELECT COALESCE(SUM(deflectedCount), 0) FROM blocked_apps")
    fun observeTotalDeflected(): Flow<Long>

    // ---------- FocusSession ----------

    @Insert
    suspend fun insertSession(session: FocusSession): Long

    @Query("UPDATE focus_sessions SET endTime = :endTime WHERE id = :id")
    suspend fun endSession(id: Long, endTime: Long)

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
}
