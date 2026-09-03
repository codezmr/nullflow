package com.codezmr.nullflow.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codezmr.nullflow.AppLog
import com.codezmr.nullflow.data.BlockedApp
import com.codezmr.nullflow.data.FocusDao
import com.codezmr.nullflow.data.InstalledApp
import com.codezmr.nullflow.data.PackageManagerRepo
import kotlinx.coroutines.launch

/**
 * The app picker — a bottom sheet (not a new screen) so the user stays
 * grounded on the main interface. Multi-select checkboxes assign apps to the
 * active FocusProfile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    dao: FocusDao,
    profileId: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    val repo = remember { PackageManagerRepo(context) }
    var installed by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    val blocked by dao.observeBlockedApps(profileId).collectAsState(initial = emptyList())
    val blockedPackages = remember(blocked) { blocked.map { it.packageName }.toSet() }

    // Load installed apps once.
    LaunchedEffect(Unit) {
        if (!loaded) {
            AppLog.d("AppPicker: loading installed apps for profile $profileId ...")
            installed = repo.getInstalledApps()
            loaded = true
            AppLog.d("AppPicker: loaded ${installed.size} apps")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Choose apps to silence",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${blockedPackages.size} selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // App list
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(installed, key = { it.packageName }) { app ->
                    val checked = app.packageName in blockedPackages
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Haptics.tick(context)
                                toggleApp(dao, scope, profileId, app, checked)
                            }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = app.icon.asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                toggleApp(dao, scope, profileId, app, checked)
                            }
                        )
                    }
                }
            }

            // ---- Done bar (closes the sheet) ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            Haptics.engage(context)
                            AppLog.d("AppPicker: DONE tapped — closing sheet ($blockedPackages.size apps)")
                            onDismiss()
                        }
                        .height(54.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (blockedPackages.size > 0)
                            "Done · ${blockedPackages.size} app${if (blockedPackages.size == 1) "" else "s"} selected"
                        else
                            "Done",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

private fun toggleApp(
    dao: FocusDao,
    scope: kotlinx.coroutines.CoroutineScope,
    profileId: Long,
    app: InstalledApp,
    currentlyChecked: Boolean
) {
    scope.launch {
        try {
            if (currentlyChecked) {
                // Remove: find the BlockedApp row for this package + profile.
                val existing = dao.getBlockedApps(profileId).firstOrNull {
                    it.packageName == app.packageName
                }
                if (existing != null) {
                    dao.deleteBlockedApp(existing.id)
                    AppLog.d("AppPicker: UNBLOCKED ${app.packageName} from profile $profileId")
                }
            } else {
                dao.insertBlockedApps(
                    listOf(
                        BlockedApp(
                            profileId = profileId,
                            packageName = app.packageName,
                            appName = app.label
                        )
                    )
                )
                // Mark this profile active so the hero toggle finds it.
                dao.clearActive()
                dao.setActive(profileId, true)
                AppLog.d("AppPicker: BLOCKED ${app.packageName} in profile $profileId (now active)")
            }
        } catch (e: Exception) {
            AppLog.e("AppPicker: toggle app FAILED", e)
        }
    }
}
