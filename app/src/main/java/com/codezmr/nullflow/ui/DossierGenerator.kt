package com.codezmr.nullflow.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as GColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.codezmr.nullflow.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The "Zero-Leak Shareable Dossier" - a 9:16 Instagram-ready share card that
 * summarizes the user's focus achievements.
 *
 *  - Rendered as a Compose layout (brand logo, total focus time, total
 *    interceptions, glassmorphic background).
 *  - Captured to a Bitmap via an off-screen [ComposeView] + [View.drawToBitmap].
 *  - Saved to [Context.cacheDir] on [Dispatchers.IO] (never blocks the UI).
 *
 * ZERO-LEAK: the card contains ONLY aggregate stats (total focus time + total
 * deflected pings) and the NullFlow brand. No app names, no package names, no
 * per-app breakdown - so sharing it leaks nothing about the user's habits.
 */
object DossierGenerator {

    /** 9:16 share-card dimensions in px (1080×1920 - standard Instagram story). */
    private const val CARD_WIDTH_PX = 1080
    private const val CARD_HEIGHT_PX = 1920

    /**
     * Generate the share card and save it to the app's cache directory.
     *
     * @return the absolute file path of the saved PNG, or null on failure.
     */
    suspend fun generateAndSave(
        context: Context,
        totalFocusMs: Long,
        totalDeflected: Long,
        completedSessions: Int
    ): String? = withContext(Dispatchers.IO) {
        try {
            val appContext = context.applicationContext
            val bitmap = renderCard(totalFocusMs, totalDeflected, completedSessions)
            val file = File(appContext.cacheDir, "nullflow_dossier.png")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            AppLog.d("DossierGenerator: saved ${file.absolutePath} (${file.length()} bytes)")
            file.absolutePath
        } catch (e: Exception) {
            AppLog.e("DossierGenerator: FAILED", e)
            null
        }
    }

    /**
     * Render the 9:16 share card into a Bitmap using pure Android Canvas
     * drawing (no Compose - avoids the windowRecomposer crash when rendering
     * off-screen).
     */
    private fun renderCard(
        totalFocusMs: Long,
        totalDeflected: Long,
        completedSessions: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH_PX, CARD_HEIGHT_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = CARD_WIDTH_PX.toFloat()
        val h = CARD_HEIGHT_PX.toFloat()

        // ---- Background: vertical gradient (dark) ----
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(0xFF0A0C10.toInt(), 0xFF12161F.toInt(), 0xFF0A0C10.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // ---- Subtle cyan glow (top-center radial) ----
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                w / 2f, h * 0.25f, 900f,
                intArrayOf(0x1F00E5FF.toInt(), 0x0000E5FF.toInt()),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, glowPaint)

        // ---- Text paints ----
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE6EAF0.toInt()
            textAlign = Paint.Align.CENTER
        }
        val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00E5FF.toInt()
            textAlign = Paint.Align.CENTER
        }
        val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0A0C10.toInt()
            textAlign = Paint.Align.CENTER
        }

        val focusText = formatFocusDuration(totalFocusMs)
        val dateText = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date())

        // ---- Brand mark: cyan circle with "N" ----
        val brandRadius = 96f
        val brandCx = w / 2f
        val brandCy = 200f
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                brandCx - brandRadius, brandCy - brandRadius,
                brandCx + brandRadius, brandCy + brandRadius,
                intArrayOf(0xFF00E5FF.toInt(), 0xFF4F8CFF.toInt()),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(brandCx, brandCy, brandRadius, brandPaint)
        darkPaint.textSize = 52f
        darkPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("N", brandCx, brandCy + 18f, darkPaint)

        // ---- Brand name ----
        whitePaint.textSize = 44f
        canvas.drawText("NullFlow", w / 2f, brandCy + brandRadius + 50f, whitePaint)
        whitePaint.alpha = 128
        whitePaint.textSize = 22f
        canvas.drawText("Disconnect on your terms.", w / 2f, brandCy + brandRadius + 85f, whitePaint)
        whitePaint.alpha = 255

        // ---- Hero stat: total focus time ----
        cyanPaint.textSize = 88f
        canvas.drawText(focusText, w / 2f, 700f, cyanPaint)
        whitePaint.alpha = 178
        whitePaint.textSize = 26f
        canvas.drawText("of deep focus achieved", w / 2f, 750f, whitePaint)
        whitePaint.alpha = 255

        // ---- Secondary stats row ----
        val statY = 950f
        val statValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE6EAF0.toInt()
            textAlign = Paint.Align.CENTER
            textSize = 48f
        }
        val statLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x73E6EAF0.toInt() // 0.45 alpha
            textAlign = Paint.Align.CENTER
            textSize = 20f
        }
        // Left stat: distractions intercepted
        canvas.drawText("$totalDeflected", w * 0.3f, statY, statValuePaint)
        canvas.drawText("distractions intercepted", w * 0.3f, statY + 35f, statLabelPaint)
        // Right stat: focus sessions
        canvas.drawText("$completedSessions", w * 0.7f, statY, statValuePaint)
        canvas.drawText("focus sessions", w * 0.7f, statY + 35f, statLabelPaint)

        // ---- Footer: date + tagline (rounded rect) ----
        val footerRect = RectF(72f, h - 200f, w - 72f, h - 80f)
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x991A1D24.toInt() // 0.6 alpha
        }
        canvas.drawRoundRect(footerRect, 24f, 24f, footerPaint)
        whitePaint.alpha = 128
        whitePaint.textSize = 22f
        canvas.drawText(dateText, w / 2f, h - 155f, whitePaint)
        whitePaint.alpha = 255
        cyanPaint.textSize = 24f
        canvas.drawText("Stay focused. Stay free.", w / 2f, h - 115f, cyanPaint)

        return bitmap
    }

    private fun formatFocusDuration(ms: Long): String {
        if (ms <= 0) return "0m"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
