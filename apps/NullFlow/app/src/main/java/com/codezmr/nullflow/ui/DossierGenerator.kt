package com.codezmr.nullflow.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codezmr.nullflow.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The "Zero-Leak Shareable Dossier" — a 9:16 Instagram-ready share card that
 * summarizes the user's focus achievements.
 *
 *  - Rendered as a Compose layout (brand logo, total focus time, total
 *    interceptions, glassmorphic background).
 *  - Captured to a Bitmap via an off-screen [ComposeView] + [View.drawToBitmap].
 *  - Saved to [Context.cacheDir] on [Dispatchers.IO] (never blocks the UI).
 *
 * ZERO-LEAK: the card contains ONLY aggregate stats (total focus time + total
 * deflected pings) and the NullFlow brand. No app names, no package names, no
 * per-app breakdown — so sharing it leaks nothing about the user's habits.
 */
object DossierGenerator {

    /** 9:16 share-card dimensions in px (1080×1920 — standard Instagram story). */
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
            val bitmap = renderCard(appContext, totalFocusMs, totalDeflected, completedSessions)
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
     * Render the 9:16 share card into a Bitmap.
     *
     * Uses an off-screen [ComposeView] sized to the card dimensions, then
     * [View.drawToBitmap] to capture it. Runs on the caller's dispatcher
     * (Dispatchers.IO when called from [generateAndSave]).
     */
    private fun renderCard(
        context: Context,
        totalFocusMs: Long,
        totalDeflected: Long,
        completedSessions: Int
    ): Bitmap {
        val composeView = ComposeView(context)
        composeView.setContent {
            NullFlowTheme {
                DossierCard(
                    totalFocusMs = totalFocusMs,
                    totalDeflected = totalDeflected,
                    completedSessions = completedSessions
                )
            }
        }
        // Measure + layout the view at the exact card size.
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(CARD_WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(CARD_HEIGHT_PX, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, CARD_WIDTH_PX, CARD_HEIGHT_PX)
        // Force a frame so Compose lays out its content before capture.
        composeView.draw(Canvas())

        val bitmap = Bitmap.createBitmap(
            CARD_WIDTH_PX, CARD_HEIGHT_PX, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        composeView.draw(canvas)
        return bitmap
    }
}

/**
 * The 9:16 share card layout. Pure Compose — no side effects, so it can be
 * rendered off-screen for capture.
 */
@Composable
private fun DossierCard(
    totalFocusMs: Long,
    totalDeflected: Long,
    completedSessions: Int
) {
    val focusText = formatFocusDuration(totalFocusMs)
    val dateText = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0C10),
                        Color(0xFF12161F),
                        Color(0xFF0A0C10)
                    )
                )
            )
    ) {
        // Subtle glassmorphic glow (top-center radial).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00E5FF).copy(alpha = 0.12f),
                            Color(0xFF00E5FF).copy(alpha = 0f)
                        ),
                        center = Offset(0.5f, 0.25f),
                        radius = 900f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 72.dp, vertical = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- Brand mark ----
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF00E5FF), Color(0xFF4F8CFF))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "N",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0A0C10)
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "NullFlow",
                fontSize = 44.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE6EAF0)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Disconnect on your terms.",
                fontSize = 22.sp,
                color = Color(0xFFE6EAF0).copy(alpha = 0.5f)
            )

            Spacer(Modifier.height(96.dp))

            // ---- Hero stat: total focus time ----
            Text(
                text = focusText,
                fontSize = 88.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF00E5FF)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "of deep focus achieved",
                fontSize = 26.sp,
                color = Color(0xFFE6EAF0).copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(72.dp))

            // ---- Secondary stats row ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DossierStatCell(value = "$totalDeflected", label = "pings deflected")
                DossierStatCell(value = "$completedSessions", label = "focus sessions")
            }

            Spacer(Modifier.weight(1f))

            // ---- Footer: date + tagline ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1A1D24).copy(alpha = 0.6f))
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = dateText,
                        fontSize = 22.sp,
                        color = Color(0xFFE6EAF0).copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Stay focused. Stay free.",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF00E5FF)
                    )
                }
            }
        }
    }
}

@Composable
private fun DossierStatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE6EAF0)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 20.sp,
            color = Color(0xFFE6EAF0).copy(alpha = 0.45f)
        )
    }
}

private fun formatFocusDuration(ms: Long): String {
    if (ms <= 0) return "0m"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
