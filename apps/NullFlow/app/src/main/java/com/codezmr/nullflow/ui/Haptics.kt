package com.codezmr.nullflow.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Haptic feedback — the "pulling a heavy lever / locking a door" feel.
 *
 *  - [tick]      : light touch-down tick (50ms, medium amplitude)
 *  - [engage]    : heavy successful-activation thud (100ms, max amplitude)
 *  - [disengage] : medium release (70ms)
 */
object Haptics {

    private fun vibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun tick(context: Context) {
        vibrator(context)?.vibrate(VibrationEffect.createOneShot(50, 150))
    }

    fun engage(context: Context) {
        vibrator(context)?.vibrate(VibrationEffect.createOneShot(100, 255))
    }

    fun disengage(context: Context) {
        vibrator(context)?.vibrate(VibrationEffect.createOneShot(70, 180))
    }

    /**
     * Crisp "locking a physical latch" thud for the Focus Matrix tactile cards
     * (40ms, full amplitude).
     */
    fun thud(context: Context) {
        vibrator(context)?.vibrate(
            VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }
}

/** Convenience composable accessor. */
@Composable
fun rememberHaptics() = Haptics
