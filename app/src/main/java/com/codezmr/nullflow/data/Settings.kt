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

    // ---- Play Store compliance: affirmative VPN consent ----
    // Set only when the user explicitly taps "I Understand & Continue" on the
    // VpnDisclosureScreen, BEFORE the OS VPN permission prompt is shown.
    // Required by Google Play's VpnService policy (prominent disclosure with
    // affirmative user action). Never set implicitly.
    var vpnConsentGiven: Boolean
        get() = prefs.getBoolean(KEY_VPN_CONSENT, false)
        set(value) { prefs.edit().putBoolean(KEY_VPN_CONSENT, value).apply() }

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

    // ---- Preferences ----
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
    // File logging is OFF by default. Users can turn it on in
    // Settings → Diagnostics to capture a shareable log file.
    var fileLoggingEnabled: Boolean
        get() = prefs.getBoolean(KEY_FILE_LOG, false)
        set(value) { prefs.edit().putBoolean(KEY_FILE_LOG, value).apply() }

    // ---- OEM kill warning ----
    // Set to true when we detect the OS killed the shield mid-session. Drives
    // the persistent red warning card on the dashboard. Deliberately NOT
    // derived from the last session's aborted_by_system flag - that would keep
    // the card stuck on screen until the user completes a new session. The
    // user clears it by tapping "Fix Settings" or "Dismiss".
    var showOemKillWarning: Boolean
        get() = prefs.getBoolean(KEY_OEM_KILL_WARNING, false)
        set(value) { prefs.edit().putBoolean(KEY_OEM_KILL_WARNING, value).apply() }

    companion object {
        private const val KEY_ONBOARDED = "has_onboarded"
        private const val KEY_VPN_CONSENT = "vpn_consent_given"
        private const val KEY_DEFAULT_PROFILE = "default_profile_id"
        private const val KEY_AUTO_START_BOOT = "auto_start_boot"
        private const val KEY_AUTO_STOP_MIN = "auto_stop_minutes"
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
