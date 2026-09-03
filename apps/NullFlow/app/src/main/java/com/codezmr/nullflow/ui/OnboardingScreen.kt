package com.codezmr.nullflow.ui

import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codezmr.nullflow.R
import com.codezmr.nullflow.data.Settings

// ---- Neumorphic palette (pure dark + icy blue LED) ----
private val PureBlack = Color(0xFF0A0A0C)
private val SurfaceDark = Color(0xFF141418)
private val IcyBlue = Color(0xFF4FC3F7)
private val ElectricBlue = Color(0xFF2979FF)
private val NeonCyan = Color(0xFF00E5FF)
private val MutedGrey = Color(0xFF3A3A42)
private val StarkWhite = Color(0xFFF2F4F8)

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
    var hasNotificationPerm by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 33) {
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
            } else {
                true // pre-Android 13: notifications always allowed
            }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))

            // ---- Breathing hero (3D matte toggle + icy LED) ----
            BreathingHero()

            Spacer(Modifier.height(36.dp))

            // ---- Value proposition (big, stark, absolute) ----
            Text(
                text = "Silence the noise.\nKeep the connection.",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = StarkWhite,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp
            )

            Spacer(Modifier.height(28.dp))

            // ---- Zero Data guarantee (the Halo anchor) ----
            Text(
                text = "Your privacy shield runs 100% locally.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = StarkWhite.copy(alpha = 0.92f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "0 bytes of data ever leave this phone.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = IcyBlue,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(44.dp))

            // ---- Tactile permission checklist ----
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
            Spacer(Modifier.height(16.dp))

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

            Spacer(Modifier.weight(1f))

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

            // ---- Footer (quiet, professional — not highlighted) ----
            val versionName = remember {
                try {
                    context.packageManager
                        .getPackageInfo(context.packageName, 0).versionName
                } catch (_: Exception) {
                    "1.0"
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Codezmr",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = StarkWhite.copy(alpha = 0.35f)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "v$versionName · © ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)} Codezmr. All rights reserved.",
                    style = MaterialTheme.typography.labelSmall,
                    color = StarkWhite.copy(alpha = 0.22f)
                )
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Breathing Hero — 3D matte toggle with a 4s icy-blue LED pulse
// ---------------------------------------------------------------------------

@Composable
private fun BreathingHero() {
    // 4-second breathing cycle (mirrors resting heart rate).
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )
    // LED glow: 0.25 (rest) → 1.0 (peak)
    val ledAlpha = 0.25f + breathe * 0.75f
    val ledScale = 0.92f + breathe * 0.12f
    // Subtle whole-icon lift
    val heroScale = 0.98f + breathe * 0.03f

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
                .shadow(
                    radius = 30.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.6f),
                    spotColor = Color.Black.copy(alpha = 0.6f)
                )
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
                        .shadow(
                            radius = (8 + breathe * 14).dp,
                            shape = CircleShape,
                            ambientColor = IcyBlue.copy(alpha = ledAlpha * 0.8f),
                            spotColor = IcyBlue.copy(alpha = ledAlpha * 0.8f)
                        )
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
    // Recessed (unchecked) vs extruded (checked): swap the shadow direction.
    val shadowRadius = if (isChecked) 18.dp else 12.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .shadow(
                radius = shadowRadius,
                shape = RoundedCornerShape(22.dp),
                // Extruded: light from top-left. Recessed: dark top-left, light bottom-right.
                ambientColor = if (isChecked)
                    Color.Black.copy(alpha = 0.5f)
                else
                    Color(0xFF000000).copy(alpha = 0.7f),
                spotColor = if (isChecked)
                    Color(0xFF2A2A32).copy(alpha = 0.5f)
                else
                    Color(0xFF000000).copy(alpha = 0.8f)
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
                .shadow(
                    radius = if (isChecked) 10.dp else 0.dp,
                    shape = CircleShape,
                    ambientColor = NeonCyan.copy(alpha = glowAlpha),
                    spotColor = NeonCyan.copy(alpha = glowAlpha)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isChecked) {
                Canvas(
                    modifier = Modifier
                        .size(18.dp)
                        .scale(checkScale)
                ) {
                    val stroke = strokeWidth / 2.2f
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
    val infiniteTransition = rememberInfiniteTransition(label = "gatePulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gatePulse"
    )

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
    val elevation = if (allGranted) 16.dp else 4.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (allGranted) pulse else 1f)
            .clip(RoundedCornerShape(20.dp))
            .shadow(
                radius = elevation,
                shape = RoundedCornerShape(20.dp),
                ambientColor = if (allGranted)
                    ElectricBlue.copy(alpha = 0.5f)
                else
                    Color.Black.copy(alpha = 0.5f),
                spotColor = if (allGranted)
                    ElectricBlue.copy(alpha = 0.5f)
                else
                    Color.Black.copy(alpha = 0.5f)
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
