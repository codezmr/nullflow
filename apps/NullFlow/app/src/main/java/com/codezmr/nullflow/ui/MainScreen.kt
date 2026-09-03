package com.codezmr.nullflow.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateColorAsState
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codezmr.nullflow.data.FocusDao
import com.codezmr.nullflow.data.FocusProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The whole app in one screen (Big Tech approach):
 *  - Hero toggle (massive, animated, haptic)
 *  - Quantified-relief stats
 *  - Profile name + "edit apps" entry point
 *  - Bottom sheets: pre-prompt consent + app picker
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    // ---- Sheet state ----
    var showPrePrompt by remember { mutableStateOf(false) }
    var pendingProfileId by remember { mutableStateOf<Long?>(null) }

    // ---- VPN consent launcher (the one system dialog we can't hide) ----
    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Consent granted → actually start the shield.
            val pid = pendingProfileId
            if (pid != null) {
                startShield(context, dao, pid)
            }
        }
        // On cancel: do nothing — toggle stays off, user can retry.
    }

    // ---- Hero toggle action ----
    fun onToggle() {
        Haptics.tick(context)
        val current = activeProfile
        if (isActive) {
            // Turn OFF.
            stopShield(context)
            endCurrentSession(dao, scope)
            Haptics.disengage(context)
        } else {
            // Turn ON.
            val profileId = current?.id ?: createDefaultProfile(dao)
            // Pre-prompt first (the "Local Privacy Shield" framing), then consent.
            pendingProfileId = profileId
            showPrePrompt = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
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

        // ---- Pre-prompt consent sheet ----
        if (showPrePrompt) {
            PrePromptSheet(
                onAllow = {
                    showPrePrompt = false
                    val intent = VpnService.prepare(context)
                    if (intent != null) {
                        vpnLauncher.launch(intent)
                    } else {
                        // Already authorized → start immediately.
                        val pid = pendingProfileId
                        if (pid != null) startShield(context, dao, pid)
                    }
                },
                onDismiss = { showPrePrompt = false }
            )
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
                radius = if (isActive) 60.dp else 24.dp,
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
// Pre-Prompt consent sheet (the "Local Privacy Shield" framing)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrePromptSheet(onAllow: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Local Privacy Shield",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "To silence your apps, Android requires us to create a " +
                    "Local Shield. No data ever leaves your phone — it just " +
                    "stops the blocked apps from reaching the internet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            androidx.compose.material3.Button(
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("Allow", fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        }
    }
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
        dao.clearActive()
        dao.setActive(profileId, true)
        dao.insertSession(
            com.codezmr.nullflow.data.FocusSession(
                profileId = profileId,
                startTime = System.currentTimeMillis()
            )
        )
        val intent = Intent(context, com.codezmr.nullflow.vpn.FocusVpnService::class.java)
            .setAction(com.codezmr.nullflow.vpn.FocusVpnService.ACTION_START)
            .putExtra(com.codezmr.nullflow.vpn.FocusVpnService.EXTRA_PROFILE_ID, profileId)
        context.startForegroundService(intent)
    }
}

private fun stopShield(context: android.content.Context) {
    val intent = Intent(context, com.codezmr.nullflow.vpn.FocusVpnService::class.java)
        .setAction(com.codezmr.nullflow.vpn.FocusVpnService.ACTION_STOP)
    context.startService(intent)
}

private fun endCurrentSession(dao: FocusDao, scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        val running = dao.getRunningSession()
        if (running != null) {
            dao.endSession(running.id, System.currentTimeMillis())
            // Deactivate the profile so the next toggle starts fresh.
            dao.setActive(running.profileId, false)
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
