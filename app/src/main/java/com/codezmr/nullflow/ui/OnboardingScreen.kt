package com.codezmr.nullflow.ui

import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.codezmr.nullflow.R
import com.codezmr.nullflow.data.Settings
import com.codezmr.nullflow.data.SystemHealth
import kotlinx.coroutines.launch

// ---- Neumorphic palette (pure dark + icy blue LED) ----
private val PureBlack = Color(0xFF0A0A0C)
private val SurfaceDark = Color(0xFF141418)
private val IcyBlue = Color(0xFF4FC3F7)
private val ElectricBlue = Color(0xFF2979FF)
private val NeonCyan = Color(0xFF00E5FF)
private val StarkWhite = Color(0xFFF2F4F8)

/**
 * First-launch onboarding — a 3-step horizontal pager.
 *
 * One cognitive decision per screen:
 *  - Step 1 "The Hook": value proposition only. Continue.
 *  - Step 2 "Arm the Shield": mandatory permissions (VPN gates Continue;
 *    Notifications is required-for-timer but skippable).
 *  - Step 3 "Seamless Focus": optional enhancements (battery limits,
 *    Quick Settings tile). Finish Setup — always available.
 *
 * No marketing cards, no tip carousel, no buried CTA: the action button
 * lives at the bottom of every page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onEnter: () -> Unit,
    onRequestAddQsTile: ((Boolean) -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ---- Permission state (real-time) ----
    var hasNotificationPerm by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasVpnPerm by remember {
        mutableStateOf(VpnService.prepare(context) == null)
    }
    // Optional: battery-optimization exemption (keeps the shield alive).
    var batteryExempt by remember {
        mutableStateOf(SystemHealth.isIgnoringBatteryOptimizations(context))
    }
    // Optional: Quick Settings tile pinned.
    var tileAdded by remember { mutableStateOf(false) }
    var tileRequesting by remember { mutableStateOf(false) }

    // ---- Launchers (one at a time) ----
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPerm = granted
        if (granted) Haptics.engage(context)
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            hasVpnPerm = true
            Haptics.engage(context)
        }
    }

    // Battery-optimization settings screen (no permission prompt — the user
    // flips the switch themselves in the system UI). Re-check on return.
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val now = SystemHealth.isIgnoringBatteryOptimizations(context)
        if (now && !batteryExempt) Haptics.engage(context)
        batteryExempt = now
    }

    // ---- Real-time re-check on resume (user returns from system screens) ----
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= 33) {
                    val granted = context.checkSelfPermission(
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted != hasNotificationPerm) {
                        hasNotificationPerm = granted
                        if (granted) Haptics.engage(context)
                    }
                }
                val vpnReady = VpnService.prepare(context) == null
                if (vpnReady != hasVpnPerm) {
                    hasVpnPerm = vpnReady
                    if (vpnReady) Haptics.engage(context)
                }
                val batteryNow = SystemHealth.isIgnoringBatteryOptimizations(context)
                if (batteryNow != batteryExempt) {
                    batteryExempt = batteryNow
                    if (batteryNow) Haptics.engage(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ---- Pager ----
    val pagerState = rememberPagerState(pageCount = { 3 })
    var currentPage by remember { mutableIntStateOf(0) }
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.currentPage }
            .collect { currentPage = it }
    }

    // ---- Swipe guard: block swiping to page 2 (Enhancements) until the VPN
    // permission is granted. The user must tap "Enable Local Shield" and grant
    // it — swiping left/right cannot bypass the mandatory permission gate.
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.currentPage }
            .collect { page ->
                if (page >= 2 && !hasVpnPerm) {
                    pagerState.scrollToPage(1)
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        AmbientGlow()

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> HookPage()
                1 -> PermissionsPage(
                    hasNotificationPerm = hasNotificationPerm,
                    hasVpnPerm = hasVpnPerm,
                    onNotificationClick = {
                        if (!hasNotificationPerm) {
                            Haptics.tick(context)
                            if (Build.VERSION.SDK_INT >= 33) {
                                notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                hasNotificationPerm = true
                            }
                        }
                    },
                    onVpnClick = {
                        if (!hasVpnPerm) {
                            Haptics.tick(context)
                            val intent = VpnService.prepare(context)
                            if (intent != null) {
                                vpnLauncher.launch(intent)
                            } else {
                                hasVpnPerm = true
                            }
                        }
                    }
                )
                2 -> EnhancementsPage(
                    batteryExempt = batteryExempt,
                    tileAdded = tileAdded,
                    tileRequesting = tileRequesting,
                    onBatteryClick = {
                        if (!batteryExempt) {
                            Haptics.tick(context)
                            batteryLauncher.launch(SystemHealth.batterySettingsIntent(context))
                        }
                    },
                    onTileClick = {
                        if (tileAdded || tileRequesting) return@EnhancementsPage
                        Haptics.tick(context)
                        tileRequesting = true
                        onRequestAddQsTile { added ->
                            tileAdded = added
                            tileRequesting = false
                            if (added) Haptics.engage(context)
                        }
                    }
                )
            }
        }

        // ---- Page dots ----
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .size(if (i == currentPage) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == currentPage) NeonCyan
                            else Color(0xFF2A2F3A)
                        )
                )
            }
        }

        // ---- Bottom action bar (one CTA per page, always visible) ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(PureBlack)
                .padding(horizontal = 26.dp)
                .padding(bottom = 34.dp)
        ) {
            when (currentPage) {
                0 -> OnboardingActionButton(
                    text = "Continue",
                    enabled = true,
                    onClick = {
                        Haptics.tick(context)
                        scope.launch { pagerState.animateScrollToPage(1) }
                    }
                )
                1 -> OnboardingActionButton(
                    text = if (hasVpnPerm) "Continue" else "Enable Local Shield",
                    enabled = hasVpnPerm,
                    onClick = {
                        Haptics.engage(context)
                        scope.launch { pagerState.animateScrollToPage(2) }
                    }
                )
                2 -> OnboardingActionButton(
                    text = "Commit & Finish",
                    enabled = true,
                    onClick = {
                        Haptics.engage(context)
                        Settings.get(context).markOnboarded()
                        onEnter()
                    }
                )
            }

            // Developer signature — absolute bottom, beneath the CTA.
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Crafted by CodeZMR",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Step 1 — The Hook
// ---------------------------------------------------------------------------

@Composable
private fun HookPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Hero: app icon with a continuous sonar-pulse ripple behind it.
        SonarHero()

        Spacer(Modifier.height(34.dp))

        Text(
            text = "Take back your attention.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = StarkWhite,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )

        Spacer(Modifier.height(28.dp))

        // Three-point feature list (high-value info, clean typography).
        FeatureRow(icon = "shield", text = "Block distracting apps instantly.")
        Spacer(Modifier.height(14.dp))
        FeatureRow(icon = "chart", text = "Track your focus and peak hours.")
        Spacer(Modifier.height(14.dp))
        FeatureRow(icon = "lock", text = "100% offline. Zero tracking.")
    }
}

/**
 * One feature row: a small line-icon in a tinted circle + a single line of
 * benefit copy. Clean typography, no emoji.
 */
@Composable
private fun FeatureRow(icon: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(IcyBlue.copy(alpha = 0.12f))
                .border(1.dp, IcyBlue.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            FeatureIcon(kind = icon)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = StarkWhite.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f)
        )
    }
}

/** Minimal line-icons drawn with Canvas (no emoji, no vector assets). */
@Composable
private fun FeatureIcon(kind: String) {
    val density = LocalDensity.current
    val stroke = with(density) { 2.dp.toPx() }
    val c = IcyBlue
    Canvas(modifier = Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        when (kind) {
            "shield" -> {
                // Shield outline: top edge + two sides tapering to a point.
                drawLine(c, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.85f, h * 0.28f), stroke)
                drawLine(c, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.15f, h * 0.28f), stroke)
                drawLine(c, Offset(w * 0.15f, h * 0.28f), Offset(w * 0.15f, h * 0.55f), stroke)
                drawLine(c, Offset(w * 0.85f, h * 0.28f), Offset(w * 0.85f, h * 0.55f), stroke)
                drawLine(c, Offset(w * 0.15f, h * 0.55f), Offset(w * 0.5f, h * 0.88f), stroke)
                drawLine(c, Offset(w * 0.85f, h * 0.55f), Offset(w * 0.5f, h * 0.88f), stroke)
            }
            "chart" -> {
                // Three ascending bars.
                drawLine(c, Offset(w * 0.2f, h * 0.8f), Offset(w * 0.2f, h * 0.55f), stroke)
                drawLine(c, Offset(w * 0.5f, h * 0.8f), Offset(w * 0.5f, h * 0.35f), stroke)
                drawLine(c, Offset(w * 0.8f, h * 0.8f), Offset(w * 0.8f, h * 0.2f), stroke)
            }
            "lock" -> {
                // Padlock: body rectangle + shackle arc (approximated with lines).
                drawLine(c, Offset(w * 0.28f, h * 0.5f), Offset(w * 0.72f, h * 0.5f), stroke)
                drawLine(c, Offset(w * 0.28f, h * 0.5f), Offset(w * 0.28f, h * 0.82f), stroke)
                drawLine(c, Offset(w * 0.72f, h * 0.5f), Offset(w * 0.72f, h * 0.82f), stroke)
                drawLine(c, Offset(w * 0.28f, h * 0.82f), Offset(w * 0.72f, h * 0.82f), stroke)
                // Shackle
                drawLine(c, Offset(w * 0.38f, h * 0.5f), Offset(w * 0.38f, h * 0.32f), stroke)
                drawLine(c, Offset(w * 0.38f, h * 0.32f), Offset(w * 0.62f, h * 0.32f), stroke)
                drawLine(c, Offset(w * 0.62f, h * 0.32f), Offset(w * 0.62f, h * 0.5f), stroke)
            }
        }
    }
}

/**
 * The hero: the app icon centered, with a continuous slow-expanding sonar
 * ripple behind it (signals the shield is an active, scanning entity).
 */
@Composable
private fun SonarHero() {
    val infiniteTransition = rememberInfiniteTransition(label = "HeroPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Alpha"
    )

    Box(
        modifier = Modifier.size(150.dp),
        contentAlignment = Alignment.Center
    ) {
        // Animated sonar ripple (expands + fades, restarts).
        Box(
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                    alpha = pulseAlpha
                }
                .clip(CircleShape)
                .background(NeonCyan)
        )
        // Static hero circle + app icon on top.
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF1E1E24), Color(0xFF0C0C10))
                    )
                )
                .shadow(elevation = 14.dp, shape = CircleShape)
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "NullFlow",
                modifier = Modifier.size(72.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Step 2 — Mandatory Permissions (The Engine)
// ---------------------------------------------------------------------------

@Composable
private fun PermissionsPage(
    hasNotificationPerm: Boolean,
    hasVpnPerm: Boolean,
    onNotificationClick: () -> Unit,
    onVpnClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Lock in your focus.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = StarkWhite
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Two steps to make distractions impossible.",
            style = MaterialTheme.typography.bodyMedium,
            color = StarkWhite.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(30.dp))

        PermissionRow(
            title = "Timer Notifications",
            subtitle = "Track your focus progress at a glance.",
            granted = hasNotificationPerm,
            onClick = onNotificationClick
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "Local Focus Shield",
            subtitle = "Safely cuts off internet to blocked apps.",
            granted = hasVpnPerm,
            onClick = onVpnClick
        )
        // Pre-framing micro-text: Android's system dialog will say "VPN" and
        // look scary. Warning the user BEFORE they click disarms the fear and
        // positions us as the trusted developer protecting them.
        Spacer(Modifier.height(10.dp))
        Text(
            text = "(Android uses its VPN system for this, but 0 bytes ever leave your phone.)",
            style = MaterialTheme.typography.bodySmall,
            color = StarkWhite.copy(alpha = 0.35f),
            textAlign = TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------------------
// Step 3 — Optional Enhancements (Bulletproofing)
// ---------------------------------------------------------------------------

@Composable
private fun EnhancementsPage(
    batteryExempt: Boolean,
    tileAdded: Boolean,
    tileRequesting: Boolean,
    onBatteryClick: () -> Unit,
    onTileClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Bulletproof the system.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = StarkWhite
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Recommended to prevent interruptions.",
            style = MaterialTheme.typography.bodyMedium,
            color = StarkWhite.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(30.dp))

        PermissionRow(
            title = "Unrestricted Battery",
            subtitle = "Stops your phone from accidentally turning off the blocker.",
            granted = batteryExempt,
            onClick = onBatteryClick
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "Quick Settings Tile",
            subtitle = "Swipe down to activate focus mode instantly.",
            granted = tileAdded,
            pending = tileRequesting,
            onClick = onTileClick
        )
    }
}

// ---------------------------------------------------------------------------
// Shared components
// ---------------------------------------------------------------------------

/** One permission/enhancement row: status circle + title + subtitle + action. */
@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    pending: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .shadow(
                elevation = if (granted) 12.dp else 4.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = NeonCyan.copy(alpha = if (granted) 0.55f else 0f),
                spotColor = NeonCyan.copy(alpha = if (granted) 0.55f else 0f)
            )
            .background(
                brush = if (granted)
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1A2A30), Color(0xFF101820))
                    )
                else
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF101014), Color(0xFF18181E))
                    )
            )
            .border(
                width = if (granted) 2.dp else 1.dp,
                color = if (granted) NeonCyan else Color(0xFF222733),
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(enabled = !granted && !pending, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status circle (check when granted, subtle ring otherwise)
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(color = if (granted) NeonCyan else SurfaceDark)
                .shadow(elevation = if (granted) 6.dp else 0.dp, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (granted) {
                Canvas(modifier = Modifier.size(18.dp)) {
                    val stroke = 3.dp.toPx()
                    drawLine(
                        color = Color(0xFF04141A),
                        start = Offset(size.width * 0.12f, size.height * 0.52f),
                        end = Offset(size.width * 0.42f, size.height * 0.82f),
                        strokeWidth = stroke
                    )
                    drawLine(
                        color = Color(0xFF04141A),
                        start = Offset(size.width * 0.42f, size.height * 0.82f),
                        end = Offset(size.width * 0.9f, size.height * 0.2f),
                        strokeWidth = stroke
                    )
                }
            } else {
                // Subtle inner ring (clean typography, no emoji).
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .border(2.dp, IcyBlue.copy(alpha = 0.6f), CircleShape)
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = StarkWhite.copy(alpha = if (granted) 0.6f else 0.9f)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = StarkWhite.copy(alpha = if (granted) 0.3f else 0.45f)
            )
        }

        if (!granted) {
            Text(
                text = if (pending) "…" else "Tap",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = IcyBlue
            )
        }
    }
}

/** The single bottom CTA, shared by all three pages. */
@Composable
private fun OnboardingActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = if (enabled)
                    Brush.linearGradient(
                        colors = listOf(NeonCyan, Color(0xFF00B8D4))
                    )
                else
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF2A2F3A), Color(0xFF1E222B))
                    )
            )
            .shadow(
                elevation = if (enabled) 10.dp else 3.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = if (enabled) NeonCyan.copy(alpha = 0.3f) else Color.Transparent,
                spotColor = if (enabled) NeonCyan.copy(alpha = 0.3f) else Color.Transparent
            )
            .clickable(enabled = enabled, onClick = onClick)
            .height(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = if (enabled) Color(0xFF0A0C10) else StarkWhite.copy(alpha = 0.35f)
        )
    }
}

// ---------------------------------------------------------------------------
// Ambient glow — soft radial gradients for premium depth (non-interactive)
// ---------------------------------------------------------------------------

@Composable
private fun AmbientGlow() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElectricBlue.copy(alpha = 0.18f),
                        ElectricBlue.copy(alpha = 0.0f)
                    ),
                    center = Offset(x = 0f, y = 0f),
                    radius = 900f
                )
            )
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonCyan.copy(alpha = 0.10f),
                        NeonCyan.copy(alpha = 0.0f)
                    ),
                    center = Offset(x = 1200f, y = 1600f),
                    radius = 700f
                )
            )
    )
}


