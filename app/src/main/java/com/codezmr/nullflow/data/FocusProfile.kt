package com.codezmr.nullflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A named block list - the "IKEA effect" hook.
 * Users name their modes ("Gym Mode", "Deep Work", "Ghosting Everyone").
 * Only one profile can be active at a time (the one the hero toggle controls).
 */
@Entity(tableName = "focus_profiles")
data class FocusProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isActive: Boolean = false
)
