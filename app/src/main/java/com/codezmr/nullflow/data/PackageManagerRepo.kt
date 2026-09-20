package com.codezmr.nullflow.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A user-installed app, ready for the picker bottom sheet. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Bitmap
)

/**
 * Lists the user's installed apps for the picker.
 *
 * Filters OUT system apps (pre-installed / platform) so the sheet shows
 * only what the user actually installed - plus a small allow-list of
 * "target" apps (WhatsApp, Instagram, …) that are sometimes system-updated.
 *
 * Icons + labels are loaded once and cached in memory to prevent UI stutter
 * while scrolling the bottom sheet.
 */
class PackageManagerRepo(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /** Apps that are worth showing even if flagged as system (common on some OEMs). */
    private val targetPackages = setOf(
        "com.whatsapp",
        "com.instagram.android",
        "com.facebook.orca",
        "com.facebook.katana",
        "com.snapchat.android",
        "com.tiktok.android",
        "com.twitter.android",
        "com.discord",
        "com.telegram.org",
        "org.thunderbird",
        "com.slack",
        "net.devinvinci.openholo",
        "com.zhiliaoapp.musically",
        // Media / streaming
        "com.google.android.youtube",
        "com.netflix.mediaclient",
        "in.hotstar"
    )

    private val cache = mutableListOf<InstalledApp>()
    private var loaded = false

    /**
     * Returns the list of user apps. Loads (and caches) on first call.
     * Runs on [Dispatchers.IO] - call from a coroutine.
     */
    suspend fun getInstalledApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        if (!loaded) {
            val apps = mutableListOf<InstalledApp>()
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (info in installed) {
                val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                // Keep: user-installed apps, OR known targets (even if system-flagged).
                if (isSystem && !isUpdatedSystem && info.packageName !in targetPackages) continue
                if (info.packageName == context.packageName) continue // skip ourselves
                try {
                    val label = info.loadLabel(pm).toString()
                    val icon = loadIcon(info)
                    apps.add(InstalledApp(info.packageName, label, icon))
                } catch (_: Exception) {
                    // Skip apps we can't read (rare permission edge cases).
                }
            }
            apps.sortBy { it.label.lowercase() }
            cache.clear()
            cache.addAll(apps)
            loaded = true
        }
        cache
    }

    private fun loadIcon(info: ApplicationInfo): Bitmap {
        val drawable: Drawable = info.loadIcon(pm)
        val bmp = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val size = 96
            val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(b)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            b
        }
        // Some third-party apps (adaptive/animated drawables) render to an
        // all-transparent bitmap. Detect that and return a letter avatar so
        // the picker never shows a blank grey circle.
        return if (isBlankBitmap(bmp)) letterAvatar(info.loadLabel(pm).toString()) else bmp
    }

    /** True if every sampled pixel is (near-)transparent. */
    private fun isBlankBitmap(bmp: Bitmap): Boolean {
        if (bmp.width < 4 || bmp.height < 4) return true
        val stepX = bmp.width / 8
        val stepY = bmp.height / 8
        var samples = 0
        for (y in 0 until bmp.height step stepY.coerceAtLeast(1)) {
            for (x in 0 until bmp.width step stepX.coerceAtLeast(1)) {
                if ((bmp.getPixel(x, y) ushr 24) > 16) return false
                samples++
            }
        }
        return samples == 0
    }

    /** Simple colored circle + first letter, used when an app icon fails to load. */
    private fun letterAvatar(label: String): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        // Deterministic hue from the label so each app gets a stable color.
        val hue = (label.hashCode() and 0xFF) / 360f
        val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.45f, 0.55f))
        val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bg)
        val letter = label.firstOrNull { it.isLetterOrDigit() }?.toString() ?: "?"
        val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textSize = size * 0.5f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        val fm = text.fontMetrics
        canvas.drawText(
            letter,
            size / 2f,
            size / 2f - (fm.ascent + fm.descent) / 2f,
            text
        )
        return bmp
    }
}
