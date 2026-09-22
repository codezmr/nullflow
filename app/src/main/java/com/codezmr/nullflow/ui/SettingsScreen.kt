package com.codezmr.nullflow.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codezmr.nullflow.AppLog
import com.codezmr.nullflow.data.FocusDao
import com.codezmr.nullflow.data.Settings
import com.codezmr.nullflow.data.SystemHealth
import kotlinx.coroutines.launch

private val SfgBg = Color(0xFF0A0C10)
private val SfgCard = Color(0xFF12151C)
private val SfgBorder = Color(0xFF222733)
private val SfgAccent = Color(0xFF00E5FF)
private val SfgMuted = Color(0xFF808080)
private val SfgText = Color(0xFFE6EAF0)
private val SfgVoid = Color(0xFF050505)
private val SfgGreen = Color(0xFF3DDC84)
private val SfgGreenDark = Color(0xFF207A48)

@Composable
fun SettingsScreen(
    dao: FocusDao,
    onBack: () -> Unit,
    onOpenSchedules: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { Settings.get(context) }

    // Force recomposition when settings change
    var refreshTick by remember { mutableIntStateOf(0) }
    fun refresh() { refreshTick++ }

    // Developer mode: tap version 7 times
    var versionTaps by remember { mutableIntStateOf(0) }
    var showDev by remember { mutableStateOf(false) }
    var lastTapTime by remember { mutableStateOf(0L) }

    // Clear confirmation
    var showClearConfirm by remember { mutableStateOf(false) }
    var clearTarget by remember { mutableStateOf("") }

    // System Health: live battery-optimization status. Re-checked on every
    // resume (the user may have just flipped it in the system settings screen).
    var batteryExempt by remember {
        mutableStateOf(SystemHealth.isIgnoringBatteryOptimizations(context))
    }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                batteryExempt = SystemHealth.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val version = remember {
        try {
            val pm = context.packageManager
            val info = pm.getPackageInfo(context.packageName, 0)
            "v${info.versionName} (${info.versionCode})"
        } catch (_: Exception) { "vUnknown" }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SfgBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            // ---- Header ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(SfgCard)
                        .border(1.dp, SfgBorder, CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Text("←", fontSize = 16.sp, color = SfgMuted)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "SETTINGS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = SfgMuted
                )
            }

            // ---- Under-development banner ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2A2410))
                    .border(1.dp, Color(0xFFB8860B), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "Under Development",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFD54F)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Settings is still being finalized. Some options may not work as expected on your device. We're working on it and a stable release is coming soon.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 16.sp
                )
            }

            // ---- SHIELD section ----
            SectionHeader("SHIELD")
            SettingCard {
                // Default mode
                val profiles by dao.observeProfiles().collectAsState(initial = emptyList())
                SettingRow(
                    title = "Default mode",
                    subtitle = profiles.firstOrNull { it.id == settings.defaultProfileId }?.name ?: "None"
                ) {
                    // Simple: cycle through profiles on tap
                    val currentIdx = profiles.indexOfFirst { it.id == settings.defaultProfileId }
                    val nextIdx = if (profiles.isEmpty()) -1 else (currentIdx + 1) % profiles.size
                    settings.defaultProfileId = if (nextIdx >= 0) profiles[nextIdx].id else null
                    refresh()
                }

                SettingDivider()

                // Auto-start on boot
                SettingSwitchRow(
                    title = "Auto-start on boot",
                    subtitle = "Resume shield after device restart",
                    checked = settings.autoStartOnBoot,
                    onCheckedChange = {
                        settings.autoStartOnBoot = it
                        refresh()
                    }
                )

                SettingDivider()

                // Auto-stop timer
                SettingRow(
                    title = "Auto-stop after",
                    subtitle = if (settings.autoStopMinutes > 0) "${settings.autoStopMinutes} min" else "Off"
                ) {
                    val options = listOf(0, 15, 25, 30, 45, 60, 90, 120)
                    val currentIdx = options.indexOf(settings.autoStopMinutes).coerceAtLeast(0)
                    val nextIdx = (currentIdx + 1) % options.size
                    settings.autoStopMinutes = options[nextIdx]
                    refresh()
                }

                SettingDivider()

                // Recurring schedules
                SettingRow(
                    title = "Schedules",
                    subtitle = "Recurring focus windows (e.g. weekdays 6-9 PM)"
                ) {
                    onOpenSchedules()
                }
            }

            // ---- SYSTEM HEALTH section ----
            SectionHeader("SYSTEM HEALTH")
            SettingCard {
                SettingRow(
                    title = "Battery protection",
                    subtitle = if (batteryExempt)
                        "Shield is protected from system sleep"
                    else
                        "Tap to allow NullFlow to ignore battery limits"
                ) {
                    if (!batteryExempt) {
                        context.startActivity(SystemHealth.batterySettingsIntent(context))
                        // Status re-checks on ON_RESUME (lifecycle observer above).
                    }
                }
            }

            // ---- DATA section ----
            SectionHeader("DATA")
            SettingCard {
                SettingRow(
                    title = "Clear session history",
                    subtitle = "Remove all focus sessions",
                    danger = true
                ) {
                    clearTarget = "sessions"
                    showClearConfirm = true
                }
                SettingDivider()
                SettingRow(
                    title = "Clear intercept log",
                    subtitle = "Reset all block counters",
                    danger = true
                ) {
                    clearTarget = "intercepts"
                    showClearConfirm = true
                }
                SettingDivider()
                SettingRow(
                    title = "Export data",
                    subtitle = "Download sessions as JSON"
                ) {
                    scope.launch {
                        try {
                            val sessions = dao.getAllCompletedSessions()
                            val json = buildString {
                                append("[\n")
                                sessions.forEachIndexed { i, s ->
                                    append("  {\"profileId\":${s.profileId},\"start\":${s.startTime},\"end\":${s.endTime}}")
                                    if (i < sessions.lastIndex) append(",")
                                    append("\n")
                                }
                                append("]")
                            }
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                java.io.File(context.cacheDir, "nullflow_export.json")
                            )
                            java.io.File(context.cacheDir, "nullflow_export.json").writeText(json)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                                context.startActivity(Intent.createChooser(intent, "Export sessions"))
                            AppLog.d("Settings: export started (${sessions.size} sessions)")
                        } catch (e: Exception) {
                            AppLog.e("Settings: export FAILED", e)
                        }
                    }
                }
            }

            // ---- DIAGNOSTICS section ----
            SectionHeader("DIAGNOSTICS")
            SettingCard {
                SettingSwitchRow(
                    title = "File logging",
                    subtitle = if (settings.fileLoggingEnabled)
                        "Saving a shareable log file"
                    else
                        "Log file saving is off",
                    checked = settings.fileLoggingEnabled,
                    onCheckedChange = {
                        settings.fileLoggingEnabled = it
                        AppLog.setFileLogging(it)
                        refresh()
                    }
                )
                SettingDivider()
                SettingRow(
                    title = "Share log file",
                    subtitle = "Send nullflow.log for debugging"
                ) {
                    scope.launch {
                        try {
                            val file = AppLog.logFile()
                            if (file == null || !file.exists() || file.length() == 0L) {
                                AppLog.w("Settings: no log file to share (enable file logging first)")
                                return@launch
                            }
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share NullFlow log"))
                            AppLog.d("Settings: log share started (${file.length()} bytes)")
                        } catch (e: Exception) {
                            AppLog.e("Settings: log share FAILED", e)
                        }
                    }
                }
            }

            // ---- ABOUT section (redesigned) ----
            SectionHeader("ABOUT")
            AboutCard(
                version = version,
                onVersionTap = {
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < 500) {
                        versionTaps++
                        lastTapTime = now
                        if (versionTaps >= 7) {
                            showDev = true
                            versionTaps = 0
                        }
                    } else {
                        versionTaps = 1
                        lastTapTime = now
                    }
                },
                onOpenLanding = {
                    openUrl(context, "https://shutupchat.com/nullflow")
                },
                onOpenTelegram = {
                    openUrl(context, "https://t.me/nullflow_app")
                }
            )

            // ---- DEVELOPER section (hidden) ----
            if (showDev) {
                SectionHeader("DEVELOPER")
                SettingCard {
                    SettingSwitchRow(
                        title = "Verbose logging",
                        subtitle = "Log every packet decision",
                        checked = settings.verboseLogging,
                        onCheckedChange = {
                            settings.verboseLogging = it
                            refresh()
                        }
                    )
                    SettingDivider()
                    SettingRow(
                        title = "Force VPN reconnect",
                        subtitle = "Restart the tunnel"
                    ) {
                        context.startService(
                            Intent(context, com.codezmr.nullflow.vpn.FocusVpnService::class.java)
                                .setAction(com.codezmr.nullflow.vpn.FocusVpnService.ACTION_REFRESH_RULES)
                        )
                        AppLog.d("Settings: forced VPN refresh")
                    }
                    SettingDivider()
                    SettingRow(
                        title = "Reset all data",
                        subtitle = "Wipe everything (profiles, sessions, logs)",
                        danger = true
                    ) {
                        clearTarget = "all"
                        showClearConfirm = true
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }

        // ---- Clear confirmation overlay ----
        if (showClearConfirm) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { showClearConfirm = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SfgCard)
                        .border(1.dp, SfgBorder, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (clearTarget) {
                            "sessions" -> "Clear session history?"
                            "intercepts" -> "Clear intercept log?"
                            "all" -> "Reset ALL data?"
                            else -> "Confirm"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SfgText
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when (clearTarget) {
                            "all" -> "This cannot be undone. All profiles, sessions, and logs will be deleted."
                            else -> "This cannot be undone."
                        },
                        fontSize = 13.sp,
                        color = SfgMuted
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SfgCard)
                                .border(1.dp, SfgBorder, RoundedCornerShape(12.dp))
                                .clickable { showClearConfirm = false }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Cancel", fontSize = 14.sp, color = SfgMuted)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFF3B30))
                                .clickable {
                                    scope.launch {
                                        try {
                                            when (clearTarget) {
                                                "sessions" -> dao.clearAllSessions()
                                                "intercepts" -> dao.clearAllIntercepts()
                                                "all" -> {
                                                    dao.clearAllSessions()
                                                    dao.clearAllIntercepts()
                                                }
                                            }
                                            AppLog.d("Settings: cleared '$clearTarget'")
                                        } catch (e: Exception) {
                                            AppLog.e("Settings: clear FAILED", e)
                                        }
                                    }
                                    showClearConfirm = false
                                    refresh()
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Delete", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Settings UI components
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = SfgMuted,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SfgCard)
            .border(1.dp, SfgBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp)
    ) {
        content()
    }
}

@Composable
private fun SettingDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SfgBorder)
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (danger) Color(0xFFFF3B30) else SfgText
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = SfgMuted
            )
        }
        Text(
            text = "›",
            fontSize = 18.sp,
            color = SfgMuted
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = SfgText
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = SfgMuted
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF0A0C10),
                checkedTrackColor = SfgAccent,
                uncheckedThumbColor = Color(0xFF505050),
                uncheckedTrackColor = Color(0xFF1E1E1E)
            )
        )
    }
}

// ---------------------------------------------------------------------------
// About card (redesigned)
// ---------------------------------------------------------------------------

@Composable
private fun AboutCard(
    version: String,
    onVersionTap: () -> Unit,
    onOpenLanding: () -> Unit,
    onOpenTelegram: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "about")
    val ringRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D1410), SfgVoid)
                )
            )
            .border(1.dp, SfgGreen.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // Hero: rotating reactor ring + shield glyph
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(96.dp).rotate(ringRotation)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2 - 4.dp.toPx()
                drawCircle(
                    color = SfgGreen.copy(alpha = 0.3f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                repeat(24) { i ->
                    val angle = (i * 15f) * Math.PI / 180f
                    val tickRadius = radius - 6.dp.toPx()
                    drawLine(
                        color = SfgGreen.copy(alpha = 0.5f),
                        start = center + Offset(
                            (tickRadius * cos(angle)).toFloat(),
                            (tickRadius * sin(angle)).toFloat()
                        ),
                        end = center + Offset(
                            ((tickRadius - 4.dp.toPx()) * cos(angle)).toFloat(),
                            ((tickRadius - 4.dp.toPx()) * sin(angle)).toFloat()
                        ),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(SfgVoid, SfgGreenDark.copy(alpha = 0.6f))
                        )
                    )
                    .border(1.dp, SfgGreen.copy(alpha = pulse), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NF",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = SfgGreen
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "NullFlow",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = SfgText,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Absolute Silence",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            color = SfgGreen,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "A zero-trust focus shield for Android. Select the apps that distract you, flip the switch, and their network traffic is silently dropped into the void.",
            fontSize = 12.sp,
            color = SfgMuted,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        // Stats row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SfgCard)
                .border(1.dp, SfgBorder, RoundedCornerShape(12.dp))
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AboutStat("No Root")
            AboutStat("Local Only")
            AboutStat("MIT")
        }

        Spacer(Modifier.height(12.dp))

        // Action rows
        AboutActionRow(
            label = "Landing page",
            value = "shutupchat.com/nullflow",
            onClick = onOpenLanding
        )
        AboutActionRow(
            label = "Report bug or feedback",
            value = "t.me/nullflow_app",
            onClick = onOpenTelegram
        )
        AboutActionRow(
            label = "Privacy",
            value = "All data stays on your device",
            onClick = {}
        )
        AboutActionRow(
            label = "Version",
            value = version,
            onClick = onVersionTap
        )
    }
}

@Composable
private fun AboutStat(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = SfgGreen
        )
    }
}

@Composable
private fun AboutActionRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = SfgText
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = SfgMuted
            )
        }
        Text(
            text = "›",
            fontSize = 18.sp,
            color = SfgMuted
        )
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    } catch (e: Exception) {
        AppLog.e("Settings: failed to open $url", e)
    }
}

private fun String.capitalize(): String = replaceFirstChar { it.uppercase() }
