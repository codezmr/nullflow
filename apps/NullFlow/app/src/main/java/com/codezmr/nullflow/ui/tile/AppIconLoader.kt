package com.codezmr.nullflow.ui.tile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-memory cache of app icons (package name → Bitmap). Avoids re-decoding
 * icons from PackageManager on every recomposition of the tile panel.
 */
private val iconCache = HashMap<String, Bitmap>()

/** Load (and cache) an app's icon as a Bitmap. Returns null if not found. */
fun loadAppIcon(context: Context, packageName: String): Bitmap? {
    iconCache[packageName]?.let { return it }
    return try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        val drawable: Drawable = context.packageManager.getApplicationIcon(info)
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
        iconCache[packageName] = bmp
        bmp
    } catch (_: Exception) {
        null
    }
}

/**
 * A composable that loads an app icon off the main thread and exposes it as a
 * [Painter]. Returns a transparent placeholder painter until loaded.
 */
@Composable
fun rememberAppIconPainter(context: Context, packageName: String): Painter {
    var bitmap by remember(packageName) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(packageName) {
        bitmap = withContext(Dispatchers.IO) { loadAppIcon(context, packageName) }
    }
    return if (bitmap != null) {
        BitmapPainter(bitmap!!.asImageBitmap())
    } else {
        // Transparent placeholder (size handled by the caller).
        ColorPainter(Color.Transparent)
    }
}
