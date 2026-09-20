package com.codezmr.nullflow.ui

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
    onOpenPicker: (Long) -> Unit,
    onOpenModeManager: () -> Unit,
    onCreateMode: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showNoAppsWarning by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // ---- State from Room ----
    val profiles by dao.observeProfiles().collectAsState(initial = emptyList())
    val profilesWithCount by dao.observeProfilesWithAppCount().collectAsState(initial = emptyList())
    val activeProfile by dao.observeActiveProfile().collectAsState(initial = null)
    val runningSession by dao.observeRunningSession().collectAsState(initial = null)
    val totalMs by dao.observeTotalFocusedMs().collectAsState(initial = 0L)
    val completedCount by dao.observeCompletedCount().collectAsState(initial = 0)
    // Live heat state from the VPN service (intercept count + 0..1 heat).
    // Emitted on the service's 1s timer tick; collectAsState keeps this off
    // the main thread.
    val heatState by com.codezmr.nullflow.vpn.FocusVpnService
        .heatState.collectAsState(initial = com.codezmr.nullflow.vpn.HeatState(0, 0f))

    // Recent sessions, minus accidental tap-tap-tap junk (< 15s).
    val rawRecentSessions by dao.observeRecentSessions(20).collectAsState(initial = emptyList())
    val recentSessions = remember(rawRecentSessions) {
        rawRecentSessions.filter { s ->
            (s.endTime ?: System.currentTimeMillis()) - s.startTime >= 15_000L
        }.take(5)
    }

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
    // user's default (from Settings), otherwise the first existing profile.
    val settings = remember { com.codezmr.nullflow.data.Settings.get(context) }
    val effectiveProfile: FocusProfile? = activeProfile
        ?: profiles.firstOrNull { it.id == settings.defaultProfileId }
        ?: profiles.firstOrNull()

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
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            // ---- 1. TOP: Brand mark + screen name + settings ----
            // The dashboard is the ROOT screen — no in-app back arrow (the
            // system back gesture already exits). Left slot shows the brand
            // null-ring mark instead.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF12151C))
                        .border(1.dp, Color(0xFF222733), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Null-ring brand mark (circle + slash), echoing the app icon.
                    Canvas(modifier = Modifier.size(16.dp)) {
                        val ring = 1.5.dp.toPx()
                        drawCircle(
                            color = Color(0xFFA0A0A0),
                            radius = size.minDimension / 2f - ring / 2f,
                            style = Stroke(width = ring)
                        )
                        drawLine(
                            color = Color(0xFFA0A0A0),
                            start = Offset(size.width * 0.22f, size.height * 0.78f),
                            end = Offset(size.width * 0.78f, size.height * 0.22f),
                            strokeWidth = ring
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "NULLFLOW",
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
                // Hero reactor core — live intercept count + heat-driven color.
                HeroToggle(
                    isActive = isActive,
                    heatState = heatState,
                    compact = settings.compactMode,
                    accent = accentColorFromSettings(settings.accentColor),
                    onClick = { onToggle() }
                )

                // Label below circle
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (isActive) "Shield on" else "Shield off",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isActive) accentColorFromSettings(settings.accentColor) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF12151C))
                                .border(1.dp, Color(0xFF222733), RoundedCornerShape(16.dp))
                                .padding(vertical = 28.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
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
                                Spacer(Modifier.height(18.dp))
                                SecondaryButton(
                                    text = "Create your first mode",
                                    icon = "+",
                                    onClick = onCreateMode,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
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
                            onClick = onOpenModeManager,
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
// Focus Telemetry Console
// ---------------------------------------------------------------------------
//
// Honest stats from live Room data:
//  - 3 metric cards: Total Uptime, Total Blocked, Peak Focus Time
//  - Day Navigator: prev/next to browse last 7 days, shows focus time +
//    blocked count for the selected day
//  - 7-day dot strip: visual overview, tap a dot to jump to that day

@Composable
private fun TelemetryConsole(
    topIntercepted: List<AppInterceptStats>,
    totalIntercepts: Int,
    totalMs: Long,
    peakHour: PeakHourStats?,
    dailyTelemetry: List<DailyFocusStats>
) {
    // Default to TODAY (the last row of the 7-day window). Resolved from the
    // actual data once it arrives — never assume a fixed index, and never show
    // a future date.
    var selectedDayIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(dailyTelemetry) {
        if (selectedDayIndex == -1 && dailyTelemetry.isNotEmpty()) {
            selectedDayIndex = dailyTelemetry.lastIndex
        }
    }

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

        // ---- 1. Metric cards ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                value = formatDuration(totalMs),
                label = "TOTAL FOCUS",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                value = "$totalIntercepts",
                label = "BLOCKED",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                value = formatPeakHour(peakHour),
                label = "PEAK HOUR",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        // ---- 2. Day Navigator ----
        if (dailyTelemetry.isNotEmpty() && selectedDayIndex != -1) {
            val days = dailyTelemetry
            val selected = days.getOrNull(selectedDayIndex) ?: days.last()
            val dayFmt = remember { SimpleDateFormat("EEE, MMM d", Locale.US) }

            // Prev / date / Next
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF12151C))
                        .border(1.dp, Color(0xFF222733), CircleShape)
                        .clickable(enabled = selectedDayIndex > 0) {
                            selectedDayIndex = (selectedDayIndex - 1).coerceAtLeast(0)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "←",
                        fontSize = 14.sp,
                        color = if (selectedDayIndex > 0) Color(0xFFA0A0A0) else Color(0xFF3A3A3A)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dayFmt.format(Date(selected.dayStart)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE6EAF0)
                    )
                    if (selected.dayStart == localMidnight(System.currentTimeMillis())) {
                        Text(
                            text = "Today",
                            fontSize = 10.sp,
                            color = Color(0xFF00E5FF)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF12151C))
                        .border(1.dp, Color(0xFF222733), CircleShape)
                        .clickable(enabled = selectedDayIndex < days.lastIndex) {
                            selectedDayIndex = (selectedDayIndex + 1).coerceAtMost(days.lastIndex)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "→",
                        fontSize = 14.sp,
                        color = if (selectedDayIndex < days.lastIndex) Color(0xFFA0A0A0) else Color(0xFF3A3A3A)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Day detail card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF12151C))
                    .border(1.dp, Color(0xFF222733), RoundedCornerShape(14.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (selected.focusMs > 0) formatDuration(selected.focusMs) else "—",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (selected.focusMs > 0) Color(0xFF00E5FF) else Color(0xFF3A4150)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Focus time",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (selected.interceptCount > 0) "${selected.interceptCount}" else "—",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (selected.interceptCount > 0) Color(0xFFE6EAF0) else Color(0xFF3A4150)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Blocked",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---- 3. 7-day dot strip ----
            val maxScore = remember(days) {
                days.maxOf { focusScore(it) }.coerceAtLeast(1f)
            }
            // Day-of-MONTH (e.g. "20"), not day-of-year ("S" = "269").
            val dayLetterFmt = remember { SimpleDateFormat("d", Locale.US) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                days.forEachIndexed { index, day ->
                    val score = (focusScore(day) / maxScore).coerceIn(0f, 1f)
                    val isSelected = index == selectedDayIndex
                    val isToday = day.dayStart == localMidnight(System.currentTimeMillis())
                    val dotColor = when {
                        isSelected -> Color(0xFF00E5FF)
                        score > 0.5f -> Color(0xFF00E5FF).copy(alpha = 0.7f)
                        score > 0f -> Color(0xFF00E5FF).copy(alpha = 0.35f)
                        else -> Color(0xFF2A2F3A)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { selectedDayIndex = index }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 14.dp else 10.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .then(
                                    if (isToday)
                                        Modifier.border(2.dp, Color(0xFF00E5FF).copy(alpha = 0.6f), CircleShape)
                                    else Modifier
                                )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = dayLetterFmt.format(Date(day.dayStart)),
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF00E5FF)
                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                    }
                }
            }
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
// Share dossier button
// ---------------------------------------------------------------------------



// ---------------------------------------------------------------------------
// Hero Toggle
// ---------------------------------------------------------------------------

/**
 * The Reactor Core — NullFlow's hero toggle.
 *
 * When armed, the core displays the LIVE intercept count and "heats up"
 * under distraction pressure:
 *
 *   heat 0.0  → deep cyan, slow 2.0s breathing
 *   heat 0.5  → amber, ~1.4s breathing
 *   heat 1.0  → incandescent red-orange, fast 0.9s breathing
 *
 * SINGLE-DRIVER ANIMATION: one [Animatable] holds the smoothed heat scalar.
 * Core color, glow radius, border alpha, and pulse speed are ALL derived
 * from it in the same frame — guaranteed synchronous, no drift between
 * properties. The breathing loop re-targets the Animatable each cycle with
 * a period derived from heat, so the core literally breathes faster as it
 * gets hotter.
 *
 * Color path is a 3-point lerp (cyan → amber → red) to avoid the desaturated
 * grey-purple dead zone a direct 2-point RGB lerp would pass through.
 */
@Composable
private fun HeroToggle(
    isActive: Boolean,
    heatState: com.codezmr.nullflow.vpn.HeatState,
    compact: Boolean = false,
    accent: Color = Color(0xFF00E5FF),
    onClick: () -> Unit
) {
    val size = if (compact) 140.dp else 190.dp
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.96f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "toggleScale"
    )

    // ---- Heat driver: one Animatable, everything derived from it ----
    // Target = 0 when disarmed, live heat when armed. The Animatable glides
    // between targets (600ms) so heat changes feel physical, not steppy.
    val heatAnim = remember { Animatable(0f) }
    LaunchedEffect(isActive, heatState.heat) {
        val target = if (isActive) heatState.heat else 0f
        if (heatAnim.value != target) {
            heatAnim.animateTo(
                targetValue = target,
                animationSpec = tween(600, easing = FastOutSlowInEasing)
            )
        }
    }
    val heat = heatAnim.value

    // ---- Impact recoil: discrete spring physics, independent of heat ----
    // When the intercept count ticks up, the core physically jolts sideways
    // (snapTo) then wobbles back to rest via a low-damping spring. Throttled
    // to max one shake per 500ms so a packet burst reads as a single impact,
    // not a glitching mess. Direction alternates per hit so the core feels
    // like it's swatting threats, not being pushed one way.
    val scope = rememberCoroutineScope()
    val recoilX = remember { Animatable(0f) }
    var lastImpactTime by remember { mutableLongStateOf(0L) }
    var impactDirection by remember { mutableIntStateOf(1) }
    LaunchedEffect(heatState.count) {
        if (!isActive || heatState.count == 0) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - lastImpactTime > 500) {
            lastImpactTime = now
            impactDirection = -impactDirection
            scope.launch {
                recoilX.snapTo(12f * impactDirection)
                recoilX.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.3f,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }

    // 3-point color lerp: cyan → amber → red-orange (no grey dead zone).
    val coreColor = if (heat < 0.5f) {
        lerpColor(accent, Color(0xFFFFB300), heat * 2f)
    } else {
        lerpColor(Color(0xFFFFB300), Color(0xFFFF3D00), (heat - 0.5f) * 2f)
    }

    // Breathing loop: period shrinks 2000ms → 900ms as heat rises.
    // Re-launches when the (quantized) period changes, so the tempo shifts
    // smoothly with the heat level without per-frame recomposition.
    val breathePeriodMs = (2000 - 1100 * heat).toInt()
    val breathePhase = remember { Animatable(0f) }
    LaunchedEffect(isActive, breathePeriodMs) {
        if (!isActive) {
            breathePhase.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            breathePhase.animateTo(
                targetValue = 1f,
                animationSpec = tween(breathePeriodMs, easing = FastOutSlowInEasing)
            )
            breathePhase.animateTo(
                targetValue = 0f,
                animationSpec = tween(breathePeriodMs, easing = FastOutSlowInEasing)
            )
        }
    }
    val breathe = if (isActive) breathePhase.value else 0f

    // Rotating segmented reticle: 360° over 12s, linear (render thread only).
    val infiniteTransition = rememberInfiniteTransition(label = "heroReticle")
    val reticleAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing)
        ),
        label = "reticleAngle"
    )

    // ---- Derived visual properties (all from heat + breathe, same frame) ----
    val activeBorderAlpha = 0.4f + 0.5f * breathe
    val glowElevation = (8 + 10 * breathe + 14 * heat).dp
    val glowAlpha = (0.35f + 0.25f * breathe + 0.3f * heat).coerceIn(0f, 1f)
    // Reticle dashes tighten as the core heats (scanner speeding up).
    val reticleDash = (10 - 4 * heat).dp
    val reticleGap = (5 - 2 * heat).dp

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // ---- Rotating segmented reticle (behind the core, active only) ----
        if (isActive) {
            Canvas(
                modifier = Modifier
                    .size(size + 26.dp)
                    .graphicsLayer { rotationZ = reticleAngle }
            ) {
                drawCircle(
                    color = coreColor.copy(alpha = 0.55f),
                    radius = size.toPx() / 2f,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(reticleDash.toPx(), reticleGap.toPx()), 0f
                        )
                    )
                )
            }
        }

        // ---- Core circle ----
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { translationX = recoilX.value }
                .shadow(
                    elevation = if (isActive) glowElevation else 12.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = if (isActive) coreColor.copy(alpha = glowAlpha) else Color.Black.copy(alpha = 0.5f),
                    spotColor = if (isActive) coreColor.copy(alpha = glowAlpha) else Color.Black.copy(alpha = 0.5f)
                )
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = if (isActive)
                            listOf(coreColor.copy(alpha = 0.14f + 0.1f * heat), coreColor.copy(alpha = 0.05f))
                        else
                            listOf(Color(0xFF1A1E26), Color(0xFF10131A)),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
                .border(
                    width = 3.dp,
                    color = if (isActive) coreColor.copy(alpha = activeBorderAlpha) else Color(0xFF2A2F3A),
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

            // ---- Center content: live count when armed, OFF when disarmed ----
            if (isActive) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${heatState.count}",
                        fontSize = if (compact) 30.sp else 44.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = coreColor,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "INTERCEPTED",
                        fontSize = if (compact) 8.sp else 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp,
                        color = coreColor.copy(alpha = 0.7f)
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3A4150))
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "OFF",
                        fontSize = if (compact) 14.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = Color(0xFF8A93A6)
                    )
                }
            }
        }
    }
}



private fun accentColorFromSettings(name: String): Color = when (name) {
    "green" -> Color(0xFF00FF88)
    "purple" -> Color(0xFFB44CFF)
    else -> Color(0xFF00E5FF)
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
                val now = System.currentTimeMillis()
                val durationMs = now - running.startTime
                if (durationMs < 15_000L) {
                    // Accidental tap-tap-tap: drop the row entirely so the
                    // history (and total-focus stats) stay meaningful.
                    dao.deleteSession(running.id)
                    dao.setActive(running.profileId, false)
                    AppLog.d("endCurrentSession: session ${running.id} was ${durationMs}ms — dropped as junk")
                } else {
                    dao.endSession(running.id, now, "completed")
                    // Deactivate the profile so the next toggle starts fresh.
                    dao.setActive(running.profileId, false)
                    AppLog.d("endCurrentSession: session ${running.id} ended, profile ${running.profileId} deactivated")
                }
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
