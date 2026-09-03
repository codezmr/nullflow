package com.codezmr.nullflow.ui

import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codezmr.nullflow.R
import com.codezmr.nullflow.data.Settings
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
fun OnboardingScreen(onEnter: () -> Unit) {
    val context = LocalContext.current

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

    val allGranted = hasNotificationPerm && hasVpnPerm

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

            // ---- Section label ----
            SectionLabel("How it works")

            Spacer(Modifier.height(14.dp))

            // ---- How it works (3 rich feature cards) ----
            FeatureCard(
                icon = "◉",
                title = "Pick the apps to silence",
                desc = "Choose any apps. They go dark — everything else stays connected."
            )
            Spacer(Modifier.height(12.dp))
            FeatureCard(
                icon = "⚡",
                title = "One tap, zero popups",
                desc = "Flip the switch. The shield engages instantly, right on this phone."
            )
            Spacer(Modifier.height(12.dp))
            FeatureCard(
                icon = "✦",
                title = "Your data never moves",
                desc = "No servers, no accounts, no tracking. It all stays on your device."
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
                if (needsVpn) Spacer(Modifier.height(14.dp))
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

            // ---- Gatekeeper button ----
            GatekeeperButton(
                allGranted = allGranted,
                onClick = {
                    Haptics.engage(context)
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
private fun FeatureCard(icon: String, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp))
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
                .background(IcyBlue.copy(alpha = 0.12f))
                .border(
                    width = 1.dp,
                    color = IcyBlue.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.titleMedium,
                color = IcyBlue
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = StarkWhite.copy(alpha = 0.95f)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = StarkWhite.copy(alpha = 0.55f),
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
    val borderColor by animateColorAsState(
        targetValue = if (isChecked) NeonCyan else MutedGrey,
        animationSpec = tween(350),
        label = "border"
    )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .shadow(
                elevation = if (isChecked) 10.dp else 5.dp,
                shape = RoundedCornerShape(22.dp)
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
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            color = MutedGrey,
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
                color = if (isChecked) StarkWhite else StarkWhite.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = StarkWhite.copy(alpha = 0.45f)
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
// Gatekeeper button — flat ghost → elevated electric blue
// ---------------------------------------------------------------------------

@Composable
private fun GatekeeperButton(allGranted: Boolean, onClick: () -> Unit) {
    // Gentle pulse when active (Animatable ping-pong; infiniteTransition
    // .animateFloat is not available in Compose 1.6.1).
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(allGranted) {
        if (allGranted) {
            while (true) {
                pulse.animateTo(
                    targetValue = 1.025f,
                    animationSpec = tween(1100, easing = FastOutSlowInEasing)
                )
                pulse.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(1100, easing = FastOutSlowInEasing)
                )
            }
        } else {
            pulse.snapTo(1f)
        }
    }

    val containerColor by animateColorAsState(
        targetValue = if (allGranted) ElectricBlue else SurfaceDark,
        animationSpec = tween(450),
        label = "gateBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (allGranted) Color.White else StarkWhite.copy(alpha = 0.3f),
        animationSpec = tween(450),
        label = "gateText"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (allGranted) pulse.value else 1f)
            .clip(RoundedCornerShape(20.dp))
            .shadow(
                elevation = if (allGranted) 12.dp else 4.dp,
                shape = RoundedCornerShape(20.dp)
            )
            .background(containerColor)
            .clickable(enabled = allGranted, onClick = onClick)
            .height(60.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (allGranted) "Enter NullFlow" else "Complete Setup",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}
