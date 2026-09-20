package com.codezmr.nullflow.data

/**
 * Aggregated interception count for one app.
 *
 * [appName] is the friendly display label (from blocked_apps when available,
 * otherwise the raw package name). Drives the interception donut, the threat
 * ledger, and the "Blocked by App" detail list in the Telemetry Console.
 */
data class AppInterceptStats(
    val packageName: String,
    val appName: String = packageName,
    val interceptCount: Int
)
