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
    val appName: String,
    /**
     * Cumulative count of deflected connection attempts attributed to this app
     * across all sessions. Drives the "Distraction Radar" (top-6 most
     * intercepted apps).
     *
     * ATTRIBUTION NOTE (zero-data privacy): the blackhole tunnel drops packets
     * silently and the packet reader reads the raw byte stream WITHOUT parsing
     * IP headers (parsing would reveal which app sent what — a privacy leak).
     * So each deflected ping is attributed to blocked apps in ROUND-ROBIN
     * rotation. This is a privacy-correct proxy: it reflects the user's
     * aggregate distraction pressure per app without ever inspecting payloads.
     */
    val deflectedCount: Long = 0L
)
