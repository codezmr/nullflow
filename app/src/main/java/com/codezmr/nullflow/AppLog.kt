package com.codezmr.nullflow

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Logcat-only logger.
 *
 * Every call goes to logcat (tag "NullFlow") for live `adb logcat -s NullFlow`.
 * No file is written — logs are read from the terminal via adb.
 *
 * The crash handler (installed in MainActivity) routes uncaught exceptions
 * through [e] on the main thread so even a fatal crash leaves its stack
 * trace in logcat.
 */
object AppLog {

    private const val TAG = "NullFlow"

    @Volatile
    private var initialized = false

    /** Call once at app start (before anything else). Safe to call repeatedly. */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            d("=== NullFlow log started ===")
            d("app version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            d("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            d("device: ${Build.MANUFACTURER} ${Build.MODEL}")
            initialized = true
        }
    }

    fun d(msg: String) = log("D", msg)
    fun w(msg: String) = log("W", msg)
    fun e(msg: String, tr: Throwable? = null) =
        log("E", msg + (tr?.let { " :: $it" } ?: ""))

    /** Log a full stack trace (used by the crash handler). */
    fun stackTrace(label: String, tr: Throwable) {
        val sb = StringBuilder()
        sb.append(label).append(" :: ").append(tr)
        for (el in tr.stackTrace) {
            sb.append("\n    at ").append(el)
        }
        tr.cause?.let { c ->
            sb.append("\nCaused by: ").append(c)
            for (el in c.stackTrace) {
                sb.append("\n    at ").append(el)
            }
        }
        log("E", sb.toString())
    }

    private fun log(level: String, msg: String) {
        when (level) {
            "D" -> Log.d(TAG, msg)
            "W" -> Log.w(TAG, msg)
            "E" -> Log.e(TAG, msg)
        }
    }
}
