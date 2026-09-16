package com.codezmr.nullflow.data

/**
 * The single hour-of-day (0-23, local time) with the most intercepted pings.
 * Drives the "Peak Focus Time" metric card (e.g. hourOfDay 9 → "09:00 AM").
 */
data class PeakHourStats(
    val hourOfDay: Int,
    val cnt: Int
)
