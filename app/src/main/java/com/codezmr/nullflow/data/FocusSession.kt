package com.codezmr.nullflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One activation of the hero toggle = one focus session.
 * Drives the "Quantified Relief" stats: "45 minutes of deep focus achieved".
 * [endTime] is null while the session is still running.
 */
@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val startTime: Long,
    val endTime: Long? = null,
    /**
     * Why the session ended. Null while running.
     *  - "completed"          → ran to the planned end (strict mode) or user stopped it
     *  - "aborted_by_system"  → the OS killed the VPN service mid-session
     *  - "aborted_by_user"    → user broke the strict-mode lock and stopped early
     */
    val endReason: String? = null
)
