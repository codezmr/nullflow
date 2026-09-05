package com.codezmr.nullflow.data

/**
 * Aggregated interception count for one app, from
 * `SELECT packageName, COUNT(id) FROM intercept_logs GROUP BY packageName`.
 * Drives the interception donut + the threat ledger in the Telemetry Console.
 */
data class AppInterceptStats(
    val packageName: String,
    val interceptCount: Int
)
