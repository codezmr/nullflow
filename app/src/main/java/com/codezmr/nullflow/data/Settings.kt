package com.codezmr.nullflow.data

import android.content.Context

class Settings private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("nullflow_settings", Context.MODE_PRIVATE)

    // ---- Onboarding ----
    val hasOnboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)

    fun markOnboarded() {
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    // ---- Shield ----
    var defaultProfileId: Long?
        get() = prefs.getLong(KEY_DEFAULT_PROFILE, -1L).takeIf { it != -1L }
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_DEFAULT_PROFILE) else putLong(KEY_DEFAULT_PROFILE, value)
            }
        }

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START_BOOT, false)
        set(value) { prefs.edit().putBoolean(KEY_AUTO_START_BOOT, value).apply() }

    var autoStopMinutes: Int
        get() = prefs.getInt(KEY_AUTO_STOP_MIN, 0)
        set(value) { prefs.edit().putInt(KEY_AUTO_STOP_MIN, value).apply() }

    var blockOnlyScreenOn: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_SCREEN_ON, true)
        set(value) { prefs.edit().putBoolean(KEY_BLOCK_SCREEN_ON, value).apply() }

    // ---- Schedule ----
    var scheduleEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCHEDULE_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_SCHEDULE_ENABLED, value).apply() }

    var scheduleStartHour: Int
        get() = prefs.getInt(KEY_SCHEDULE_START, 9)
        set(value) { prefs.edit().putInt(KEY_SCHEDULE_START, value).apply() }

    var scheduleEndHour: Int
        get() = prefs.getInt(KEY_SCHEDULE_END, 18)
        set(value) { prefs.edit().putInt(KEY_SCHEDULE_END, value).apply() }

    var scheduleDays: Int
        get() = prefs.getInt(KEY_SCHEDULE_DAYS, 0b1111100)
        set(value) { prefs.edit().putInt(KEY_SCHEDULE_DAYS, value).apply() }

    // ---- Preferences ----
    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) { prefs.edit().putBoolean(KEY_HAPTICS, value).apply() }

    var compactMode: Boolean
        get() = prefs.getBoolean(KEY_COMPACT, false)
        set(value) { prefs.edit().putBoolean(KEY_COMPACT, value).apply() }

    var accentColor: String
        get() = prefs.getString(KEY_ACCENT, "cyan") ?: "cyan"
        set(value) { prefs.edit().putString(KEY_ACCENT, value).apply() }

    // ---- Developer ----
    var verboseLogging: Boolean
        get() = prefs.getBoolean(KEY_VERBOSE_LOG, false)
        set(value) { prefs.edit().putBoolean(KEY_VERBOSE_LOG, value).apply() }

    // ---- Diagnostics ----
    // File logging is ON by default (debug phase) so issues can be shared as
    // a log file. Users can switch it off in Settings → Diagnostics.
    var fileLoggingEnabled: Boolean
        get() = prefs.getBoolean(KEY_FILE_LOG, true)
        set(value) { prefs.edit().putBoolean(KEY_FILE_LOG, value).apply() }

    // ---- OEM kill warning ----
    // Set to true when we detect the OS killed the shield mid-session. Drives
    // the persistent red warning card on the dashboard. Deliberately NOT
    // derived from the last session's aborted_by_system flag — that would keep
    // the card stuck on screen until the user completes a new session. The
    // user clears it by tapping "Fix Settings" or "Dismiss".
    var showOemKillWarning: Boolean
        get() = prefs.getBoolean(KEY_OEM_KILL_WARNING, false)
        set(value) { prefs.edit().putBoolean(KEY_OEM_KILL_WARNING, value).apply() }

    companion object {
        private const val KEY_ONBOARDED = "has_onboarded"
        private const val KEY_DEFAULT_PROFILE = "default_profile_id"
        private const val KEY_AUTO_START_BOOT = "auto_start_boot"
        private const val KEY_AUTO_STOP_MIN = "auto_stop_minutes"
        private const val KEY_BLOCK_SCREEN_ON = "block_only_screen_on"
        private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        private const val KEY_SCHEDULE_START = "schedule_start_hour"
        private const val KEY_SCHEDULE_END = "schedule_end_hour"
        private const val KEY_SCHEDULE_DAYS = "schedule_days"
        private const val KEY_HAPTICS = "haptics_enabled"
        private const val KEY_COMPACT = "compact_mode"
        private const val KEY_ACCENT = "accent_color"
        private const val KEY_VERBOSE_LOG = "verbose_logging"
        private const val KEY_FILE_LOG = "file_logging_enabled"
        private const val KEY_OEM_KILL_WARNING = "show_oem_kill_warning"

        @Volatile
        private var INSTANCE: Settings? = null

        fun get(context: Context): Settings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Settings(context).also { INSTANCE = it }
            }
        }
    }
}
