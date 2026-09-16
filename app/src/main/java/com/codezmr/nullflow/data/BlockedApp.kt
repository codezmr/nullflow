package com.codezmr.nullflow.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An app assigned to a focus profile. When the profile is active,
 * ONLY these packages lose internet (they're routed into the VPN blackhole).
 */
@Entity(
    tableName = "blocked_apps",
    indices = [Index(value = ["profileId", "packageName"], unique = true)]
)
data class BlockedApp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val packageName: String,
    val appName: String
)
