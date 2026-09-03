package com.codezmr.nullflow

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash-proof file logger.
 *
 * Primary target: PUBLIC Downloads folder (visible in the Files app, easy to
 * share as a .txt):
 *
 *   /storage/emulated/0/Download/NullFlow/nullflow.log
 *
 * Written via MediaStore.Downloads (API 29+, no permission needed for our own
 * files). If that ever fails, we FALL BACK to the app's external files dir so
 * logging NEVER crashes the app and we always have *some* log to analyze.
 *
 * Every call also goes to logcat (tag "NullFlow") for live `adb logcat -s NullFlow`.
 *
 * The crash handler (installed in MainActivity) routes uncaught exceptions
 * through [e] on the main thread — the same synchronized writer — so even a
 * fatal crash leaves its stack trace in the file.
 */
object AppLog {

    private const val TAG = "NullFlow"
    private const val FILE_NAME = "nullflow.log"
    private const val RELATIVE_DIR = "Download/NullFlow"

    @Volatile
    private var context: Context? = null

    @Volatile
    private var logUri: Uri? = null

    @Volatile
    private var fallbackFile: File? = null

    @Volatile
    private var initialized = false

    @Volatile
    private var lineCount = 0

    /** Call once at app start (before anything else). Safe to call repeatedly. */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            this.context = context.applicationContext
            try {
                logUri = resolveOrCreateLogFile(context.applicationContext)
                d("=== NullFlow log started ===")
                d("app version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                d("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                d("device: ${Build.MANUFACTURER} ${Build.MODEL}")
                d("log path: $RELATIVE_DIR/$FILE_NAME  (public Downloads folder)")
                initialized = true
            } catch (e: Exception) {
                // Public Downloads failed → fall back to app-private external dir.
                Log.e(TAG, "public log init failed, using fallback", e)
                try {
                    val dir = context.getExternalFilesDir(null)
                    fallbackFile = File(dir, FILE_NAME)
                    d("log path (FALLBACK): ${fallbackFile?.absolutePath}")
                } catch (e2: Exception) {
                    Log.e(TAG, "fallback log init failed too", e2)
                }
                initialized = true // don't retry forever
            }
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
        val line = "${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())} [$level] $msg"
        when (level) {
            "D" -> Log.d(TAG, msg)
            "W" -> Log.w(TAG, msg)
            "E" -> Log.e(TAG, msg)
        }
        val ctx = context ?: return
        try {
            synchronized(this) {
                val uri = logUri
                if (uri != null) {
                    // Append by opening in append mode.
                    ctx.contentResolver.openOutputStream(uri, "wa")?.use { out ->
                        out.write(line.toByteArray(Charsets.UTF_8))
                        out.write(System.lineSeparator().toByteArray(Charsets.UTF_8))
                    }
                    lineCount++
                    if (lineCount > 2000) rotate(ctx)
                    Unit
                } else {
                    // Fallback path.
                    fallbackFile?.let { f ->
                        FileOutputStream(f, true).use { out ->
                            out.write(line.toByteArray(Charsets.UTF_8))
                            out.write(System.lineSeparator().toByteArray(Charsets.UTF_8))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Never let logging crash the app.
        }
    }

    private fun rotate(ctx: Context) {
        try {
            val uri = logUri
            if (uri != null) {
                ctx.contentResolver.delete(uri, null, null)
                logUri = resolveOrCreateLogFile(ctx)
            } else {
                fallbackFile?.delete()
            }
            lineCount = 0
            d("log rotated")
        } catch (_: Exception) {
        }
    }

    /**
     * Finds an existing nullflow.log in Download/NullFlow, or creates one.
     * Uses contentResolver.insert directly (MediaStore.insert static is not in
     * the local android.jar).
     */
    private fun resolveOrCreateLogFile(ctx: Context): Uri {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?"
        val args = arrayOf(FILE_NAME, RELATIVE_DIR)

        // Look for an existing file.
        ctx.contentResolver.query(collection, arrayOf(MediaStore.Downloads._ID), selection, args, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    return MediaStore.Downloads.EXTERNAL_CONTENT_URI.buildUpon()
                        .appendPath(id.toString()).build()
                }
            }

        // Create a new one.
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_DIR)
        }
        val newUri = ctx.contentResolver.insert(collection, values)
            ?: throw IllegalStateException("could not create log file")
        // Write an empty line so the file exists on disk.
        ctx.contentResolver.openOutputStream(newUri)?.use { it.write("\n".toByteArray()) }
        return newUri
    }

    /** Human-readable location for showing in the UI. */
    fun currentPath(): String =
        logUri?.let { "$RELATIVE_DIR/$FILE_NAME" } ?: fallbackFile?.absolutePath ?: "unavailable"
}
