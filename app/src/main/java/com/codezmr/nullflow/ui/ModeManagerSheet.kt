package com.codezmr.nullflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codezmr.nullflow.AppLog
import com.codezmr.nullflow.data.FocusDao
import com.codezmr.nullflow.data.FocusProfile
import com.codezmr.nullflow.data.ProfileWithCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SheetBg = Color(0xFF0A0C10)
private val SheetAccent = Color(0xFF00E5FF)
private val SheetSurface = Color(0xFF12151C)
private val SheetBorder = Color(0xFF222733)
private val SheetMuted = Color(0xFF808080)
private val SheetTextDim = Color(0xFFA0A0A0)
private val SheetSelected = Color(0xFF151D24)

@Composable
fun ModeManagerSheet(
    dao: FocusDao,
    onDismiss: () -> Unit,
    /**
     * Called when a NEW mode is created and named. The caller should open the
     * app picker for this profile so the user completes setup in one flow
     * (no "0 apps" dead-end modes).
     */
    onModeCreated: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val profiles by dao.observeProfilesWithAppCount().collectAsState(initial = emptyList())
    val activeProfile by dao.observeActiveProfile().collectAsState(initial = null)

    // Inline create/rename state (NO stacked dialogs — the field expands
    // inside the sheet itself).
    var showCreate by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<FocusProfile?>(null) }
    var renameName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<FocusProfile?>(null) }

    fun createProfile(name: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val id = dao.insertProfile(FocusProfile(name = name))
                    AppLog.d("ModeManager: created profile '$name' (id=$id)")
                    // Seamless setup: hand the new profile to the app picker.
                    onModeCreated(id)
                } catch (e: Exception) {
                    AppLog.e("ModeManager: create FAILED", e)
                }
            }
            showCreate = false
            createName = ""
        }
    }

    fun renameProfile(profileId: Long, newName: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    dao.renameProfile(profileId, newName)
                    AppLog.d("ModeManager: renamed profile $profileId → '$newName'")
                } catch (e: Exception) {
                    AppLog.e("ModeManager: rename FAILED", e)
                }
            }
            renameTarget = null
            renameName = ""
        }
    }

    fun deleteProfile(profile: FocusProfile) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    dao.deleteBlockedAppsByProfile(profile.id)
                    dao.deleteProfileById(profile.id)
                    AppLog.d("ModeManager: deleted profile '${profile.name}' (id=${profile.id}) + its apps")
                } catch (e: Exception) {
                    AppLog.e("ModeManager: delete FAILED", e)
                }
            }
            deleteTarget = null
        }
    }

    fun selectProfileById(id: Long, name: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    dao.clearActive()
                    dao.setActive(id, true)
                    AppLog.d("ModeManager: profile '$name' (id=$id) set active")
                } catch (e: Exception) {
                    AppLog.e("ModeManager: set active FAILED", e)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SheetBg)
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 28.dp)
    ) {
        // Header: screen name + back
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(SheetSurface)
                        .border(1.dp, SheetBorder, CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("←", fontSize = 14.sp, color = SheetMuted)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "MANAGE MODES",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
            TextButton(onClick = onDismiss) {
                Text("Done", color = SheetAccent, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Profile list
        if (profiles.isEmpty()) {
            Text(
                text = "No modes yet. Create your first one below.",
                color = SheetMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(profiles, key = { it.id }) { p ->
                    val isActive = activeProfile?.id == p.id
                    val isRenaming = renameTarget?.id == p.id
                    if (isRenaming) {
                        // Inline rename: the row itself becomes the text field.
                        InlineNameField(
                            label = "Rename Mode",
                            placeholder = "Mode name",
                            text = renameName,
                            onTextChange = { renameName = it },
                            confirmLabel = "Save",
                            onConfirm = { renameProfile(p.id, renameName.trim()) },
                            onDismiss = {
                                renameTarget = null
                                renameName = ""
                            }
                        )
                    } else {
                        ModeRow(
                            name = p.name,
                            appCount = p.appCount,
                            isActive = isActive,
                            onClick = { selectProfileById(p.id, p.name) },
                            onRename = {
                                renameTarget = FocusProfile(id = p.id, name = p.name, isActive = p.isActive)
                                renameName = p.name
                            },
                            onDelete = { deleteTarget = FocusProfile(id = p.id, name = p.name, isActive = p.isActive) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Create — INLINE (no dialog stacked over the sheet). Tapping "New
        // Mode" expands a text field right here in the sheet.
        if (showCreate) {
            InlineNameField(
                label = "New Mode",
                placeholder = "e.g. Deep Work, Gym, Ghosting",
                text = createName,
                onTextChange = { createName = it },
                confirmLabel = "Create & pick apps",
                onConfirm = { createProfile(createName.trim()) },
                onDismiss = {
                    showCreate = false
                    createName = ""
                }
            )
        } else {
            SecondaryButton(
                text = "New Mode",
                icon = "+",
                onClick = {
                    AppLog.d("ModeManager: 'New Mode' tapped → showCreate=true")
                    showCreate = true
                }
            )
        }
    }

    // Delete confirmation overlay
    deleteTarget?.let { target ->
        InlineConfirmOverlay(
            title = "Delete \"${target.name}\"?",
            message = "This removes the mode and its blocked apps.",
            confirmLabel = "Delete",
            onConfirm = { deleteProfile(target) },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun ModeRow(
    name: String,
    appCount: Int,
    isActive: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) SheetSelected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Active indicator dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isActive) SheetAccent else Color(0xFF2A2F3A))
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = if (isActive) Color.White else SheetTextDim,
                fontSize = 15.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$appCount app${if (appCount == 1) "" else "s"}" +
                    if (isActive) "  ·  active" else "",
                color = SheetMuted,
                fontSize = 12.sp
            )
        }

        // Rename — 48dp minimum touch target (Material spec) so edit and
        // delete can't be hit by accident on small screens / one-handed use.
        IconButton(onClick = onRename, modifier = Modifier.size(48.dp)) {
            Text("✎", color = SheetMuted, fontSize = 16.sp)
        }

        // Delete
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Text("✕", color = SheetMuted, fontSize = 16.sp)
        }
    }
}

/**
 * Inline name field — expands INSIDE the sheet (no stacked modal dialog).
 * Used for both "New Mode" creation and in-row renaming.
 */
@Composable
private fun InlineNameField(
    label: String,
    placeholder: String,
    text: String,
    onTextChange: (String) -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // Auto-focus the field when it appears so the keyboard opens immediately.
    //
    // TIMING MATTERS (Android 15): requesting IME focus in the SAME frame the
    // field is composed is too early — the window's input focus hasn't settled,
    // so the request is dropped and the keyboard never opens (the field reports
    // isFocused=false). We therefore:
    //   1) wait one frame (withFrameNanos) so the layout + focus pass completes,
    //   2) request focus,
    //   3) if it still didn't take, retry once after a short delay.
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        AppLog.d("InlineNameField('$label'): frame settled → requesting IME focus")
        focusRequester.requestFocus()
        // Give the focus request a beat to land, then verify + retry once.
        delay(120)
        if (!isFocused) {
            AppLog.w("InlineNameField('$label'): focus did not take → retrying")
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SheetSurface)
            .border(1.dp, SheetAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Box {
            if (text.isEmpty()) {
                Text(
                    placeholder,
                    color = SheetMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        isFocused = it.isFocused
                        AppLog.d("InlineNameField('$label'): focus changed → isFocused=${it.isFocused}")
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(SheetBg)
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = if (isFocused) SheetAccent else SheetBorder,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        if (text.isNotBlank()) onConfirm()
                        else focusManager.clearFocus()
                    }
                )
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SheetMuted)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onConfirm,
                enabled = text.isNotBlank()
            ) {
                Text(confirmLabel, color = SheetAccent)
            }
        }
    }
}

@Composable
private fun InlineConfirmOverlay(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SheetSurface)
                .border(1.dp, SheetBorder, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = SheetTextDim, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = SheetMuted)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onConfirm) {
                    Text(confirmLabel, color = Color(0xFFFF5252))
                }
            }
        }
    }
}
