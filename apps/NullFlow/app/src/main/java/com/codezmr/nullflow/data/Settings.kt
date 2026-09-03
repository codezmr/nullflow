package com.codezmr.nullflow.data

import android.content.Context

/**
 * Lightweight SharedPreferences-backed settings.
 * Currently just the onboarding flag (first-launch gate).
 */
class Settings private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("nullflow_settings", Context.MODE_PRIVATE)

    val hasOnboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)

    fun markOnboarded() {
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    companion object {
        private const val KEY_ONBOARDED = "has_onboarded"

        @Volatile
        private var INSTANCE: Settings? = null

        fun get(context: Context): Settings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Settings(context).also { INSTANCE = it }
            }
        }
    }
}
