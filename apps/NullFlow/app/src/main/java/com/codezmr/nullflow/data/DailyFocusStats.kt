package com.codezmr.nullflow.data

/**
 * One day's telemetry, for the 7-day activity heatmap.
 *
 * [dayStart] is the local-midnight epoch-ms of the day (so the UI can label
 * the cell). [focusMs] is focused time on that day; [interceptCount] is the
 * number of pings blackholed on that day. The heatmap's "Focus Score" is
 * derived from both (focus time weighted, interceptions as a secondary signal).
 */
data class DailyFocusStats(
    val dayStart: Long,
    val focusMs: Long,
    val interceptCount: Int
)
