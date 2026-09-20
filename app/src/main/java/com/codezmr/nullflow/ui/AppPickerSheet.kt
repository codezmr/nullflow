package com.codezmr.nullflow.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codezmr.nullflow.AppLog
import com.codezmr.nullflow.data.BlockedApp
import com.codezmr.nullflow.data.FocusDao
import com.codezmr.nullflow.data.InstalledApp
import com.codezmr.nullflow.data.PackageManagerRepo
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import kotlinx.coroutines.launch

// ---- Focus Matrix palette ----
private val CardUnselected = Color(0xFF12151C)
private val CardSelected = Color(0xFF15222E)
private val BorderUnselected = Color(0xFF222733)
private val AccentCyan = Color(0xFF00E5FF)
private val GlassSurface = Color(0xFF141820)
private val MutedText = Color(0xFFA0A0A0)
private val BadgeUnselected = Color(0xFF1E2430)

/**
 * "The Focus Matrix" — the app picker, redesigned with NO checkboxes.
 *
 *  - Sticky dark-glass search bar (filters the entire list by label).
 *  - Tactile App Cards: unselected = dark + "+ ADD"; selected = cyan glow +
 *    "🔒 SHIELDED" + vibrant icon with a radial halo. Micro-spring press +
 *    thud haptic.
 *  - "Done · N apps selected" bar at the bottom.
 *
 *  Users build their own modes by picking individual apps — no opaque
 *  predefined categories (trust: the user always sees exactly what's blocked).
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

    // Search query (filters the whole list).
    var query by remember { mutableStateOf("") }

    // Load installed apps once.
    LaunchedEffect(Unit) {
        if (!loaded) {
            AppLog.d("AppPicker: loading installed apps for profile $profileId ...")
            installed = repo.getInstalledApps()
            loaded = true
            AppLog.d("AppPicker: loaded ${installed.size} apps")
        }
    }

    // Filter by search (case-insensitive on label).
    val filtered = remember(installed, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) installed else installed.filter { it.label.lowercase().contains(q) }
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
            // ---- Header: screen name + back + count ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(GlassSurface)
                        .border(1.dp, BorderUnselected, CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("←", fontSize = 14.sp, color = MutedText)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "SELECT APPS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                )
                val shieldedCount = blockedPackages.size
                Text(
                    text = if (shieldedCount == 0) "0 shielded" else "$shieldedCount shielded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AccentCyan,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ---- Sticky search bar (dark glass) ----
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp),
                placeholder = { Text("Search apps...", color = MutedText) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(16.dp),
                // Focus state: NO bright cyan border (it competed with the
                // SHIELDED action buttons). Instead the container lightens
                // slightly and only the cursor keeps the cyan accent.
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF3A4150),
                    unfocusedBorderColor = BorderUnselected,
                    cursorColor = AccentCyan,
                    focusedContainerColor = Color(0xFF1A2029),
                    unfocusedContainerColor = GlassSurface
                )
            )

            // ---- Section label ----
            Text(
                text = if (query.isBlank()) "ALL APPS" else "RESULTS",
                color = MutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 28.dp, bottom = 8.dp)
            )

            // ---- Tactile app cards ----
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                    items(filtered, key = { it.packageName }) { app ->
                        TactileAppCard(
                            appName = app.label,
                            iconBitmap = app.icon,
                            isShielded = app.packageName in blockedPackages,
                        onToggle = {
                            Haptics.thud(context)
                            toggleApp(dao, scope, profileId, app, app.packageName in blockedPackages)
                        }
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            text = "No apps match \"$query\"",
                            color = MutedText,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }
            }

            // ---- Done bar ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
            ) {
                PrimaryButton(
                    text = if (blockedPackages.size > 0)
                        "Done · ${blockedPackages.size} app${if (blockedPackages.size == 1) "" else "s"} selected"
                    else
                        "Done",
                    onClick = {
                        Haptics.engage(context)
                        AppLog.d("AppPicker: DONE tapped — closing sheet (${blockedPackages.size} apps)")
                        onDismiss()
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tactile app card (replaces the checkbox)
// ---------------------------------------------------------------------------

@Composable
private fun TactileAppCard(
    appName: String,
    iconBitmap: Bitmap,
    isShielded: Boolean,
    onToggle: () -> Unit
) {
    // Icon is already loaded + cached by PackageManagerRepo — render it
    // directly. No second async load, so the slot is never empty.
    val iconPainter = remember(iconBitmap) { BitmapPainter(iconBitmap.asImageBitmap()) }
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "card_scale"
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isShielded) CardSelected else CardUnselected,
        label = "card_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isShielded) AccentCyan else BorderUnselected,
        label = "card_border"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(
                elevation = if (isShielded) 10.dp else 4.dp,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(
                width = if (isShielded) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Icon (with radial halo when shielded).
            // A visible placeholder circle sits BEHIND the async-loaded icon so
            // the slot is never empty while the bitmap loads on Dispatchers.IO.
            Box(contentAlignment = Alignment.Center) {
                if (isShielded) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        AccentCyan.copy(alpha = 0.35f),
                                        AccentCyan.copy(alpha = 0f)
                                    )
                                )
                            )
                    )
                }
                // Placeholder circle (visible while the icon bitmap loads).
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            color = if (isShielded) Color(0xFF1E2A35) else Color(0xFF1A1E26)
                        )
                )
                androidx.compose.foundation.Image(
                    painter = iconPainter,
                    contentDescription = appName,
                    // Desaturate (grey out) the icon when unselected; full color
                    // when shielded. A semi-transparent grey tint reads as "muted".
                    colorFilter = if (isShielded) null
                    else ColorFilter.tint(Color(0xFF6B7280)),
                    alpha = if (isShielded) 1f else 0.55f,
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = appName,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }

        // Action pill badge (replaces the checkbox)
        SmallActionButton(
            text = if (isShielded) "SHIELDED" else "+ ADD",
            onClick = onToggle,
            active = isShielded
        )
    }
}

// ---------------------------------------------------------------------------
// Actions
// ---------------------------------------------------------------------------

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
                dao.clearActive()
                dao.setActive(profileId, true)
                AppLog.d("AppPicker: BLOCKED ${app.packageName} in profile $profileId (now active)")
            }
        } catch (e: Exception) {
            AppLog.e("AppPicker: toggle app FAILED", e)
        }
    }
}


