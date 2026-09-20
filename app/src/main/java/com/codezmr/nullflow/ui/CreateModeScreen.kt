package com.codezmr.nullflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codezmr.nullflow.AppLog
import com.codezmr.nullflow.data.FocusDao
import com.codezmr.nullflow.data.FocusProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---- Palette (matches the Focus Matrix / sheet surfaces) ----
private val CreateBg = Color(0xFF0A0C10)
private val CreateAccent = Color(0xFF00E5FF)
private val CreateSurface = Color(0xFF12151C)
private val CreateBorder = Color(0xFF222733)
private val CreateMuted = Color(0xFF808080)

/**
 * Full-screen "Create Mode" route (Scenario A).
 *
 * Replaces the old nested bottom-sheet create flow. Tapping "Create your first
 * mode" (or "New Mode" in the manager) navigates here — a standard full-screen
 * window where the keyboard opens natively and reliably (no modal-window IME
 * bugs).
 *
 * Layout:
 *  - Top:    large borderless name field (auto-focused, keyboard opens on entry).
 *  - Middle: the app picker, embedded directly below the field.
 *  - Bottom: sticky "Save Mode" button.
 *
 * The profile is created the moment the user types a name (so the picker can
 * reference a real profileId). If they leave without saving, the empty profile
 * is cleaned up. This guarantees no "0 apps" dead-end: the user completes
 * Name + Apps in one focused context.
 */
@Composable
fun CreateModeScreen(
    dao: FocusDao,
    onBack: () -> Unit
) {
    val scope = remember { kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.Main
    ) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var name by remember { mutableStateOf("") }
    var profileId by remember { mutableStateOf<Long?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Auto-focus the name field on entry. This is a full-screen window (not a
    // mid-transition sheet), so requestFocus() lands reliably and the keyboard
    // opens natively. We still command the IME explicitly as a belt-and-suspenders
    // measure once focus is captured.
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    LaunchedEffect(isFocused) {
        if (isFocused) {
            kotlinx.coroutines.delay(50)
            keyboardController?.show()
        }
    }

    // Create the profile as soon as the user types a name (debounced on first
    // non-blank char). The picker needs a real profileId to persist selections.
    fun ensureProfile() {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || profileId != null) return
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val id = dao.insertProfile(FocusProfile(name = trimmed))
                    profileId = id
                    AppLog.d("CreateMode: created profile '$trimmed' (id=$id)")
                } catch (e: Exception) {
                    AppLog.e("CreateMode: create profile FAILED", e)
                }
            }
        }
    }

    // Clean up the (possibly empty) profile if the user backs out without saving.
    fun discardAndBack() {
        val id = profileId
        if (id != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        dao.deleteBlockedAppsByProfile(id)
                        dao.deleteProfileById(id)
                        AppLog.d("CreateMode: discarded unsaved profile $id")
                    } catch (e: Exception) {
                        AppLog.e("CreateMode: discard FAILED", e)
                    }
                }
            }
        }
        onBack()
    }

    fun save() {
        val id = profileId ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || isSaving) return
        isSaving = true
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Name may have changed since creation — sync it.
                    dao.renameProfile(id, trimmed)
                    // Make this the active mode so the shield uses it immediately.
                    dao.clearActive()
                    dao.setActive(id, true)
                    AppLog.d("CreateMode: SAVED profile '$trimmed' (id=$id) + set active")
                } catch (e: Exception) {
                    AppLog.e("CreateMode: save FAILED", e)
                }
            }
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreateBg)
    ) {
        // ---- Top bar: back + title ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 4.dp)
        ) {
            TextButton(onClick = { discardAndBack() }) {
                Text("← Cancel", color = CreateMuted, fontSize = 14.sp)
            }
            Text(
                text = "NEW MODE",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // ---- Name field (large, borderless) ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Name this mode",
                color = CreateMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(8.dp))
            Box {
                if (name.isEmpty()) {
                    Text(
                        text = "e.g. Deep Work, Gym, Ghosting",
                        color = CreateMuted.copy(alpha = 0.6f),
                        fontSize = 22.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                BasicTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        ensureProfile()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused }
                        .padding(vertical = 6.dp),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (name.isNotBlank()) save()
                            else focusManager.clearFocus()
                        }
                    )
                )
            }
            // Subtle underline that lights up when focused.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        if (isFocused) CreateAccent else CreateBorder
                    )
            )
        }

        // ---- App picker (embedded) ----
        val id = profileId
        if (id != null) {
            AppPickerContent(
                dao = dao,
                profileId = id,
                title = "SELECT APPS",
                onBack = null,
                actionLabel = "Save Mode",
                onAction = { save() }
            )
        } else {
            // Placeholder until the profile exists (first keystroke).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Type a name above to start picking apps",
                    color = CreateMuted,
                    fontSize = 14.sp
                )
            }
            // Sticky Save (disabled until a name is typed).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
            ) {
                PrimaryButton(
                    text = "Save Mode",
                    onClick = { save() },
                    enabled = name.isNotBlank()
                )
            }
        }
    }
}
