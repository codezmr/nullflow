package com.codezmr.nullflow.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.codezmr.nullflow.AppLog

/**
 * "Don'tKillMyApp" intent router.
 *
 * Chinese OEMs (Xiaomi, Oppo, Vivo, OnePlus) ship proprietary autostart /
 * background-start settings that aggressively kill background apps. The
 * standard Android "Ignore battery optimizations" screen does NOT work on
 * these devices — you must open the OEM's specific (often hidden) settings
 * menu.
 *
 * These OEMs frequently rename their internal components across OS updates,
 * so every proprietary launch is wrapped in try-catch and falls back to the
 * standard Android battery settings, then to the App Info screen.
 */
object OemSettingsHelper {

    /**
     * Route the user to the correct autostart / battery settings screen for
     * their device. Always succeeds (worst case: App Info screen).
     */
    fun navigateToAutoStartOrBattery(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        var success = false

        try {
            val intent = Intent()
            when (manufacturer) {
                "xiaomi", "poco", "redmi" -> {
                    intent.component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
                "oppo", "realme" -> {
                    intent.component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
                "vivo" -> {
                    intent.component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                }
                "oneplus" -> {
                    intent.component = ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.AllowAutoLaunchActivity"
                    )
                }
                else -> {
                    // Samsung, Pixel, Motorola, etc. — standard Android path.
                    success = launchStandardBatterySettings(context)
                    return
                }
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            success = true
            AppLog.d("OemSettingsHelper: launched OEM autostart menu for '$manufacturer'")

        } catch (e: Exception) {
            // OEM changed the package name or the component isn't present.
            AppLog.e("OemSettingsHelper: OEM intent failed for '$manufacturer' → falling back", e)
            success = false
        }

        if (!success) {
            launchStandardBatterySettings(context)
        }
    }

    /**
     * Standard Android battery-optimization settings. Falls back to the App
     * Info screen if even that intent is unavailable.
     */
    private fun launchStandardBatterySettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            AppLog.d("OemSettingsHelper: launched standard battery settings")
            true
        } catch (e: Exception) {
            // Absolute fallback: App Info screen.
            AppLog.e("OemSettingsHelper: battery settings failed → App Info screen", e)
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } catch (e2: Exception) {
                AppLog.e("OemSettingsHelper: App Info screen also failed", e2)
                false
            }
        }
    }
}
