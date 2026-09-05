package com.codezmr.nullflow.ui

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codezmr.nullflow.AppLog
import com.codezmr.nullflow.data.FocusDao
import com.codezmr.nullflow.data.FocusProfile
import com.codezmr.nullflow.ui.tile.rememberAppIconPainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The whole app in one screen (Big Tech approach):
 *  - Hero toggle (massive, animated, haptic)
 *  - Quantified-relief stats
 *  - Profile name + "edit apps" entry point (app picker sheet)
 *
 * The toggle is INSTANT: VPN consent was already handled during onboarding,
 * so flipping it on creates the tunnel with zero popups.
 */
@Composable
fun MainScreen(
    dao: FocusDao,
    onOpenPicker: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ---- State from Room ----
    val profiles by dao.observeProfiles().collectAsState(initial = emptyList())
    val activeProfile by dao.observeActiveProfile().collectAsState(initial = null)
    val runningSession by dao.observeRunningSession().collectAsState(initial = null)
    val totalMs by dao.observeTotalFocusedMs().collectAsState(initial = 0L)
    val completedCount by dao.observeCompletedCount().collectAsState(initial = 0)

    // ---- Command Center: radar + dossier data ----
    // Top 6 most-intercepted apps (drives the Distraction Radar).
    val topIntercepted by dao.observeTopIntercepted(6).collectAsState(initial = emptyList())
    // Total deflected pings across all apps (drives the shareable dossier).
    val totalDeflected by dao.observeTotalDeflected().collectAsState(initial = 0L)
    // Dossier generation state (for the share button's loading feedback).
    var dossierBusy by remember { mutableStateOf(false) }

    // The profile the UI is "pointing at": the active one if set, otherwise the
    // first existing profile. This keeps the profile row, the blocked-app count,
    // and the toggle all in agreement (and stops the toggle from minting a new
    // empty profile on every tap).
    val effectiveProfile: FocusProfile? = activeProfile ?: profiles.firstOrNull()

    // Live count of blocked apps for the effective profile (drives the "add apps"
    // guard + the "N apps shielded" stat).
    val blockedApps by remember(effectiveProfile?.id) {
        val id = effectiveProfile?.id
        if (id != null) dao.observeBlockedApps(id) else flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val blockedCount = blockedApps.size

    val isActive = activeProfile != null && runningSession != null

    // ---- Visual state change: bright/neutral → deep dark when active ----
    val offBg = Color(0xFF1E222B)
    val onBg = Color(0xFF0A0C10)
    val bgColor by animateColorAsState(
        targetValue = if (isActive) onBg else offBg,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "bg"
    )

    // ---- Live session timer ----
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isActive) {
        while (isActive) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    // ---- Hero toggle action (INSTANT — consent was handled in onboarding) ----
    fun onToggle() {
        Haptics.tick(context)
        val current = activeProfile
        AppLog.d("TOGGLE tapped: isActive=$isActive activeProfile=${current?.name} runningSession=${runningSession != null}")
        if (isActive) {
            // Turn OFF.
            AppLog.d("TOGGLE → turning OFF (stop service + end session)")
            stopShield(context)
            endCurrentSession(dao, scope)
            Haptics.disengage(context)
        } else {
            // Turn ON — one tap, zero popups.
            // VpnService.prepare() was already answered during onboarding,
            // so the tunnel establishes immediately.
            //
            // Profile resolution: the effective profile (active, else first
            // existing), or a brand-new default only if none exist at all.
            val profileId = effectiveProfile?.id ?: createDefaultProfile(dao)

            // Guard: nothing to shield → don't start the service (it would
            // immediately stop), and tell the user to add apps first.
            if (blockedCount == 0) {
                AppLog.w("TOGGLE → blocked, 0 apps in profile $profileId. Opening picker.")
                Haptics.tick(context)
                onOpenPicker(profileId)
                return
            }

            AppLog.d("TOGGLE → turning ON for profileId=$profileId ($blockedCount apps)")
            startShield(context, dao, profileId)
            Haptics.engage(context)
        }
    }

    // ---- Share the Zero-Leak Dossier (generate 9:16 card → share sheet) ----
    fun onShareDossier() {
        if (dossierBusy) return
        Haptics.tick(context)
        dossierBusy = true
        scope.launch {
            try {
                val path = DossierGenerator.generateAndSave(
                    context = context,
                    totalFocusMs = totalMs,
                    totalDeflected = totalDeflected,
                    completedSessions = completedCount
                )
                if (path != null) {
                    DossierShare.share(context, path)
                    Haptics.engage(context)
                } else {
                    Haptics.disengage(context)
                }
            } finally {
                dossierBusy = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Root Column: fillMaxSize. The top half gets weight(1f) so it takes
        // ALL remaining space and stops exactly where the bottom dashboard
        // begins. The dashboard is anchored below with NO weight, so it is
        // pinned to the bottom edge and can never be pushed off-screen by the
        // (unconstrained) middle content.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
        ) {
            // ---- 1. TOP HALF (dynamic space — takes remaining height) ----
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ---- Tactical HUD header (top-left, asymmetrical) ----
                // Mimics command-line / aviation HUD aesthetics: brand + live
                // system status anchored to the top-left edge.
                HudHeader(
                    isShieldActive = isActive
                )

                // Guaranteed minimum gap between the HUD header and the Hero
                // Switch (the weight(1f) spacer below absorbs any extra space).
                Spacer(Modifier.height(40.dp))

                Spacer(Modifier.weight(1f))

                // ---- THE HERO TOGGLE ----
                HeroToggle(
                    isActive = isActive,
                    onClick = { onToggle() }
                )

                Spacer(Modifier.height(28.dp))

                // Status line
                Text(
                    text = if (isActive) "Shield active" else "Shield off",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isActive)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )

                // Active profile name + edit
                if (effectiveProfile != null) {
                    val profile = effectiveProfile
                    Spacer(Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            onOpenPicker(profile.id)
                        }
                    ) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (blockedCount > 0)
                                "$blockedCount app${if (blockedCount == 1) "" else "s"} shielded"
                            else "No apps yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (blockedCount > 0)
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Edit apps",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // ---- Blocked-app icon row (transparency: see exactly what's
                    // shielded) — mirrors the QS tile panel's icon row. ----
                    if (blockedCount > 0) {
                        Spacer(Modifier.height(12.dp))
                        BlockedAppIconRow(
                            context = context,
                            apps = blockedApps,
                            bg = bgColor
                        )
                    }
                } else {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "No focus mode yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(12.dp))
                    // Fresh install: give a clear way to pick apps BEFORE toggling on.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .clickable {
                                val profileId = createDefaultProfile(dao)
                                onOpenPicker(profileId)
                            }
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Choose apps to shield",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
            }

            // ---- 2. BOTTOM HALF (anchored dashboard — NO weight) ----
            // Pinned to the bottom edge. The top half's weight(1f) absorbs all
            // extra space, so this block can never be pushed off-screen.
            if (isActive) {
                // ---- Active: live session stats ----
                StatsRow(
                    sessionMs = if (runningSession != null)
                        (now - runningSession!!.startTime) else 0L,
                    totalMs = totalMs,
                    completedCount = completedCount
                )
            } else {
                // ---- Inactive: the Premium Analytics Command Center ----
                CommandCenter(
                    topIntercepted = topIntercepted,
                    totalDeflected = totalDeflected,
                    totalMs = totalMs,
                    completedCount = completedCount,
                    dossierBusy = dossierBusy,
                    onShareDossier = { onShareDossier() }
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Tactical HUD Header — top-left brand + live system status
// ---------------------------------------------------------------------------
//
// Replaces the old centered "NullFlow" + tagline. Asymmetrical top-left
// alignment mimics command-line / aviation HUD / security-software aesthetics.
// The status line is state-driven: cyan "SECURE" when the shield is active,
// muted grey "STANDBY" when off.
//
// Uses statusBarsPadding() to avoid the notch / camera cutout / status bar.

@Composable
private fun HudHeader(isShieldActive: Boolean) {
    // Status colors (state-driven).
    val nodeColor = if (isShieldActive) Color(0xFF00E5FF) else Color(0xFF4A4E58)
    val textColor = if (isShieldActive) Color(0xFF00E5FF) else Color(0xFF8A8F99)
    val statusText = if (isShieldActive) "SYS.STATUS: SECURE" else "SYS.STATUS: STANDBY"

    // Animate the node color + glow so the transition feels alive.
    val animatedNodeColor by animateColorAsState(
        targetValue = nodeColor,
        animationSpec = tween(400),
        label = "hudNode"
    )
    val animatedTextColor by animateColorAsState(
        targetValue = textColor,
        animationSpec = tween(400),
        label = "hudText"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isShieldActive) 0.6f else 0f,
        animationSpec = tween(400),
        label = "hudGlow"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 24.dp, top = 24.dp, end = 24.dp)
    ) {
        // Main brand: "NULLFLOW" all-caps, heavy weight, wide letter spacing.
        Text(
            text = "NULLFLOW",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f)
        )

        Spacer(Modifier.height(6.dp))

        // Dynamic status line: glowing node + monospace status text.
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Status node (6dp circle). Glows cyan when active.
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(animatedNodeColor)
                    .shadow(
                        elevation = if (isShieldActive) 8.dp else 0.dp,
                        shape = CircleShape,
                        ambientColor = Color(0xFF00E5FF).copy(alpha = glowAlpha),
                        spotColor = Color(0xFF00E5FF).copy(alpha = glowAlpha)
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusText,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = animatedTextColor
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Command Center (inactive state) — radar + shareable dossier
// ---------------------------------------------------------------------------

@Composable
private fun CommandCenter(
    topIntercepted: List<com.codezmr.nullflow.data.BlockedApp>,
    totalDeflected: Long,
    totalMs: Long,
    completedCount: Int,
    dossierBusy: Boolean,
    onShareDossier: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "DISTRACTION RADAR",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Drag across a node to inspect",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
        )

        Spacer(Modifier.height(12.dp))

        // The hexagonal radar (square aspect, ~280dp).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            if (topIntercepted.isEmpty()) {
                // Empty state: no interceptions yet.
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No data yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Activate the shield to start tracking",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                    )
                }
            } else {
                FocusRadarGraph(
                    apps = topIntercepted,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Aggregate stats (compact, under the radar).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCell(value = formatDuration(totalMs), label = "all-time focus")
            StatCell(value = "$totalDeflected", label = "pings deflected")
            StatCell(value = "$completedCount", label = "sessions")
        }

        Spacer(Modifier.height(24.dp))

        // ---- Share the Zero-Leak Dossier ----
        ShareDossierButton(
            busy = dossierBusy,
            onClick = onShareDossier
        )
    }
}

@Composable
private fun ShareDossierButton(busy: Boolean, onClick: () -> Unit) {
    val alpha by animateFloatAsState(
        targetValue = if (busy) 0.6f else 1f,
        animationSpec = tween(200),
        label = "shareAlpha"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF00E5FF), Color(0xFF4F8CFF))
                )
            )
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp)
    ) {
        Text(
            text = if (busy) "Generating…" else "Share my focus dossier",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0A0C10).copy(alpha = alpha)
        )
    }
}

// ---------------------------------------------------------------------------
// Hero Toggle
// ---------------------------------------------------------------------------

@Composable
private fun HeroToggle(isActive: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.96f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "toggleScale"
    )
    val ringColor = if (isActive) Color(0xFF4F8CFF) else Color(0xFF3A4150)
    val glowAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.55f else 0f,
        animationSpec = tween(600),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .size(220.dp)
            .scale(scale)
            // 3D extruded hardware feel:
            //  1) Dark, offset drop-shadow to the bottom-right (depth).
            .shadow(
                elevation = if (isActive) 26.dp else 14.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.55f),
                spotColor = Color.Black.copy(alpha = 0.55f)
            )
            .background(
                color = if (isActive) Color(0xFF4F8CFF).copy(alpha = glowAlpha) else Color.Transparent,
                shape = CircleShape
            )
            .padding(14.dp)
            //  2) Body with a top-left light → bottom-right dark gradient (bevel).
            .background(
                brush = Brush.linearGradient(
                    colors = if (isActive)
                        listOf(Color(0xFF1B2A44), Color(0xFF0C1420))
                    else
                        listOf(Color(0xFF23272F), Color(0xFF14171D)),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        //  3) Subtle semi-transparent white highlight on the top-left (specular).
        Box(
            modifier = Modifier
                .size(220.dp)
                .padding(14.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0f)
                        ),
                        center = Offset(70f, 70f),
                        radius = 260f
                    )
                )
        )

        // Inner ring
        Box(
            modifier = Modifier
                .size(150.dp)
                .background(
                    color = ringColor.copy(alpha = if (isActive) 0.18f else 0.08f),
                    shape = CircleShape
                )
                .padding(6.dp)
                .background(
                    color = if (isActive) Color(0xFF4F8CFF) else Color(0xFF2A2F3A),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isActive) "ON" else "OFF",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color(0xFF0A0C10) else Color(0xFF8A93A6)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Stats
// ---------------------------------------------------------------------------

@Composable
private fun StatsRow(sessionMs: Long, totalMs: Long, completedCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatCell(
            value = formatDuration(sessionMs),
            label = if (sessionMs > 0) "this session" else "deep focus"
        )
        StatCell(value = formatDuration(totalMs), label = "all-time focus")
        StatCell(value = "$completedCount", label = "sessions")
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
        )
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0m"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun startShield(context: android.content.Context, dao: FocusDao, profileId: Long) {
    // Mark profile active + start a session, then start the service.
    val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )
    scope.launch {
        try {
            dao.clearActive()
            dao.setActive(profileId, true)
            dao.insertSession(
                com.codezmr.nullflow.data.FocusSession(
                    profileId = profileId,
                    startTime = System.currentTimeMillis()
                )
            )
            AppLog.d("startShield: profile $profileId marked active + session inserted")
            val intent = Intent(context, com.codezmr.nullflow.vpn.FocusVpnService::class.java)
                .setAction(com.codezmr.nullflow.vpn.FocusVpnService.ACTION_START)
                .putExtra(com.codezmr.nullflow.vpn.FocusVpnService.EXTRA_PROFILE_ID, profileId)
            context.startForegroundService(intent)
            AppLog.d("startShield: startForegroundService launched")
        } catch (e: Exception) {
            AppLog.e("startShield FAILED", e)
        }
    }
}

private fun stopShield(context: android.content.Context) {
    val intent = Intent(context, com.codezmr.nullflow.vpn.FocusVpnService::class.java)
        .setAction(com.codezmr.nullflow.vpn.FocusVpnService.ACTION_STOP)
    try {
        context.startService(intent)
        AppLog.d("stopShield: ACTION_STOP service launched")
    } catch (e: Exception) {
        AppLog.e("stopShield FAILED", e)
    }
}

private fun endCurrentSession(dao: FocusDao, scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        try {
            val running = dao.getRunningSession()
            if (running != null) {
                dao.endSession(running.id, System.currentTimeMillis())
                // Deactivate the profile so the next toggle starts fresh.
                dao.setActive(running.profileId, false)
                AppLog.d("endCurrentSession: session ${running.id} ended, profile ${running.profileId} deactivated")
            } else {
                AppLog.w("endCurrentSession: no running session found")
            }
        } catch (e: Exception) {
            AppLog.e("endCurrentSession FAILED", e)
        }
    }
}

private fun createDefaultProfile(dao: FocusDao): Long {
    // We need the new profile id synchronously (before launching the consent
    // dialog), so runBlocking is acceptable here — it's a single local insert.
    return kotlinx.coroutines.runBlocking {
        dao.insertProfile(FocusProfile(name = "Deep Work"))
    }
}

/**
 * A horizontal, scrollable row of the blocked apps' icons (24dp circles,
 * 8dp spacing) with a right-edge gradient fade as a scroll hint. Mirrors the
 * QS tile panel's icon row so the user sees exactly what's shielded.
 */
@Composable
private fun BlockedAppIconRow(
    context: android.content.Context,
    apps: List<com.codezmr.nullflow.data.BlockedApp>,
    bg: Color
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(apps, key = { it.packageName }) { app ->
                val painter = rememberAppIconPainter(context, app.packageName)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2430))
                ) {
                    Image(
                        painter = painter,
                        contentDescription = app.appName,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        // Right-edge gradient fade (scroll hint).
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(36.dp)
                .fillMaxHeight()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            bg.copy(alpha = 0f),
                            bg.copy(alpha = 0.9f)
                        )
                    )
                )
        )
    }
}
