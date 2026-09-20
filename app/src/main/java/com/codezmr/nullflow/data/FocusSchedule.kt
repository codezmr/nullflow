package com.codezmr.nullflow.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A recurring focus window tied to a profile.
 *
 * Times are stored as MINUTES FROM MIDNIGHT (0..1439), NOT epoch timestamps.
 * This is timezone- and DST-safe: "6:00 PM" means 6:00 PM in whatever zone the
 * device is in when the alarm fires. Storing an epoch would break the moment
 * the user travels or the clock shifts for DST.
 *
 * Days of the week are a single 7-bit MASK (bit 0 = Sunday .. bit 6 = Saturday),
 * not a JSON array or a child table. SQLite compares one integer, and the
 * scheduler can test a day with a single bitwise AND.
 *
 * A profile may have MULTIPLE schedules (e.g. "weekdays 18:00-21:00" plus
 * "weekends 10:00-12:00"). Each row drives its own start/stop alarm pair.
 */
@Entity(
    tableName = "focus_schedules",
    indices = [Index("profileId")]
)
data class FocusSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val startMinute: Int,
    val endMinute: Int,
    val daysBitmask: Int,
    val isEnabled: Boolean = true
) {
    companion object {
        const val DAY_SUNDAY = 1 shl 0
        const val DAY_MONDAY = 1 shl 1
        const val DAY_TUESDAY = 1 shl 2
        const val DAY_WEDNESDAY = 1 shl 3
        const val DAY_THURSDAY = 1 shl 4
        const val DAY_FRIDAY = 1 shl 5
        const val DAY_SATURDAY = 1 shl 6

        /** Monday..Friday (the common "school/work days" preset). */
        const val WEEKDAYS = DAY_MONDAY or DAY_TUESDAY or DAY_WEDNESDAY or
            DAY_THURSDAY or DAY_FRIDAY

        /** All seven days. */
        const val EVERY_DAY = DAY_SUNDAY or DAY_MONDAY or DAY_TUESDAY or
            DAY_WEDNESDAY or DAY_THURSDAY or DAY_FRIDAY or DAY_SATURDAY

        /** True if [dayOfWeek] (Calendar.SUNDAY=0 .. SATURDAY=6) is in the mask. */
        fun maskIncludes(mask: Int, dayOfWeek: Int): Boolean =
            (mask and (1 shl dayOfWeek)) != 0
    }
}
