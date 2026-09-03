package com.codezmr.nullflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.codezmr.nullflow.data.FocusDatabase
import com.codezmr.nullflow.ui.AppPickerSheet
import com.codezmr.nullflow.ui.MainScreen
import com.codezmr.nullflow.ui.NullFlowTheme
import com.codezmr.nullflow.ui.OnboardingScreen
import com.codezmr.nullflow.vpn.FocusVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Logging + crash capture MUST be first — before anything can fail.
        AppLog.init(applicationContext)
        installCrashHandler()
        AppLog.d("MainActivity.onCreate (savedState=${savedInstanceState != null})")

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val dao = FocusDatabase.get(this).focusDao()

        // Reconcile stale state: if the app was killed while the shield was ON,
        // the service died with it (isShieldRunning == false on fresh process),
        // but Room still has a running session + active profile. Clear it so the
        // toggle isn't stuck showing "ON".
        if (!FocusVpnService.isShieldRunning) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val running = dao.getRunningSession()
                if (running != null) {
                    dao.endSession(running.id, System.currentTimeMillis())
                    dao.setActive(running.profileId, false)
                    AppLog.w("Reconciled stale session ${running.id} (service not running on app start)")
                }
            }
        }

        setContent {
            NullFlowTheme {
                // Welcome screen shows on EVERY app open. For returning users the
                // permission checklist is already checked, so it's a single
                // "Enter NullFlow" tap. (A fresh install still walks the setup.)
                var showOnboarding by remember { mutableStateOf(true) }

                if (showOnboarding) {
                    OnboardingScreen(onEnter = {
                        AppLog.d("onboarding complete → entering main screen")
                        showOnboarding = false
                    })
                } else {
                    // Picker sheet state lives here so MainScreen can open it.
                    var pickerProfileId by remember { mutableStateOf<Long?>(null) }

                    MainScreen(
                        dao = dao,
                        onOpenPicker = { profileId ->
                            AppLog.d("open app picker for profile $profileId")
                            pickerProfileId = profileId
                        }
                    )

                    if (pickerProfileId != null) {
                        AppPickerSheet(
                            dao = dao,
                            profileId = pickerProfileId!!,
                            onDismiss = { pickerProfileId = null }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppLog.d("MainActivity.onResume")
    }

    override fun onPause() {
        AppLog.d("MainActivity.onPause")
        super.onPause()
    }

    override fun onDestroy() {
        AppLog.d("MainActivity.onDestroy")
        super.onDestroy()
    }

    /**
     * Catches uncaught exceptions from ANY thread and writes the full stack
     * trace to the log file BEFORE the process dies. This is how we get the
     * crash cause even when the app closes immediately.
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                AppLog.stackTrace(
                    "UNCAUGHT EXCEPTION on thread '${thread.name}' — app will crash",
                    throwable
                )
            } catch (_: Exception) {
                // Logging must never throw.
            }
            // Hand off to the default handler so the system crash dialog still shows.
            previous?.uncaughtException(thread, throwable)
                ?: Thread.UncaughtExceptionHandler { t, e ->
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(10)
                }.uncaughtException(thread, throwable)
        }
        AppLog.d("crash handler installed")
    }
}
