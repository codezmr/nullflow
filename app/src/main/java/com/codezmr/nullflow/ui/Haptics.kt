package com.codezmr.nullflow.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.codezmr.nullflow.data.Settings

object Haptics {

    private fun vibrator(context: Context): Vibrator? {
        if (!Settings.get(context).hapticsEnabled) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun vibratorFor(context: Context): Vibrator? = vibrator(context)

    fun tick(context: Context) {
        vibrator(context)?.vibrate(VibrationEffect.createOneShot(50, 150))
    }

    fun engage(context: Context) {
        vibrator(context)?.vibrate(VibrationEffect.createOneShot(100, 255))
    }

    fun disengage(context: Context) {
        vibrator(context)?.vibrate(VibrationEffect.createOneShot(70, 180))
    }

    fun thud(context: Context) {
        vibrator(context)?.vibrate(
            VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }
}

@Composable
fun rememberHaptics() = Haptics
