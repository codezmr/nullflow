package com.codezmr.nullflow.ui

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codezmr.nullflow.AppLog
import com.codezmr.nullflow.data.FocusDao
import com.codezmr.nullflow.data.FocusProfile
import kotlinx.coroutines.delay
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
            val profileId = current?.id ?: createDefaultProfile(dao)
            AppLog.d("TOGGLE → turning ON for profileId=$profileId")
            startShield(context, dao, profileId)
            Haptics.engage(context)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // Wordmark
            Text(
                text = "NullFlow",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Disconnect on your terms.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
            )

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
            if (activeProfile != null) {
                Spacer(Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        onOpenPicker(activeProfile!!.id)
                    }
                ) {
                    Text(
                        text = activeProfile!!.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Edit apps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "No focus mode yet — tap the switch to begin",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
            }

            Spacer(Modifier.weight(1f))

            // ---- Quantified Relief stats ----
            StatsRow(
                sessionMs = if (runningSession != null)
                    (now - runningSession!!.startTime) else 0L,
                totalMs = totalMs,
                completedCount = completedCount
            )

            Spacer(Modifier.height(40.dp))
        }
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
            .shadow(
                elevation = if (isActive) 24.dp else 10.dp,
                shape = CircleShape,
                clip = false
            )
            .background(
                color = if (isActive) Color(0xFF4F8CFF).copy(alpha = glowAlpha) else Color.Transparent,
                shape = CircleShape
            )
            .padding(14.dp)
            .background(
                color = if (isActive) Color(0xFF101826) else Color(0xFF1A1D24),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
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
