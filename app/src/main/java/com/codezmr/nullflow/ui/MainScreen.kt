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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var showModeManager by remember { mutableStateOf(false) }
    var showNoAppsWarning by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // ---- State from Room ----
    val profiles by dao.observeProfiles().collectAsState(initial = emptyList())
    val profilesWithCount by dao.observeProfilesWithAppCount().collectAsState(initial = emptyList())
    val activeProfile by dao.observeActiveProfile().collectAsState(initial = null)
    val runningSession by dao.observeRunningSession().collectAsState(initial = null)
    val totalMs by dao.observeTotalFocusedMs().collectAsState(initial = 0L)
    val completedCount by dao.observeCompletedCount().collectAsState(initial = 0)
    val recentSessions by dao.observeRecentSessions(5).collectAsState(initial = emptyList())

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

            // Guard: nothing to shield → show a clear message.
            if (blockedCount == 0) {
                AppLog.w("TOGGLE → blocked, 0 apps in profile $profileId. Showing warning.")
                Haptics.tick(context)
                showNoAppsWarning = true
                return
            }

            AppLog.d("TOGGLE → turning ON for profileId=$profileId ($blockedCount apps)")
            startShield(context, dao, profileId)
            Haptics.engage(context)
        }
    }

    var showStats by remember { mutableStateOf(false) }
    val statsExpanded by animateFloatAsState(
        targetValue = if (showStats) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "statsExpand"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            // ---- 1. TOP: Screen name + back + settings ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF12151C))
                        .border(1.dp, Color(0xFF222733), CircleShape)
                        .clickable {
                            AppLog.d("Dashboard: back pressed → finishing activity")
                            (context as? android.app.Activity)?.finish()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("←", fontSize = 16.sp, color = Color(0xFFA0A0A0))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "DASHBOARD",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF12151C))
                        .border(1.dp, Color(0xFF222733), CircleShape)
                        .clickable {
                            AppLog.d("Dashboard: settings opened")
                            showSettings = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙", fontSize = 16.sp, color = Color(0xFFA0A0A0))
                }
            }

            // ---- 2. CENTER: Hero + info ----
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Hero circle
                HeroToggle(
                    isActive = isActive,
                    label = if (isActive) "ON" else "OFF",
                    onClick = { onToggle() }
                )

                // Label below circle
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (isActive) "Shield on" else "Shield off",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isActive) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )

                Spacer(Modifier.height(28.dp))

                // Active mode info (when ON)
                if (isActive && effectiveProfile != null) {
                    val profile = effectiveProfile
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF12151C))
                            .border(1.dp, Color(0xFF222733), RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = profile.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$blockedCount app${if (blockedCount == 1) "" else "s"} shielded",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        if (blockedCount > 0) {
                            Spacer(Modifier.height(12.dp))
                            BlockedAppIconRow(
                                context = context,
                                apps = blockedApps,
                                bg = Color(0xFF12151C)
                            )
                        }
                        if (runningSession != null) {
                            Spacer(Modifier.height(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(32.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = formatDuration(now - runningSession!!.startTime),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF00E5FF)
                                    )
                                    Text(
                                        text = "this session",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = formatDuration(totalMs),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFFE6EAF0)
                                    )
                                    Text(
                                        text = "total focus",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Mode selector (when OFF)
                if (!isActive) {
                    if (profiles.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF12151C))
                                .border(1.dp, Color(0xFF222733), RoundedCornerShape(16.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No focus mode yet",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Create a mode to start shielding apps",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(16.dp))
                            SecondaryButton(
                                text = "Create your first mode",
                                icon = "+",
                                onClick = { showModeManager = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text(
                            text = "SELECT MODE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(Modifier.height(10.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF12151C))
                                .border(1.dp, Color(0xFF222733), RoundedCornerShape(16.dp))
                        ) {
                            profilesWithCount.forEachIndexed { index, p ->
                                val isSelected = activeProfile?.id == p.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (index < profilesWithCount.lastIndex)
                                                Modifier.border(
                                                    width = 0.dp,
                                                    color = Color.Transparent,
                                                    shape = RoundedCornerShape(0.dp)
                                                )
                                            else Modifier
                                        )
                                        .clickable {
                                            Haptics.tick(context)
                                            scope.launch {
                                                dao.clearActive()
                                                dao.setActive(p.id, true)
                                                AppLog.d("Dashboard: selected mode '${p.name}' (id=${p.id})")
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .border(
                                                width = 2.dp,
                                                color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF3A4150),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF00E5FF))
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = p.name,
                                            fontSize = 15.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) Color.White
                                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = if (p.appCount > 0)
                                                "${p.appCount} app${if (p.appCount == 1) "" else "s"}"
                                            else "No apps yet",
                                            fontSize = 12.sp,
                                            color = if (p.appCount > 0)
                                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                            else Color(0xFF00E5FF).copy(alpha = 0.7f)
                                        )
                                    }
                                    SmallActionButton(
                                        text = "Edit",
                                        onClick = { onOpenPicker(p.id) },
                                        active = false
                                    )
                                }
                                if (index < profilesWithCount.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(Color(0xFF222733))
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        SecondaryButton(
                            text = "Manage modes",
                            icon = "+",
                            onClick = { showModeManager = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ---- 3. Collapsible stats ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF12151C))
                        .border(1.dp, Color(0xFF222733), RoundedCornerShape(12.dp))
                        .clickable {
                            Haptics.tick(context)
                            showStats = !showStats
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Stats & History",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Text(
                        text = if (showStats) "▲" else "▼",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }

                if (showStats) {
                    Spacer(Modifier.height(10.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TelemetryConsole(
                            topIntercepted = topIntercepted,
                            totalIntercepts = totalIntercepts,
                            totalMs = totalMs,
                            peakHour = peakHour,
                            dailyTelemetry = dailyTelemetry
                        )
                        if (recentSessions.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            SessionHistory(sessions = recentSessions, dao = dao)
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }

        if (showModeManager) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showModeManager = false }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clickable(enabled = false) { }
                ) {
                    ModeManagerSheet(
                        dao = dao,
                        onDismiss = { showModeManager = false }
                    )
                }
            }
        }

        if (showNoAppsWarning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { showNoAppsWarning = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF12151C))
                        .border(1.dp, Color(0xFF222733), RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No apps in this mode",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Add at least one app to shield before turning on.",
                        fontSize = 13.sp,
                        color = Color(0xFFA0A0A0)
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(
                        text = "Add apps",
                        onClick = {
                            showNoAppsWarning = false
                            val profileId = effectiveProfile?.id ?: createDefaultProfile(dao)
                            onOpenPicker(profileId)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    GhostButton(
                        text = "Cancel",
                        onClick = { showNoAppsWarning = false }
                    )
                }
            }
        }

        if (showSettings) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showSettings = false }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clickable(enabled = false) { }
                ) {
                    SettingsScreen(
                        dao = dao,
                        onBack = { showSettings = false }
                    )
                }
            }
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
    dailyTelemetry: List<DailyFocusStats>
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
    if (days.isEmpty()) {
        Box(modifier = modifier)
        return
    }
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



// ---------------------------------------------------------------------------
// Hero Toggle
// ---------------------------------------------------------------------------

@Composable
private fun HeroToggle(isActive: Boolean, label: String, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.96f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "toggleScale"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.6f else 0f,
        animationSpec = tween(700),
        label = "glow"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFF00E5FF) else Color(0xFF2A2F3A),
        animationSpec = tween(500),
        label = "borderColor"
    )

    // Pulse animation when active
    var pulsePhase by remember { mutableStateOf(0f) }
    LaunchedEffect(isActive) {
        if (isActive) {
            while (true) {
                pulsePhase = 0f
                val start = System.currentTimeMillis()
                while (pulsePhase < 1f) {
                    pulsePhase = ((System.currentTimeMillis() - start) % 2000) / 2000f
                    delay(50)
                }
            }
        }
    }
    val pulseGlow = if (isActive) 0.3f + 0.3f * (0.5f + 0.5f * kotlin.math.sin(pulsePhase * 2 * kotlin.math.PI).toFloat()) else 0f

    Box(
        modifier = Modifier
            .size(190.dp)
            .scale(scale)
            .shadow(
                elevation = if (isActive) 28.dp else 12.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = if (isActive) Color(0xFF00E5FF).copy(alpha = (glowAlpha + pulseGlow).coerceIn(0f, 1f)) else Color.Black.copy(alpha = 0.5f),
                spotColor = if (isActive) Color(0xFF00E5FF).copy(alpha = (glowAlpha + pulseGlow).coerceIn(0f, 1f)) else Color.Black.copy(alpha = 0.5f)
            )
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = if (isActive)
                        listOf(Color(0xFF0D2A30), Color(0xFF0A1A20))
                    else
                        listOf(Color(0xFF1A1E26), Color(0xFF10131A)),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .border(
                width = 3.dp,
                color = borderColor,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Specular highlight
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color.White.copy(alpha = 0f)
                        ),
                        center = Offset(60f, 60f),
                        radius = 200f
                    )
                )
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (isActive) Color(0xFF00E5FF) else Color(0xFF3A4150))
                    .shadow(
                        elevation = if (isActive) 10.dp else 0.dp,
                        shape = CircleShape,
                        ambientColor = Color(0xFF00E5FF).copy(alpha = if (isActive) 0.7f else 0f),
                        spotColor = Color(0xFF00E5FF).copy(alpha = if (isActive) 0.7f else 0f)
                    )
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = if (isActive) Color(0xFF00E5FF) else Color(0xFF8A93A6)
            )
        }
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

// ---------------------------------------------------------------------------
// Session History — last 5 completed sessions
// ---------------------------------------------------------------------------

@Composable
private fun SessionHistory(
    sessions: List<com.codezmr.nullflow.data.FocusSession>,
    dao: FocusDao
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "RECENT SESSIONS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(8.dp))

        sessions.forEach { session ->
            val durationMs = (session.endTime ?: System.currentTimeMillis()) - session.startTime
            val profileName = remember(session.profileId) {
                runCatching {
                    kotlinx.coroutines.runBlocking { dao.getProfile(session.profileId)?.name }
                }.getOrNull() ?: "Unknown"
            }
            val dateFmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.US) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF12151C))
                    .border(1.dp, Color(0xFF222733), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profileName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE6EAF0)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = dateFmt.format(Date(session.startTime)),
                        fontSize = 11.sp,
                        color = Color(0xFF808080)
                    )
                }
                Text(
                    text = formatDuration(durationMs),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00E5FF)
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
