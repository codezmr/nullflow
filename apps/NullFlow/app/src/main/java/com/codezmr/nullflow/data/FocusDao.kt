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
