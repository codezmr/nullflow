package com.codezmr.nullflow.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager

/**
 * Battery-optimization helpers for keeping the shield service alive.
 *
 * Play Store policy: we must NOT use ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
 * (direct exemption request - restricted permission). Instead we route the user
 * to the system's battery-optimization settings screen
 * (ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) and let them make the choice.
 */
object SystemHealth {

    /** True if the OS will not kill us in the background for power savings. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Intent that opens the system "Ignore battery optimizations" settings list.
     * No special permission required - this is a plain settings screen.
     */
    fun batterySettingsIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .setPackage("com.android.settings")
            .putExtra(
                android.provider.Settings.EXTRA_APP_PACKAGE,
                context.packageName
            )

    /**
     * High-priority "Shield stopped unexpectedly" notification, posted when we
     * detect the OS killed the VPN mid-session. Uses the existing focus_session
     * channel so no new channel (and no new user prompt) is needed.
     */
    fun postShieldKilledNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
            val contentIntent = android.app.PendingIntent.getActivity(
                context, 10,
                Intent(context, com.codezmr.nullflow.MainActivity::class.java),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = android.app.Notification.Builder(
                context, "focus_session"
            )
                .setSmallIcon(com.codezmr.nullflow.R.drawable.ic_shield_hud)
                .setContentTitle("Shield stopped unexpectedly")
                .setContentText("The system ended the shield to save power. Open NullFlow to turn it back on.")
                .setStyle(android.app.Notification.BigTextStyle().bigText(
                    "The system ended the shield to save power. " +
                        "If this keeps happening, allow NullFlow to ignore battery " +
                        "optimizations (Settings → System Health)."
                ))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(android.app.Notification.CATEGORY_STATUS)
                .build()
            nm.notify(43, notification)
        } catch (e: Exception) {
            com.codezmr.nullflow.AppLog.e("postShieldKilledNotification FAILED", e)
        }
    }
}
