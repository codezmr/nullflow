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
        val settings = Settings.get(context)
        if (!settings.autoStartOnBoot) {
            AppLog.d("BootReceiver: autoStartOnBoot=false, skipping")
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
