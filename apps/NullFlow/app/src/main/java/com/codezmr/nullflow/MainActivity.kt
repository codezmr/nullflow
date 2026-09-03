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
import com.codezmr.nullflow.data.Settings
import com.codezmr.nullflow.ui.AppPickerSheet
import com.codezmr.nullflow.ui.MainScreen
import com.codezmr.nullflow.ui.NullFlowTheme
import com.codezmr.nullflow.ui.OnboardingScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val dao = FocusDatabase.get(this).focusDao()

        setContent {
            NullFlowTheme {
                // Onboarding gate: show the welcome/permission screen on first launch.
                var showOnboarding by remember {
                    mutableStateOf(!Settings.get(this).hasOnboarded)
                }

                if (showOnboarding) {
                    OnboardingScreen(onEnter = { showOnboarding = false })
                } else {
                    // Picker sheet state lives here so MainScreen can open it.
                    var pickerProfileId by remember { mutableStateOf<Long?>(null) }

                    MainScreen(
                        dao = dao,
                        onOpenPicker = { profileId -> pickerProfileId = profileId }
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
}
