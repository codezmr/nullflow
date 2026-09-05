package com.codezmr.nullflow

import android.content.ComponentName
import android.graphics.drawable.Icon
import android.app.StatusBarManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

                // ---- Back-confirmation: pressing back (button or gesture) shows
                // a dialog instead of immediately minimizing the app. ----
                var showExitDialog by remember { mutableStateOf(false) }
                // Wire the system back handler (button + gesture) to our dialog.
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    onBackPressedDispatcher.addCallback(
                        this@MainActivity,
                        object : androidx.activity.OnBackPressedCallback(true) {
                            override fun handleOnBackPressed() {
                                AppLog.d("back pressed → showing exit confirmation")
                                showExitDialog = true
                            }
                        }
                    )
                }

                if (showExitDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            AppLog.d("exit dialog dismissed (stay)")
                            showExitDialog = false
                        },
                        title = {
                            Text("Exit NullFlow?")
                        },
                        text = {
                            Text("This stops the shield and closes the app completely. Your focus data is saved on this phone.")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                AppLog.d("exit confirmed → stopping shield + closing app")
                                showExitDialog = false
                                // 1) Stop the shield (if running). This sends
                                //    ACTION_STOP to the service → teardown()
                                //    closes the tunnel, removes the notification,
                                //    ends the Room session, deactivates the profile.
                                //    Idempotent — safe if the shield is already off.
                                try {
                                    startService(FocusVpnService.stopIntent(this))
                                    AppLog.d("exit: ACTION_STOP sent to FocusVpnService")
                                } catch (e: Exception) {
                                    AppLog.e("exit: failed to stop shield", e)
                                }
                                // 2) Close the app completely (all activities in
                                //    this task). The shield is already stopped
                                //    above, so nothing lingers in the background.
                                finishAffinity()
                                AppLog.d("exit: finishAffinity() called — app closing")
                            }) {
                                Text("Exit")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                AppLog.d("exit cancelled → stay")
                                showExitDialog = false
                            }) {
                                Text("Stay")
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        textContentColor = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (showOnboarding) {
                    OnboardingScreen(
                        onEnter = {
                            AppLog.d("onboarding complete → entering main screen")
                            showOnboarding = false
                        },
                        onRequestAddQsTile = { onResult ->
                            requestAddQsTile(onResult)
                        }
                    )
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
     * Request the system to add the GhostShield Quick Settings tile (1-tap,
     * no manual drag-and-drop). Android 13+ (TIRAMISU) only — older versions
     * must pin the tile manually (the onboarding shows a fallback card there).
     *
     * @param onResult called on the main thread with `true` if the user
     *   confirmed the add, `false` if they dismissed it (or on pre-33 where
     *   the API is unavailable — the caller should treat that as "skip").
     */
    fun requestAddQsTile(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            AppLog.d("requestAddQsTile: pre-Android 13 — API unavailable, reporting false")
            onResult(false)
            return
        }
        try {
            val sbm = getSystemService(StatusBarManager::class.java)
            val tileComponent = ComponentName(this, com.codezmr.nullflow.tile.FocusTileService::class.java)
            val label = "GhostShield"
            val icon = Icon.createWithResource(this, R.drawable.ic_hero_toggle)
            // Main-thread executor (the callback must run on the main thread).
            // Dependency-free: a Handler on the main looper implements Executor.
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val mainExecutor = java.util.concurrent.Executor { handler.post(it) }
            sbm.requestAddTileService(
                tileComponent,
                label,
                icon,
                mainExecutor,
                java.util.function.Consumer { resultCode: Int? ->
                    // 0 = user confirmed the add; non-zero = dismissed/error.
                    val added = resultCode == 0
                    AppLog.d("requestAddQsTile: callback resultCode=$resultCode added=$added")
                    onResult(added)
                }
            )
            AppLog.d("requestAddQsTile: requestAddTileService invoked for $tileComponent")
        } catch (e: Exception) {
            AppLog.e("requestAddQsTile FAILED", e)
            onResult(false)
        }
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
                ?: Thread.UncaughtExceptionHandler { _, _ ->
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(10)
                }.uncaughtException(thread, throwable)
        }
        AppLog.d("crash handler installed")
    }
}
