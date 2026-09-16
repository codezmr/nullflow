package com.codezmr.nullflow.ui.tile

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codezmr.nullflow.AppLog
import com.codezmr.nullflow.ui.SecondaryButton
import com.codezmr.nullflow.data.FocusDao
import com.codezmr.nullflow.data.FocusDatabase
import com.codezmr.nullflow.data.ProfileWithAppsRow
import com.codezmr.nullflow.vpn.FocusVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---- NullFlow panel palette (rest-mode dark + neon cyan accent) ----
private val PanelBg = Color(0xFF0A0C10)
private val PanelAccent = Color(0xFF00E5FF)
private val PanelDivider = Color(0xFF1E222B)
private val PanelMuted = Color(0xFF808080)
private val PanelSelectedRow = Color(0xFF151D24)
private val PanelTextDim = Color(0xFFA0A0A0)

/**
 * The GhostShield Focus Panel — a Compose UI hosted inside a native
 * BottomSheetDialog shown from the Quick Settings tile.
 *
 *  - Master Switch: ON starts the shield for the active profile; OFF stops it.
 *  - "SELECT MODE" list: pick any profile (sets it active in Room). If the
 *    shield is already ON, this also hot-swaps the tunnel via
 *    ACTION_REFRESH_RULES. If OFF, it only updates the DB target (Q2 = A).
 *  - "+ Create / Edit Modes": dismisses the panel and opens the main app.
 *
 * All Room writes run on Dispatchers.IO.
 */
@Composable
fun TileFocusPanel(
    onOpenApp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao: FocusDao = remember { FocusDatabase.get(context).focusDao() }

    // ---- Reactive state from Room ----
    // One row per (profile, blocked-app). Group by profile id → a mode with its
    // list of blocked package names (for the icon row).
    val profileRows by dao.observeProfilesWithApps().collectAsState(initial = emptyList())
    val activeProfile by dao.observeActiveProfile().collectAsState(initial = null)
    val runningSession by dao.observeRunningSession().collectAsState(initial = null)

    // Grouped: profile id → (name, isActive, list of package names).
    val grouped = remember(profileRows) {
        val map = LinkedHashMap<Long, ModeWithApps>()
        for (row in profileRows) {
            val mode = map.getOrPut(row.id) { ModeWithApps(row.id, row.name, row.isActive) }
            row.packageName?.let { mode.packages.add(it) }
        }
        map.values.toList()
    }

    // Shield is ON only when there's an active profile AND a live session.
    val isShieldOn = activeProfile != null && runningSession != null

    // ---- Master switch ----
    fun onToggleShield(shouldActivate: Boolean) {
        if (shouldActivate) {
            val profileId = activeProfile?.id
            if (profileId == null) {
                AppLog.w("TilePanel: toggle ON but no active profile — ignoring")
                return
            }
            // Mark active + start a session, then start the service (mirrors
            // the main app hero toggle so both stay in sync).
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        dao.clearActive()
                        dao.setActive(profileId, true)
                        dao.insertSession(
                            com.codezmr.nullflow.data.FocusSession(
                                profileId = profileId,
                                startTime = System.currentTimeMillis()
                            )
                        )
                        AppLog.d("TilePanel: profile $profileId active + session inserted")
                    } catch (e: Exception) {
                        AppLog.e("TilePanel: start session FAILED", e)
                    }
                }
                context.startForegroundService(FocusVpnService.startIntent(context, profileId))
                AppLog.d("TilePanel: startForegroundService launched")
            }
        } else {
            // Turn OFF: stop the service + end the session + deactivate.
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val running = dao.getRunningSession()
                        if (running != null) {
                            dao.endSession(running.id, System.currentTimeMillis())
                            dao.setActive(running.profileId, false)
                            AppLog.d("TilePanel: session ${running.id} ended, profile deactivated")
                        }
                    } catch (e: Exception) {
                        AppLog.e("TilePanel: end session FAILED", e)
                    }
                }
                context.startService(FocusVpnService.stopIntent(context))
                AppLog.d("TilePanel: ACTION_STOP_SHIELD sent")
            }
        }
    }

    // ---- Mode selection ----
    fun onModeSelected(mode: ModeWithApps) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    dao.clearActive()
                    dao.setActive(mode.id, true)
                    AppLog.d("TilePanel: profile '${mode.name}' (id=${mode.id}) set active")
                } catch (e: Exception) {
                    AppLog.e("TilePanel: set active FAILED", e)
                }
            }
            // If the shield is already ON, hot-swap the tunnel to the new
            // profile's apps. If OFF, do nothing (Q2 = A: passive select).
            if (isShieldOn) {
                context.startService(FocusVpnService.refreshIntent(context))
                AppLog.d("TilePanel: ACTION_REFRESH_RULES sent (shield was ON)")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBg)
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 28.dp)
    ) {
        // ---- Header row: title + master switch ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Use Focus Shield",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isShieldOn) "Shield active" else "Shield off",
                    color = if (isShieldOn) PanelAccent else PanelMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Switch(
                checked = isShieldOn,
                onCheckedChange = { onToggleShield(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF0A0C10),
                    checkedTrackColor = PanelAccent,
                    uncheckedThumbColor = Color(0xFF505050),
                    uncheckedTrackColor = Color(0xFF1E1E1E)
                )
            )
        }

        Spacer(Modifier.height(22.dp))

        // ---- Section label ----
        Text(
            text = "SELECT MODE",
            color = PanelMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        // ---- Profiles list (with app-icon rows) ----
        if (grouped.isEmpty()) {
            Text(
                text = "No modes yet. Tap below to create one.",
                color = PanelMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                items(grouped, key = { it.id }) { mode ->
                    val isSelected = activeProfile?.id == mode.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) PanelSelectedRow else Color.Transparent)
                            .clickable { onModeSelected(mode) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = PanelAccent)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.name,
                                color = if (isSelected) Color.White else PanelTextDim,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Spacer(Modifier.height(6.dp))
                            // Visual mode selector: a scrollable row of the
                            // blocked apps' icons (24dp circles, 8dp spacing)
                            // with a right-edge gradient fade as a scroll hint.
                            if (mode.packages.isEmpty()) {
                                Text(
                                    text = "No apps yet",
                                    color = PanelMuted,
                                    fontSize = 12.sp
                                )
                            } else {
                                AppIconRow(context = context, packages = mode.packages)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Divider ----
        Divider(color = PanelDivider, thickness = 1.dp)

        Spacer(Modifier.height(16.dp))

        // ---- Action row: add more apps (opens dashboard) ----
        SecondaryButton(
            text = "Add more apps",
            icon = "+",
            onClick = onOpenApp
        )
    }
}

/** A focus mode grouped with its blocked apps' package names. */
private data class ModeWithApps(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val packages: MutableList<String> = mutableListOf()
)

/**
 * A horizontal, scrollable row of blocked-app icons (24dp circles, 8dp
 * spacing) with a right-edge gradient fade so the user knows the list extends
 * off-screen. Icons load off the main thread and are cached in memory.
 */
@Composable
private fun AppIconRow(context: Context, packages: List<String>) {
    Box(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(packages, key = { it }) { pkg ->
                val painter = rememberAppIconPainter(context, pkg)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2430))
                ) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        // Right-edge gradient fade (scroll hint) — only meaningful when there's
        // more content to the right.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(36.dp)
                .fillMaxHeight()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            PanelBg.copy(alpha = 0f),
                            PanelBg.copy(alpha = 0.9f)
                        )
                    )
                )
        )
    }
}
