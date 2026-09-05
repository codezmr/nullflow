package com.codezmr.nullflow.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One intercepted (blackholed) connection attempt — a single "packet drop"
 * observed by the VPN packet reader.
 *
 * Every row here represents a REAL byte stream read from the tunnel fd and
 * silently dropped by the OS (setBlocking(true)). The payload is never
 * inspected or stored (zero-data privacy) — only the attribution target
 * (round-robin across the shielded apps) and the moment it happened.
 *
 * This table is the single source of truth for the Focus Telemetry Console:
 * the interception donut, the threat ledger, the "Threats Neutralized" metric,
 * and the 7-day activity heatmap all aggregate from it.
 */
@Entity(
    tableName = "intercept_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["packageName"])
    ]
)
data class InterceptLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestamp: Long
)
