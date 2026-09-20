package com.codezmr.nullflow.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codezmr.nullflow.AppLog
import com.codezmr.nullflow.data.FocusDatabase
import com.codezmr.nullflow.data.Settings
import com.codezmr.nullflow.vpn.FocusVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Re-arm ALL enabled schedules FIRST. AlarmManager state is wiped by the
        // kernel on reboot, so every recurring window must be re-armed or it
        // silently stops firing after the next restart. This runs regardless of
        // autoStartOnBoot (schedules are independent of the boot-shield toggle).
        try {
            ScheduleManager.reArmAll()
        } catch (e: Exception) {
            AppLog.e("BootReceiver: re-arm schedules FAILED", e)
        }

        val settings = Settings.get(context)
        if (!settings.autoStartOnBoot) {
            AppLog.d("BootReceiver: autoStartOnBoot=false, skipping boot shield")
            return
        }
        val profileId = settings.defaultProfileId ?: return
        AppLog.d("BootReceiver: auto-starting shield for profile $profileId")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val dao = FocusDatabase.get(context).focusDao()
                dao.clearActive()
                dao.setActive(profileId, true)
                dao.insertSession(
                    com.codezmr.nullflow.data.FocusSession(
                        profileId = profileId,
                        startTime = System.currentTimeMillis()
                    )
                )
                context.startForegroundService(FocusVpnService.startIntent(context, profileId))
                AppLog.d("BootReceiver: shield started")
            } catch (e: Exception) {
                AppLog.e("BootReceiver: FAILED", e)
            }
        }
    }
}
