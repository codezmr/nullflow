package com.codezmr.nullflow.ui

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codezmr.nullflow.AppLog
import com.codezmr.nullflow.data.AppInterceptStats
import com.codezmr.nullflow.data.DailyFocusStats
import com.codezmr.nullflow.data.FocusDao
import com.codezmr.nullflow.data.FocusProfile
import com.codezmr.nullflow.data.PeakHourStats
import com.codezmr.nullflow.ui.tile.rememberAppIconPainter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The whole app in one screen (Big Tech approach):
 *  - Hero toggle (massive, animated, haptic)
 *  - Focus Telemetry Console (live interception data from the VPN blackhole)
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

    // ---- Focus Telemetry Console: live interception data ----
    // Top 5 most-intercepted apps (drives the donut + the threat ledger).
    val topIntercepted by dao.getInterceptionsByApp(5).collectAsState(initial = emptyList())
    // Total intercepted pings across all time ("Threats Neutralized").
    val totalIntercepts by dao.getTotalIntercepts().collectAsState(initial = 0)
    // The hour of day with the most intercepts ("Peak Focus Time").
    val peakHour by dao.getPeakInterceptHour().collectAsState(initial = null)
    // Per-day telemetry for the last 7 days (heatmap).
    val dailyTelemetry by dao.getDailyTelemetry(localMidnight(System.currentTimeMillis()))
        .collectAsState(initial = emptyList())

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
                    totalDeflected = totalIntercepts.toLong(),
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
        // Root Column: fillMaxSize. The center content gets weight(1f) so it
        // takes ALL remaining space. The bottom dashboard is anchored with NO
        // weight, so it is permanently pinned to the bottom edge and can never
        // be pushed off-screen by the (unconstrained) center content.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
        ) {
            // ---- 1. TOP: Tactical HUD header (fixed height) ----
            HudHeader(
                isShieldActive = isActive
            )

            // ---- 2. CENTER: Hero Toggle + App Icons (weight(1f) — dynamic) ----
            // This Column absorbs all remaining vertical space. Its content is
            // centered vertically so the Hero Toggle stays visually balanced
            // regardless of how many app icons are shown below it.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // ---- THE HERO TOGGLE ----
                HeroToggle(
                    isActive = isActive,
                    onClick = { onToggle() }
                )

                Spacer(Modifier.height(24.dp))

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
                    Spacer(Modifier.height(12.dp))
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
                        Spacer(Modifier.height(10.dp))
                        BlockedAppIconRow(
                            context = context,
                            apps = blockedApps,
                            bg = bgColor
                        )
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No focus mode yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(10.dp))
                    // Fresh install: give a clear way to pick apps BEFORE toggling on.
                    // Styled as an OutlinedButton: dark surface + 1dp cyan border.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF12151C))
                            .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
                            .clickable {
                                val profileId = createDefaultProfile(dao)
                                onOpenPicker(profileId)
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Choose apps to shield",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF00E5FF)
                        )
                    }
                }
            }

            // ---- 3. BOTTOM: Anchored dashboard (NO weight — pinned to bottom) ----
            // The center's weight(1f) absorbs all extra space, so this block is
            // permanently pinned to the bottom edge and can never be pushed
            // off-screen by the center content.
            if (isActive) {
                // ---- Active: live session stats ----
                StatsRow(
                    sessionMs = if (runningSession != null)
                        (now - runningSession!!.startTime) else 0L,
                    totalMs = totalMs,
                    completedCount = completedCount
                )
            } else {
                // ---- Inactive: the Focus Telemetry Console ----
                TelemetryConsole(
                    topIntercepted = topIntercepted,
                    totalIntercepts = totalIntercepts,
                    totalMs = totalMs,
                    peakHour = peakHour,
                    dailyTelemetry = dailyTelemetry,
                    dossierBusy = dossierBusy,
                    onShareDossier = { onShareDossier() }
                )
            }

            Spacer(Modifier.height(24.dp))
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
// Focus Telemetry Console (inactive state)
// ---------------------------------------------------------------------------
//
// A cybersecurity-style observability hub, built STRICTLY from live Room data:
//  - Telemetry header: 3 glassmorphic metric cards (Total Uptime, Threats
//    Neutralized, Peak Focus Time).
//  - Interception donut: thick-ringed Canvas chart of the top 3 most-blocked
//    apps (Cyan / Purple / Electric Blue).
//  - Threat ledger: the top 5 blocked apps with icon, exact intercept count,
//    and a progress bar proportional to the top app's count.
//  - 7-day activity heatmap: one rounded box per day, color-coded by the
//    daily Focus Score (dim #1A1D24 → glowing #00E5FF).
//
// If the intercept log is empty, a sleek "[ AWAITING NETWORK TELEMETRY ]"
// wireframe is rendered instead of a 0% pie chart.

// Neon palette for the donut segments (top 3 apps).
private val DonutCyan = Color(0xFF00E5FF)
private val DonutPurple = Color(0xFFB44CFF)
private val DonutBlue = Color(0xFF4F8CFF)
private val DonutPalette = listOf(DonutCyan, DonutPurple, DonutBlue)

// Heatmap palette: dim (no focus) → glowing (deep work).
private val HeatDim = Color(0xFF1A1D24)
private val HeatGlow = Color(0xFF00E5FF)

@Composable
private fun TelemetryConsole(
    topIntercepted: List<AppInterceptStats>,
    totalIntercepts: Int,
    totalMs: Long,
    peakHour: PeakHourStats?,
    dailyTelemetry: List<DailyFocusStats>,
    dossierBusy: Boolean,
    onShareDossier: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "FOCUS TELEMETRY",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )

        Spacer(Modifier.height(10.dp))

        // ---- 1. Telemetry header: glassmorphic metric cards ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                value = formatDuration(totalMs),
                label = "TOTAL UPTIME",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                value = "$totalIntercepts",
                label = "THREATS NEUTRALIZED",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                value = formatPeakHour(peakHour),
                label = "PEAK FOCUS TIME",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(14.dp))

        if (totalIntercepts == 0) {
            // ---- Empty state: no intercepts logged yet ----
            AwaitingTelemetryWireframe()
        } else {
            // ---- 2. Interception donut (top 3 apps) ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                InterceptionDonut(
                    stats = topIntercepted.take(3),
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(12.dp))

            // ---- 3. Threat ledger: top 5 blocked apps ----
            Text(
                text = "THREAT LEDGER",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(6.dp))
            InterceptLedger(
                stats = topIntercepted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )

            Spacer(Modifier.height(12.dp))

            // ---- 4. 7-day activity heatmap ----
            Text(
                text = "7-DAY ACTIVITY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(6.dp))
            ActivityHeatmap(
                daily = dailyTelemetry,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(14.dp))

        // ---- Share the Zero-Leak Dossier ----
        ShareDossierButton(
            busy = dossierBusy,
            onClick = onShareDossier
        )
    }
}

// ---------------------------------------------------------------------------
// Telemetry header metric card — dark glassmorphic (#12151C / #222733 border)
// ---------------------------------------------------------------------------

@Composable
private fun MetricCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF12151C))
            .border(1.dp, Color(0xFF222733), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF00E5FF),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// Interception donut — thick-ringed Canvas chart of the top 3 blocked apps
// ---------------------------------------------------------------------------

@Composable
private fun InterceptionDonut(
    stats: List<AppInterceptStats>,
    modifier: Modifier = Modifier
) {
    val total = stats.sumOf { it.interceptCount.toLong() }.coerceAtLeast(1L)
    // Animated reveal: the ring sweeps in on first draw.
    val reveal by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "donutReveal"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = minOf(size.width, size.height) / 2f * 0.86f
            val strokeWidth = radius * 0.34f // thick ring
            val gapDeg = 3f // small gap between segments

            var startAngle = -90f // 12 o'clock
            stats.forEachIndexed { i, stat ->
                val sweep = (stat.interceptCount.toFloat() / total.toFloat()) * 360f * reveal
                if (sweep > 0.1f) {
                    drawArc(
                        color = DonutPalette[i % DonutPalette.size],
                        startAngle = startAngle + gapDeg / 2f,
                        sweepAngle = (sweep - gapDeg).coerceAtLeast(0.1f),
                        useCenter = false,
                        topLeft = Offset(cx - radius, cy - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                }
                startAngle += sweep
            }
        }

        // Center readout: total threats neutralized.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${stats.sumOf { it.interceptCount }}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFE6EAF0)
            )
            Text(
                text = "DROPPED",
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Threat ledger — LazyColumn of the top blocked apps
// ---------------------------------------------------------------------------

@Composable
private fun InterceptLedger(
    stats: List<AppInterceptStats>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val maxCount = stats.firstOrNull()?.interceptCount?.coerceAtLeast(1) ?: 1

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(stats, key = { it.packageName }) { stat ->
            val painter = rememberAppIconPainter(context, stat.packageName)
            val appName = remember(stat.packageName) {
                loadAppName(context, stat.packageName)
            }
            val progress = stat.interceptCount.toFloat() / maxCount.toFloat()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF12151C))
                    .border(1.dp, Color(0xFF222733), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                // App icon (28dp rounded square).
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E2430))
                ) {
                    Image(
                        painter = painter,
                        contentDescription = appName,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = appName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE6EAF0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${stat.interceptCount}×",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00E5FF)
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    // Progress bar: filled proportional to the top app's count.
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = DonutCyan,
                        trackColor = Color(0xFF1E222B)
                    )
                }
            }
        }
    }
}

/** Resolve a package name to its human-readable app label (cached). */
private val appNameCache = HashMap<String, String>()

private fun loadAppName(context: android.content.Context, packageName: String): String {
    appNameCache[packageName]?.let { return it }
    val name = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        // Uninstalled app — fall back to the last segment of the package name.
        packageName.substringAfterLast('.')
    }
    appNameCache[packageName] = name
    return name
}

// ---------------------------------------------------------------------------
// 7-day activity heatmap — one rounded box per day, color-coded by Focus Score
// ---------------------------------------------------------------------------

@Composable
private fun ActivityHeatmap(
    daily: List<DailyFocusStats>,
    modifier: Modifier = Modifier
) {
    // The DAO always returns exactly 7 rows (oldest→newest, anchored at
    // localMidnight(now-6d)); days with no activity come back as zeros.
    val days = remember(daily) { daily }
    // Normalize the Focus Score against the best day (deep work = 1.0).
    val maxScore = remember(days) {
        days.maxOf { focusScore(it) }.coerceAtLeast(1f)
    }
    val dayLabelFmt = remember { SimpleDateFormat("EEE", Locale.US) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        days.forEach { day ->
            val score = (focusScore(day) / maxScore).coerceIn(0f, 1f)
            val isToday = day.dayStart == localMidnight(System.currentTimeMillis())
            val cellColor = lerpColor(HeatDim, HeatGlow, score)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(cellColor.copy(alpha = 0.12f + 0.88f * score))
                    .border(
                        width = 1.dp,
                        color = if (isToday) HeatGlow.copy(alpha = 0.8f) else Color(0xFF222733),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = dayLabelFmt.format(Date(day.dayStart)),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (score > 0.5f) Color(0xFF0A0C10)
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (day.focusMs > 0) formatDuration(day.focusMs) else "—",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (score > 0.5f) Color(0xFF0A0C10)
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Daily Focus Score (0..1 scale before normalization): focused minutes are the
 * primary signal; intercepted pings add a small secondary weight (a day where
 * the shield worked hard is still a "focus" day).
 */
private fun focusScore(day: DailyFocusStats): Float {
    val focusMinutes = day.focusMs / 60_000f
    val interceptWeight = day.interceptCount * 0.5f
    return (focusMinutes + interceptWeight).coerceAtLeast(0f)
}

/** Linear interpolation between two colors (t in 0..1). */
private fun lerpColor(from: Color, to: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * tt,
        green = from.green + (to.green - from.green) * tt,
        blue = from.blue + (to.blue - from.blue) * tt,
        alpha = from.alpha + (to.alpha - from.alpha) * tt
    )
}

// ---------------------------------------------------------------------------
// Empty state — "[ AWAITING NETWORK TELEMETRY ]" wireframe
// ---------------------------------------------------------------------------

@Composable
private fun AwaitingTelemetryWireframe() {
    // Subtle pulsing glow so the wireframe feels alive, not dead.
    val pulse by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
        label = "awaitPulse"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF12151C))
            .border(
                width = 1.dp,
                color = Color(0xFF00E5FF).copy(alpha = 0.15f + 0.2f * pulse),
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Wireframe "signal" glyph: three rising bars (monospace aesthetic).
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(8.dp, 14.dp, 20.dp).forEach { h ->
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(h)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF00E5FF).copy(alpha = 0.25f + 0.35f * pulse))
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "[ AWAITING NETWORK TELEMETRY ]",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                color = Color(0xFF00E5FF).copy(alpha = 0.5f + 0.4f * pulse)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Activate the shield to start logging intercepted pings",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Share dossier button
// ---------------------------------------------------------------------------

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
// Stats (active session)
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

/** Format a peak hour-of-day (0-23) as e.g. "09:00 AM". Null → "—". */
private fun formatPeakHour(peak: PeakHourStats?): String {
    if (peak == null) return "—"
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, peak.hourOfDay)
    val fmt = SimpleDateFormat("hh:00 a", Locale.US)
    return fmt.format(cal.time)
}

/** Local-midnight epoch-ms for the given instant (day boundary for the heatmap). */
private fun localMidnight(epochMs: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochMs
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
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
