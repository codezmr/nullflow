package com.codezmr.nullflow

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Logcat + optional file logger.
 *
 * Every call goes to logcat (tag "NullFlow") for live `adb logcat -s NullFlow`.
 * In DEBUG builds, every call is ALSO appended to a rolling file in the app's
 * external-files dir so the user can share a plain-text log without adb.
 *
 * File logging is ON by default (debug phase) and can be switched off in
 * Settings → Diagnostics. The file is capped at ~1MB and rotated to a single
 * .old file so it never grows unbounded.
 *
 * The crash handler (installed in MainActivity) routes uncaught exceptions
 * through [e] on the main thread so even a fatal crash leaves its stack
 * trace in logcat (and the file, when enabled).
 */
object AppLog {

    private const val TAG = "NullFlow"
    private const val LOG_FILE_NAME = "nullflow.log"
    private const val LOG_FILE_OLD = "nullflow.log.old"
    private const val MAX_LOG_BYTES = 1_000_000L

    @Volatile
    private var initialized = false

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var fileLogging = false

    /** Single-thread executor so file writes never block the caller. */
    private val fileExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "NullFlow-Log").apply { isDaemon = true }
    }

    private val tsFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /** Call once at app start (before anything else). Safe to call repeatedly. */
    fun init(context: Context) {
        appContext = context.applicationContext
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            // File logging defaults ON in debug builds; the Settings toggle
            // (fileLoggingEnabled) is read on first init and on every toggle.
            fileLogging = BuildConfig.DEBUG &&
                com.codezmr.nullflow.data.Settings.get(context).fileLoggingEnabled
            d("=== NullFlow log started ===")
            d("app version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            d("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            d("device: ${Build.MANUFACTURER} ${Build.MODEL}")
            d("file logging: ${if (fileLogging) "ON" else "OFF"}")
            initialized = true
        }
    }

    /** Enable/disable file logging at runtime (Settings toggle). */
    fun setFileLogging(enabled: Boolean) {
        fileLogging = enabled && BuildConfig.DEBUG
        d("file logging: ${if (fileLogging) "ON" else "OFF"}")
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

    /** Absolute path of the current log file (for the share action). */
    fun logFile(): File? = appContext?.let {
        File(it.getExternalFilesDir(null), LOG_FILE_NAME)
    }

    private fun log(level: String, msg: String) {
        when (level) {
            "D" -> Log.d(TAG, msg)
            "W" -> Log.w(TAG, msg)
            "E" -> Log.e(TAG, msg)
        }
        if (fileLogging) {
            val line = "${tsFormat.format(Date())} [$level] $msg"
            fileExecutor.execute { appendToFile(line) }
        }
    }

    private fun appendToFile(line: String) {
        val ctx = appContext ?: return
        try {
            val dir = ctx.getExternalFilesDir(null) ?: return
            val file = File(dir, LOG_FILE_NAME)
            // Rotate once when the cap is hit (keep a single .old backup).
            if (file.exists() && file.length() > MAX_LOG_BYTES) {
                val old = File(dir, LOG_FILE_OLD)
                if (old.exists()) old.delete()
                file.renameTo(old)
            }
            file.appendText(line + "\n")
        } catch (_: Exception) {
            // Never let logging crash the app.
        }
    }
}
