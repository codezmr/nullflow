package com.codezmr.nullflow.ui

import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.VibrationEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codezmr.nullflow.R
import com.codezmr.nullflow.data.Settings
import com.codezmr.nullflow.data.SystemHealth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---- Neumorphic palette (pure dark + icy blue LED) ----
private val PureBlack = Color(0xFF0A0A0C)
private val SurfaceDark = Color(0xFF141418)
private val IcyBlue = Color(0xFF4FC3F7)
private val ElectricBlue = Color(0xFF2979FF)
private val NeonCyan = Color(0xFF00E5FF)
private val MutedGrey = Color(0xFF3A3A42)
private val StarkWhite = Color(0xFFF2F4F8)

// ---- 30 short lines: tips, tricks & motivation (rotates randomly) ----
private val TIPS: List<Tip> = listOf(
    Tip("TIP", "Block your most-used app first — that's the real test."),
    Tip("TIP", "One app at a time. Silence is a feature."),
    Tip("TIP", "Turn the shield on before you open the app, not after."),
    Tip("TIP", "A blocked app can't send you a notification. That's the point."),
    Tip("TIP", "Keep your shield list short. 3 apps beat 15."),
    Tip("TIP", "If it feels uncomfortable, it's working."),
    Tip("TIP", "Charge your phone across the room. Distance is a shield too."),
    Tip("TIP", "Name your focus mode after the thing you're actually doing."),
    Tip("TIP", "Do the hardest task first, while the noise is still muted."),
    Tip("TIP", "A single 'do not disturb' session beats ten scattered ones."),
    Tip("TRICK", "You don't lose the internet. You reclaim your attention."),
    Tip("TRICK", "The shield drops packets locally — 0 bytes ever leave this phone."),
    Tip("TRICK", "Other apps keep working normally. Only the blocked ones go quiet."),
    Tip("TRICK", "Flip it off the instant the session ends. No guilt, no lag."),
    Tip("TRICK", "Use the timer in the notification to pace your breaks."),
    Tip("TRICK", "Block the app, not the tab. The whole app goes dark."),
    Tip("TRICK", "Your stats are the proof — watch the focused minutes add up."),
    Tip("TRICK", "A 25-minute block is a full deep-work sprint. That's enough."),
    Tip("TRICK", "The 'End session' button in the notification is your exit."),
    Tip("TRICK", "Re-open the app anytime — your list is saved on this phone."),
    Tip("MOTIVATE", "25 minutes of deep work beats 3 hours of distracted scrolling."),
    Tip("MOTIVATE", "You are not behind. You are choosing where to go next."),
    Tip("MOTIVATE", "Attention is the rarest currency. Spend it on purpose."),
    Tip("MOTIVATE", "The feed will still be there. Your focus won't wait."),
    Tip("MOTIVATE", "Small silences, repeated, become a calmer mind."),
    Tip("MOTIVATE", "You don't need more time. You need less interruption."),
    Tip("MOTIVATE", "Every session you finish is a vote for the person you're becoming."),
    Tip("MOTIVATE", "Quiet isn't empty. It's where the good work happens."),
    Tip("MOTIVATE", "Protect the next hour like it matters — because it does."),
    Tip("MOTIVATE", "You've got this. One focused session at a time.")
)

private data class Tip(val tag: String, val text: String)

/**
 * First-launch onboarding — premium security + serene control.
 *
 *  - Breathing hero: 3D matte toggle icon, 4s looping icy-blue LED pulse
 *    (mirrors resting heart rate → subconsciously calming).
 *  - Value prop: big, stark, absolute statement. No feature body text.
 *  - "Zero Data" anchor: "100% locally · 0 bytes" right before permissions
 *    (Halo Effect → frames the VPN warning as a formality).
 *  - Tactile checklist: two neumorphic pill rows. Unchecked = recessed;
 *    granted = extruded + neon cyan border + check (Foot-in-the-Door).
 *  - Gatekeeper: flat ghost button → elevates + electric blue when complete.
 */
@Composable
fun OnboardingScreen(
    onEnter: () -> Unit,
    onRequestAddQsTile: ((Boolean) -> Unit) -> Unit = {}
) {
    val context = LocalContext.current

    // ---- Quick Settings tile-pinning state (Android 13+ only) ----
    var tileAdded by remember { mutableStateOf(false) }
    var tileRequesting by remember { mutableStateOf(false) }

    // ---- Permission state (real-time) ----
    // (pre-Android 13: notifications are always allowed → true)
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
    // Battery-optimization exemption (keeps the shield alive overnight).
    // OPTIONAL — never blocks the gatekeeper, but strongly recommended.
    var batteryExempt by remember {
        mutableStateOf(SystemHealth.isIgnoringBatteryOptimizations(context))
    }

    // ---- Launchers (sequential — one at a time) ----
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

    val allGranted = hasNotificationPerm && hasVpnPerm

    // ---- Real-time permission observation ----
    // When the user returns from a system settings/consent screen (ON_RESUME),
    // re-check BOTH permissions so the UI instantly reflects the new state and
    // the SwipeToArmSlider unlocks without a manual refresh.
    //
    //  - Notifications: checkSelfPermission (no side effects).
    //  - VPN: VpnService.prepare(context) == null. prepare() is a non-launching
    //    check when already-authorized (returns null); it only returns an Intent
    //    (the consent dialog) when NOT yet authorized — and we don't launch it
    //    here, we just read the null/non-null result.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Re-check notifications (Android 13+ only; pre-33 always granted).
                if (Build.VERSION.SDK_INT >= 33) {
                    val granted = context.checkSelfPermission(
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted != hasNotificationPerm) {
                        hasNotificationPerm = granted
                        if (granted) Haptics.engage(context)
                    }
                }
                // Re-check VPN readiness.
                val vpnReady = VpnService.prepare(context) == null
                if (vpnReady != hasVpnPerm) {
                    hasVpnPerm = vpnReady
                    if (vpnReady) Haptics.engage(context)
                }
                // Re-check battery-optimization exemption (user may have just
                // flipped it in the system settings screen).
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

    // Version + year (for the footer).
    val versionName = remember {
        try {
            context.packageManager
                .getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "1.0"
        }
    }
    val year = remember {
        java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        // ---- Ambient background: soft radial glows (premium depth) ----
        AmbientGlow()

        // ---- Scrollable content ----
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // ---- Breathing hero (3D matte toggle + icy LED) ----
            BreathingHero()

            Spacer(Modifier.height(30.dp))

            // ---- Value proposition (big, stark, absolute) ----
            Text(
                text = "Silence the noise.\nKeep the connection.",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = StarkWhite,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp
            )

            Spacer(Modifier.height(14.dp))

            // ---- Zero Data guarantee (the Halo anchor) — as a pill badge ----
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(IcyBlue.copy(alpha = 0.10f))
                    .border(
                        width = 1.dp,
                        color = IcyBlue.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(50.dp)
                    )
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔒",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "100% local · 0 bytes leave this phone",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = IcyBlue,
                    letterSpacing = 0.3.sp
                )
            }

            Spacer(Modifier.height(30.dp))

            // ---- Rotating tip / trick / motivation (auto + tap to swap) ----
            TipCard()

            Spacer(Modifier.height(30.dp))

            // ---- Section label (elite terminology) ----
            SectionLabel("System Protocols")

            Spacer(Modifier.height(14.dp))

            // ---- System Protocols (3 dimmed informational cards) ----
            // Dimmed on purpose: these are informational, NOT the interactive
            // setup requirements below. The permission rows glow to draw the eye.
            FeatureCard(
                icon = "◉",
                title = "Targeted Interception",
                desc = "Choose any apps. They go dark — everything else stays connected.",
                dimmed = true
            )
            Spacer(Modifier.height(8.dp))
            FeatureCard(
                icon = "⚡",
                title = "Tactical Deployment",
                desc = "Flip the switch. The shield engages instantly, right on this phone.",
                dimmed = true
            )
            Spacer(Modifier.height(8.dp))
            FeatureCard(
                icon = "✦",
                title = "Zero-Leak Architecture",
                desc = "No servers, no accounts, no tracking. It all stays on your device.",
                dimmed = true
            )

            // ---- Permission checklist (ONLY show what's still needed) ----
            val needsNotif = !hasNotificationPerm
            val needsVpn = !hasVpnPerm

            if (needsNotif || needsVpn) {
                Spacer(Modifier.height(30.dp))
                SectionLabel("One-time setup")
                Spacer(Modifier.height(14.dp))
            }

            if (needsNotif) {
                NeumorphicChecklistItem(
                    title = "Notifications",
                    description = "Shows your focus timer and keeps the shield running.",
                    isChecked = hasNotificationPerm,
                    onClick = {
                        if (!hasNotificationPerm) {
                            Haptics.tick(context)
                            if (Build.VERSION.SDK_INT >= 33) {
                                notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                hasNotificationPerm = true
                            }
                        }
                    }
                )
                if (needsVpn) Spacer(Modifier.height(8.dp))
            }

            if (needsVpn) {
                NeumorphicChecklistItem(
                    title = "Local Shield",
                    description = "Safely drops network for blocked apps. Nothing else.",
                    isChecked = hasVpnPerm,
                    onClick = {
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
            }

            Spacer(Modifier.height(34.dp))

            // ---- Recommended: keep the shield alive (battery optimization) ----
            // Routed through the system settings screen (Play-policy safe —
            // no direct exemption request). Never blocks the gatekeeper.
            BatteryKeepAliveSection(
                exempt = batteryExempt,
                onClick = {
                    if (batteryExempt) return@BatteryKeepAliveSection
                    Haptics.tick(context)
                    batteryLauncher.launch(SystemHealth.batterySettingsIntent(context))
                }
            )

            Spacer(Modifier.height(28.dp))

            // ---- Optional: pin the GhostShield Quick Settings tile ----
            // Highly recommended (1-tap access) but never blocks onboarding.
            QsTilePinSection(
                tileAdded = tileAdded,
                tileRequesting = tileRequesting,
                onPinClick = {
                    if (tileAdded || tileRequesting) return@QsTilePinSection
                    Haptics.tick(context)
                    tileRequesting = true
                    onRequestAddQsTile { added ->
                        tileAdded = added
                        tileRequesting = false
                        if (added) Haptics.engage(context)
                    }
                }
            )

            Spacer(Modifier.height(28.dp))

            // ---- The Final Gatekeeper: SwipeToArmSlider ----
            // Replaces the old "Complete Setup" button. Locked until both
            // permissions are granted; then the user physically drags the thumb
            // to "arm" the shield (psychological commitment).
            SwipeToArmSlider(
                unlocked = allGranted,
                onArmed = {
                    Settings.get(context).markOnboarded()
                    onEnter()
                }
            )

            Spacer(Modifier.height(28.dp))

            // ---- Footer: quiet brand lockup (mark + wordmark · hairline · legal) ----
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Brand lockup: the null-ring mark + "codezmr"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Mini null-ring (echoes the app icon: circle + slash)
                    Canvas(modifier = Modifier.size(13.dp)) {
                        val ring = 1.5.dp.toPx()
                        drawCircle(
                            color = StarkWhite.copy(alpha = 0.30f),
                            radius = size.minDimension / 2f - ring / 2f,
                            style = Stroke(width = ring)
                        )
                        drawLine(
                            color = StarkWhite.copy(alpha = 0.30f),
                            start = Offset(size.width * 0.22f, size.height * 0.78f),
                            end = Offset(size.width * 0.78f, size.height * 0.22f),
                            strokeWidth = ring
                        )
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "codezmr",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = StarkWhite.copy(alpha = 0.38f),
                        letterSpacing = 2.2.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                // Hairline divider
                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .height(1.dp)
                        .background(StarkWhite.copy(alpha = 0.10f))
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "v$versionName  ·  © $year Codezmr  ·  All rights reserved",
                    style = MaterialTheme.typography.labelSmall,
                    color = StarkWhite.copy(alpha = 0.22f),
                    letterSpacing = 0.4.sp
                )
            }

            Spacer(Modifier.height(36.dp))
        }
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

// ---------------------------------------------------------------------------
// Section label — small uppercase eyebrow text
// ---------------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = IcyBlue.copy(alpha = 0.8f),
        letterSpacing = 2.0.sp,
        modifier = Modifier.padding(start = 4.dp)
    )
}

// ---------------------------------------------------------------------------
// Feature card — a rich neumorphic card (icon chip + title + description)
// ---------------------------------------------------------------------------

@Composable
private fun FeatureCard(
    icon: String,
    title: String,
    desc: String,
    /**
     * When true, the card is visually dimmed (lower alpha, muted icon) so it
     * reads as informational — NOT the interactive setup requirements. The
     * permission rows below glow cyan to carry the visual weight instead.
     */
    dimmed: Boolean = false
) {
    // Dim the whole card when informational.
    val cardAlpha = if (dimmed) 0.55f else 1f
    val iconColor = if (dimmed) IcyBlue.copy(alpha = 0.4f) else IcyBlue
    val titleColor = if (dimmed) StarkWhite.copy(alpha = 0.6f) else StarkWhite.copy(alpha = 0.95f)
    val descColor = if (dimmed) StarkWhite.copy(alpha = 0.35f) else StarkWhite.copy(alpha = 0.55f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .clip(RoundedCornerShape(20.dp))
            .shadow(elevation = if (dimmed) 4.dp else 8.dp, shape = RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF14141A), Color(0xFF0E0E12))
                )
            )
            .border(
                width = 1.dp,
                color = StarkWhite.copy(alpha = 0.06f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon chip
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(IcyBlue.copy(alpha = if (dimmed) 0.06f else 0.12f))
                .border(
                    width = 1.dp,
                    color = IcyBlue.copy(alpha = if (dimmed) 0.12f else 0.25f),
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.titleMedium,
                color = iconColor
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = titleColor
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = descColor,
                lineHeight = 17.sp
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Breathing Hero — 3D matte toggle with a 4s icy-blue LED pulse
// ---------------------------------------------------------------------------

@Composable
private fun BreathingHero() {
    // 4-second breathing cycle (mirrors resting heart rate).
    // Driven by an Animatable ping-pong loop (infiniteTransition.animateFloat
    // is not available in Compose 1.6.1).
    val breathe = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            breathe.animateTo(
                targetValue = 1f,
                animationSpec = tween(4000, easing = FastOutSlowInEasing)
            )
            breathe.animateTo(
                targetValue = 0f,
                animationSpec = tween(4000, easing = FastOutSlowInEasing)
            )
        }
    }
    val b = breathe.value
    // LED glow: 0.25 (rest) → 1.0 (peak)
    val ledAlpha = 0.25f + b * 0.75f
    val ledScale = 0.92f + b * 0.12f
    // Subtle whole-icon lift
    val heroScale = 0.98f + b * 0.03f

    Box(
        modifier = Modifier
            .size(150.dp)
            .scale(heroScale),
        contentAlignment = Alignment.Center
    ) {
        // Outer matte ring (3D bevel: light top-left, dark bottom-right)
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF1E1E24), Color(0xFF0C0C10))
                    )
                )
                .shadow(elevation = 14.dp, shape = CircleShape)
                .padding(10.dp)
        ) {
            // Inner matte face
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF26262E), Color(0xFF141418))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // The app icon (matte, slightly dimmed so the LED is the star)
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "NullFlow",
                    modifier = Modifier
                        .size(72.dp)
                        .scale(1f)
                )

                // Icy-blue LED (the breathing element) — top-right of the face
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 26.dp, end = 26.dp)
                        .size(18.dp)
                        .scale(ledScale)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    IcyBlue.copy(alpha = ledAlpha),
                                    IcyBlue.copy(alpha = ledAlpha * 0.4f)
                                )
                            )
                        )
                        .shadow(elevation = (4 + b * 8).dp, shape = CircleShape)
                ) {
                    // Bright LED core
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEAF9FF).copy(alpha = 0.5f + ledAlpha * 0.5f))
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tip card — auto-rotates (fade every 6s) AND manual (tap to advance).
// The tag cycles TIP → TRICK → MOTIVATE → TIP ...
// ---------------------------------------------------------------------------

private val TAG_ORDER = listOf("TIP", "TRICK", "MOTIVATE")

@Composable
private fun TipCard() {
    val scope = rememberCoroutineScope()

    // Current tag index + the tip shown for that tag.
    var tagIndex by remember { mutableStateOf(0) }
    var tip by remember { mutableStateOf(TIPS.first { it.tag == TAG_ORDER[0] }) }
    // Fade for the swap animation (driven imperatively).
    val alpha = remember { Animatable(1f) }

    // Advances to the next tag (cycling) and picks a fresh random tip for it.
    fun advance() {
        tagIndex = (tagIndex + 1) % TAG_ORDER.size
        val nextTag = TAG_ORDER[tagIndex]
        tip = TIPS.filter { it.tag == nextTag }.random()
    }

    // Fades out, swaps, fades back in.
    fun swap() {
        scope.launch {
            alpha.animateTo(0f, tween(180))
            advance()
            alpha.animateTo(1f, tween(260))
        }
    }

    // AUTO swap every 6 seconds.
    LaunchedEffect(Unit) {
        while (true) {
            delay(6000)
            swap()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(18.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF101014), Color(0xFF16161C))
                )
            )
            .border(
                width = 1.dp,
                color = IcyBlue.copy(alpha = 0.18f),
                shape = RoundedCornerShape(18.dp)
            )
            .alpha(alpha.value)
            .clickable { swap() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Small tag chip (rotates with the tip)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(IcyBlue.copy(alpha = 0.14f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = tip.tag,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = IcyBlue,
                letterSpacing = 1.2.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = tip.text,
            style = MaterialTheme.typography.bodyMedium,
            color = StarkWhite.copy(alpha = 0.82f),
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
        // Tiny "tap to change" affordance
        Spacer(Modifier.width(8.dp))
        Text(
            text = "↻",
            style = MaterialTheme.typography.bodyMedium,
            color = IcyBlue.copy(alpha = 0.5f)
        )
    }
}

// ---------------------------------------------------------------------------
// Neumorphic checklist item — recessed when unchecked, extruded + neon when granted
// ---------------------------------------------------------------------------

@Composable
private fun NeumorphicChecklistItem(
    title: String,
    description: String,
    isChecked: Boolean,
    onClick: () -> Unit
) {
    // Incomplete: subtle #222733 border. Complete: glowing cyan #00E5FF border.
    val borderColor by animateColorAsState(
        targetValue = if (isChecked) NeonCyan else Color(0xFF222733),
        animationSpec = tween(350),
        label = "border"
    )
    // Glow shadow behind the row when complete (the "glow" effect).
    val glowAlpha by animateFloatAsState(
        targetValue = if (isChecked) 0.55f else 0f,
        animationSpec = tween(350),
        label = "glow"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (isChecked) 1f else 0.3f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "checkScale"
    )
    // Text dims slightly on completion (indicates "done, move on").
    val titleAlpha by animateFloatAsState(
        targetValue = if (isChecked) 0.6f else 0.9f,
        animationSpec = tween(350),
        label = "titleAlpha"
    )
    val descAlpha by animateFloatAsState(
        targetValue = if (isChecked) 0.3f else 0.45f,
        animationSpec = tween(350),
        label = "descAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            // Glow: a soft cyan shadow that fades in when complete.
            .shadow(
                elevation = if (isChecked) 12.dp else 4.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = NeonCyan.copy(alpha = glowAlpha),
                spotColor = NeonCyan.copy(alpha = glowAlpha)
            )
            .background(
                brush = if (isChecked)
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1A2A30), Color(0xFF101820))
                    )
                else
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF101014), Color(0xFF18181E))
                    )
            )
            .border(
                width = if (isChecked) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(enabled = !isChecked, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Check / empty circle
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    color = if (isChecked) NeonCyan else SurfaceDark
                )
                .shadow(elevation = if (isChecked) 6.dp else 0.dp, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isChecked) {
                // Solid cyan circle with a checkmark.
                Canvas(
                    modifier = Modifier
                        .size(18.dp)
                        .scale(checkScale)
                ) {
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
                // Hollow circle (incomplete).
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            color = Color(0xFF222733),
                            shape = CircleShape
                        )
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = StarkWhite.copy(alpha = titleAlpha)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = StarkWhite.copy(alpha = descAlpha)
            )
        }

        if (!isChecked) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Tap",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = IcyBlue
            )
        }
    }
}

// ---------------------------------------------------------------------------
// SwipeToArmSlider — the Final Gatekeeper
// ---------------------------------------------------------------------------
//
// Replaces the old "Complete Setup" button. A tactile slider the user must
// physically drag to "arm" the shield after granting system permissions.
//
//  - LOCKED (permissions missing): dark grey track (#1A1D24), text
//    "[ SYSTEM LOCKED ]", thumb cannot be dragged.
//  - UNLOCKED (permissions granted): cyan gradient track, text
//    "> SWIPE TO ARM >". Dragging the thumb right fires continuous haptic
//    feedback (detent-based). At the 90% threshold: heavy VibrationEffect.
//    Composition, thumb locks in place, onArmed() is called.
//
// Slider math: drag offset clamped between 0f and (trackWidth - thumbWidth).

@Composable
private fun SwipeToArmSlider(
    unlocked: Boolean,
    onArmed: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    // Thumb travel (px). Updated on each layout pass.
    var maxTravel by remember { mutableStateOf(0f) }
    // Current thumb offset (px).
    var thumbOffset by remember { mutableStateOf(0f) }
    // Whether the arm has fired (locks the thumb + prevents re-trigger).
    var armed by remember { mutableStateOf(false) }
    // Last detent index fired (for detent-based haptics).
    var lastDetent by remember { mutableStateOf(-1) }

    // Reset when the lock state changes (e.g., permissions just granted).
    LaunchedEffect(unlocked) {
        if (unlocked) {
            thumbOffset = 0f
            armed = false
            lastDetent = -1
        }
    }

    val thumbSize = 44.dp
    val trackHeight = 56.dp
    val density = LocalDensity.current

    // Detent-based haptics: fire TextHandleMove every ~15% of travel.
    fun fireDetentHaptic(offset: Float, max: Float) {
        if (max <= 0f) return
        val fraction = offset / max
        val detent = (fraction / 0.15f).toInt()
        if (detent != lastDetent) {
            lastDetent = detent
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Heavy "arm" vibration.
    // NOTE: VibrationEffect.Composition is package-private (not accessible to
    // app code), so we use the public createWaveform() API instead — a
    // [off, on, off, on, off, on] pattern that produces a heavy "thud-tick-thud"
    // feel (the closest public equivalent to a Composition of THUD/TICK/THUD).
    fun fireArmVibration() {
        val vibrator = Haptics.vibratorFor(context) ?: return
        // Waveform: [initialDelay, on, off, on, off, on] in ms.
        val waveform = longArrayOf(0L, 80L, 40L, 120L, 40L, 60L)
        val amplitudes = intArrayOf(
            VibrationEffect.DEFAULT_AMPLITUDE,
            VibrationEffect.DEFAULT_AMPLITUDE,
            0,
            VibrationEffect.DEFAULT_AMPLITUDE,
            0,
            VibrationEffect.DEFAULT_AMPLITUDE
        )
        val effect = VibrationEffect.createWaveform(waveform, amplitudes, -1)
        vibrator.vibrate(effect)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeight)
            // Measure the track to compute maxTravel = trackWidth - thumbWidth - padding.
            .onGloballyPositioned { coords ->
                val trackWidthPx = coords.size.width.toFloat()
                val thumbWidthPx = with(density) { thumbSize.toPx() }
                val paddingPx = with(density) { 8.dp.toPx() } // 4.dp start + 4.dp end
                maxTravel = (trackWidthPx - thumbWidthPx - paddingPx).coerceAtLeast(0f)
            }
            .clip(RoundedCornerShape(16.dp))
            // Track background: dark grey when locked, cyan gradient when unlocked.
            .background(
                brush = if (unlocked)
                    Brush.horizontalGradient(
                        colors = listOf(
                            NeonCyan.copy(alpha = 0.12f),
                            NeonCyan.copy(alpha = 0.22f)
                        )
                    )
                else
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1A1D24), Color(0xFF1A1D24))
                    )
            )
            .border(
                width = 1.dp,
                color = if (unlocked) NeonCyan.copy(alpha = 0.5f) else Color(0xFF222733),
                shape = RoundedCornerShape(16.dp)
            )
            .shadow(
                elevation = if (unlocked) 8.dp else 3.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = if (unlocked) NeonCyan.copy(alpha = 0.3f) else Color.Transparent,
                spotColor = if (unlocked) NeonCyan.copy(alpha = 0.3f) else Color.Transparent
            ),
        contentAlignment = Alignment.Center
    ) {
        // Track label (centered, behind the thumb).
        Text(
            text = if (unlocked) "> SWIPE TO ARM >" else "[ SYSTEM LOCKED ]",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            color = if (unlocked) NeonCyan.copy(alpha = 0.8f) else StarkWhite.copy(alpha = 0.25f)
        )

        // The draggable thumb.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
                .size(thumbSize)
                .offset {
                    // Convert px offset to IntOffset.
                    androidx.compose.ui.unit.IntOffset(thumbOffset.roundToInt(), 0)
                }
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = if (unlocked)
                        Brush.linearGradient(
                            colors = listOf(NeonCyan, Color(0xFF00B8D4))
                        )
                    else
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF2A2F3A), Color(0xFF1E222B))
                        )
                )
                .shadow(
                    elevation = if (unlocked) 10.dp else 4.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = if (unlocked) NeonCyan.copy(alpha = 0.4f) else Color.Transparent,
                    spotColor = if (unlocked) NeonCyan.copy(alpha = 0.4f) else Color.Transparent
                )
                .pointerInput(unlocked, armed) {
                    if (unlocked && !armed) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                // New offset, clamped to [0, maxTravel].
                                val newOffset = (thumbOffset + dragAmount)
                                    .coerceIn(0f, maxTravel)
                                thumbOffset = newOffset
                                // Detent-based haptic feedback.
                                fireDetentHaptic(newOffset, maxTravel)
                                // 90% threshold → arm.
                                if (maxTravel > 0f && newOffset >= maxTravel * 0.9f && !armed) {
                                    armed = true
                                    // Snap the thumb to the end.
                                    thumbOffset = maxTravel
                                    fireArmVibration()
                                    onArmed()
                                }
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Thumb icon: a right-pointing chevron (unlocked) or a lock (locked).
            Text(
                text = if (unlocked) "›" else "🔒",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (unlocked) Color(0xFF0A0C10) else StarkWhite.copy(alpha = 0.4f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Battery keep-alive section (recommended, non-blocking)
// ---------------------------------------------------------------------------

/**
 * "Keep the Shield Alive" — routes the user to the system battery-optimization
 * settings so the OS doesn't kill the VPN overnight.
 *
 *  - Not exempt: cyan outlined button, tap → system settings screen.
 *  - Exempt: dimmed "Protected" state (re-checks live on resume).
 *
 * This section is RECOMMENDED — it never blocks the user from entering the app.
 */
@Composable
private fun BatteryKeepAliveSection(
    exempt: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (exempt)
            NeonCyan.copy(alpha = 0.10f)
        else
            SurfaceDark,
        animationSpec = tween(350),
        label = "batteryBg"
    )
    val border by animateColorAsState(
        targetValue = if (exempt)
            NeonCyan.copy(alpha = 0.4f)
        else
            NeonCyan,
        animationSpec = tween(350),
        label = "batteryBorder"
    )
    val text by animateColorAsState(
        targetValue = if (exempt)
            NeonCyan.copy(alpha = 0.5f)
        else
            NeonCyan,
        animationSpec = tween(350),
        label = "batteryText"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Recommended — Keep the Shield Alive",
            color = StarkWhite.copy(alpha = 0.55f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, border, RoundedCornerShape(16.dp))
                .background(bg)
                .clickable(enabled = !exempt, onClick = onClick)
                .height(56.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (exempt) "✓" else "🔋",
                    fontSize = 16.sp,
                    color = text
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (exempt)
                        "Shield protected from system sleep"
                    else
                        "Allow NullFlow to ignore battery limits",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = text
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Optional Quick Settings tile-pinning section (above the gatekeeper button)
// ---------------------------------------------------------------------------

/**
 * "Highly Recommended for Seamless Use" — offers a 1-tap pin of the
 * GhostShield Quick Settings tile.
 *
 *  - Android 13+ (TIRAMISU): an electric-cyan outlined button that calls the
 *    native `requestAddTileService` API. On success it flips to a dimmed
 *    "Added" state.
 *  - Android 10-12 (fallback): a muted glassmorphic card with manual
 *    drag-and-drop instructions.
 *
 * This section is OPTIONAL — it never blocks the user from entering the app.
 */
@Composable
private fun QsTilePinSection(
    tileAdded: Boolean,
    tileRequesting: Boolean,
    onPinClick: () -> Unit
) {
    val isTiramisu = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    Column(modifier = Modifier.fillMaxWidth()) {
        // Section header
        Text(
            text = "Highly Recommended for Seamless Use",
            color = StarkWhite.copy(alpha = 0.55f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        if (isTiramisu) {
            // ---- API 33+: 1-tap native pin button ----
            val bg by animateColorAsState(
                targetValue = if (tileAdded)
                    NeonCyan.copy(alpha = 0.10f)
                else
                    SurfaceDark,
                animationSpec = tween(350),
                label = "pinBg"
            )
            val border by animateColorAsState(
                targetValue = if (tileAdded)
                    NeonCyan.copy(alpha = 0.4f)
                else
                    NeonCyan,
                animationSpec = tween(350),
                label = "pinBorder"
            )
            val text by animateColorAsState(
                targetValue = if (tileAdded)
                    NeonCyan.copy(alpha = 0.5f)
                else
                    NeonCyan,
                animationSpec = tween(350),
                label = "pinText"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, border, RoundedCornerShape(16.dp))
                    .background(bg)
                    .clickable(enabled = !tileAdded && !tileRequesting, onClick = onPinClick)
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (tileAdded) "✓" else "⚡",
                        fontSize = 16.sp,
                        color = text
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            tileAdded -> "Added to Quick Settings"
                            tileRequesting -> "Adding…"
                            else -> "Pin to Quick Settings"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = text
                    )
                }
            }
        } else {
            // ---- API 30-32: manual instructions (glassmorphic card) ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(StarkWhite.copy(alpha = 0.06f))
                    .border(1.dp, StarkWhite.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Pro Tip: Swipe down your notification shade, tap Edit, and drag " +
                        "GhostShield to your active tiles for 1-tap zero-friction access.",
                    color = StarkWhite.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
