package com.codezmr.nullflow.ui

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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codezmr.nullflow.AppLog
import com.codezmr.nullflow.data.FocusDao
import com.codezmr.nullflow.data.FocusProfile
import com.codezmr.nullflow.data.FocusSchedule
import com.codezmr.nullflow.service.ScheduleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---- Palette (matches Settings / CreateMode surfaces) ----
private val SchedBg = Color(0xFF0A0C10)
private val SchedCard = Color(0xFF12151C)
private val SchedBorder = Color(0xFF222733)
private val SchedAccent = Color(0xFF00E5FF)
private val SchedMuted = Color(0xFF808080)
private val SchedText = Color(0xFFE6EAF0)

private val DAY_LABELS = listOf("S", "M", "T", "W", "T", "F", "S")

/**
 * Full-screen schedule manager. Lists every recurring focus window and lets the
 * user create, toggle, and delete them. Tapping a schedule opens the inline
 * editor (day chips + start/end time steppers).
 *
 * A schedule is tied to a profile: when its window fires, that profile's shield
 * turns on at [FocusSchedule.startMinute] and off at [FocusSchedule.endMinute]
 * on the selected days.
 */
@Composable
fun ScheduleEditorScreen(
    dao: FocusDao,
    onBack: () -> Unit
) {
    val profiles by dao.observeProfiles().collectAsState(initial = emptyList())
    val allSchedules by dao.observeAllSchedules().collectAsState(initial = emptyList())

    var editingId by remember { mutableStateOf<Long?>(null) }
    var showNew by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SchedBg)
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
                        .background(SchedCard)
                        .border(1.dp, SchedBorder, CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Text("←", fontSize = 16.sp, color = SchedMuted)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "SCHEDULES",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = SchedMuted
                )
            }

            if (profiles.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Create a mode first",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = SchedText
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Schedules attach to a mode. Go back and create one, then come here to set a recurring window.",
                        fontSize = 13.sp,
                        color = SchedMuted,
                        lineHeight = 18.sp
                    )
                }
            } else {
                // ---- Existing schedules ----
                if (allSchedules.isEmpty()) {
                    Text(
                        text = "No schedules yet. Tap + New to set a recurring focus window.",
                        fontSize = 13.sp,
                        color = SchedMuted,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }

                allSchedules.forEach { schedule ->
                    val profileName = profiles.firstOrNull { it.id == schedule.profileId }?.name
                        ?: "Unknown mode"
                    ScheduleCard(
                        schedule = schedule,
                        profileName = profileName,
                        onClick = {
                            editingId = schedule.id
                            showNew = false
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                }

                // ---- New schedule button ----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, SchedAccent, RoundedCornerShape(14.dp))
                        .clickable {
                            showNew = true
                            editingId = null
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ New Schedule",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SchedAccent
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }

        // ---- Editor overlay (new or existing) ----
        if (showNew || editingId != null) {
            val existing = editingId?.let { id -> allSchedules.firstOrNull { it.id == id } }
            ScheduleEditorOverlay(
                dao = dao,
                profiles = profiles,
                existing = existing,
                onDismiss = {
                    showNew = false
                    editingId = null
                },
                onSaved = {
                    showNew = false
                    editingId = null
                }
            )
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: FocusSchedule,
    profileName: String,
    onClick: () -> Unit
) {
    val daysText = buildString {
        for (d in 0..6) {
            if (FocusSchedule.maskIncludes(schedule.daysBitmask, d)) append(DAY_LABELS[d])
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SchedCard)
            .border(1.dp, SchedBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profileName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SchedText
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${daysText.ifEmpty { "Never" }}  ·  ${minuteToLabel(schedule.startMinute)} - ${minuteToLabel(schedule.endMinute)}",
                    fontSize = 12.sp,
                    color = SchedMuted
                )
            }
            Text(
                text = if (schedule.isEnabled) "ON" else "OFF",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (schedule.isEnabled) SchedAccent else SchedMuted
            )
        }
    }
}

@Composable
private fun ScheduleEditorOverlay(
    dao: FocusDao,
    profiles: List<FocusProfile>,
    existing: FocusSchedule?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var profileId by remember { mutableStateOf(existing?.profileId ?: profiles.firstOrNull()?.id ?: 0L) }
    var startMinute by remember { mutableIntStateOf(existing?.startMinute ?: 18 * 60) }
    var endMinute by remember { mutableIntStateOf(existing?.endMinute ?: 21 * 60) }
    var daysBitmask by remember { mutableIntStateOf(existing?.daysBitmask ?: FocusSchedule.WEEKDAYS) }
    var isEnabled by remember { mutableStateOf(existing?.isEnabled ?: true) }
    var isSaving by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(SchedCard)
                .border(1.dp, SchedBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .padding(24.dp)
                .clickable(enabled = false) { }
        ) {
            Text(
                text = if (existing == null) "New Schedule" else "Edit Schedule",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = SchedText
            )
            Spacer(Modifier.height(16.dp))

            // ---- Mode selector (tap to cycle) ----
            val profileName = profiles.firstOrNull { it.id == profileId }?.name ?: "Select mode"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SchedBg)
                    .border(1.dp, SchedBorder, RoundedCornerShape(10.dp))
                    .clickable {
                        val idx = profiles.indexOfFirst { it.id == profileId }
                        profileId = profiles[(idx + 1) % profiles.size].id
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mode", fontSize = 11.sp, color = SchedMuted)
                    Spacer(Modifier.height(2.dp))
                    Text(profileName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SchedText)
                }
                Text("›", fontSize = 18.sp, color = SchedMuted)
            }
            Spacer(Modifier.height(16.dp))

            // ---- Day chips ----
            Text("Days", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = SchedMuted)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (d in 0..6) {
                    val selected = FocusSchedule.maskIncludes(daysBitmask, d)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (selected) SchedAccent else SchedBg)
                            .border(
                                width = 1.dp,
                                color = if (selected) SchedAccent else SchedBorder,
                                shape = CircleShape
                            )
                            .clickable {
                                daysBitmask = if (selected) daysBitmask and (1 shl d).inv()
                                else daysBitmask or (1 shl d)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = DAY_LABELS[d],
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) Color(0xFF0A0C10) else SchedMuted
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ---- Start / End time steppers ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TimeStepper(
                    label = "Starts",
                    minute = startMinute,
                    onMinus = { startMinute = (startMinute - 15 + 1440) % 1440 },
                    onPlus = { startMinute = (startMinute + 15) % 1440 },
                    modifier = Modifier.weight(1f)
                )
                TimeStepper(
                    label = "Ends",
                    minute = endMinute,
                    onMinus = { endMinute = (endMinute - 15 + 1440) % 1440 },
                    onPlus = { endMinute = (endMinute + 15) % 1440 },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(16.dp))

            // ---- Enable toggle ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enabled", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SchedText)
                    Spacer(Modifier.height(2.dp))
                    Text("Off schedules are kept but never fire", fontSize = 12.sp, color = SchedMuted)
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { isEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF0A0C10),
                        checkedTrackColor = SchedAccent,
                        uncheckedThumbColor = Color(0xFF505050),
                        uncheckedTrackColor = Color(0xFF1E1E1E)
                    )
                )
            }
            Spacer(Modifier.height(20.dp))

            // ---- Save / Cancel ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SchedBg)
                        .border(1.dp, SchedBorder, RoundedCornerShape(12.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", fontSize = 14.sp, color = SchedMuted)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SchedAccent)
                        .clickable(enabled = !isSaving) {
                            if (daysBitmask == 0) return@clickable
                            isSaving = true
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    try {
                                        val id = if (existing != null) {
                                            dao.updateSchedule(
                                                id = existing.id,
                                                startMinute = startMinute,
                                                endMinute = endMinute,
                                                daysBitmask = daysBitmask,
                                                isEnabled = isEnabled
                                            )
                                            existing.id
                                        } else {
                                            dao.insertSchedule(
                                                FocusSchedule(
                                                    profileId = profileId,
                                                    startMinute = startMinute,
                                                    endMinute = endMinute,
                                                    daysBitmask = daysBitmask,
                                                    isEnabled = isEnabled
                                                )
                                            )
                                        }
                                        // Arm (or re-arm) the alarms for this schedule.
                                        if (isEnabled) ScheduleManager.armNext(id)
                                        else ScheduleManager.cancel(id)
                                        AppLog.d("ScheduleEditor: saved schedule id=$id")
                                    } catch (e: Exception) {
                                        AppLog.e("ScheduleEditor: save FAILED", e)
                                    }
                                }
                                isSaving = false
                                onSaved()
                            }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (existing == null) "Create" else "Save",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0A0C10)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeStepper(
    label: String,
    minute: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SchedBg)
            .border(1.dp, SchedBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 11.sp, color = SchedMuted)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SchedCard)
                    .border(1.dp, SchedBorder, CircleShape)
                    .clickable(onClick = onMinus),
                contentAlignment = Alignment.Center
            ) {
                Text("−", fontSize = 18.sp, color = SchedText)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = minuteToLabel(minute),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = SchedText
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SchedCard)
                    .border(1.dp, SchedBorder, CircleShape)
                    .clickable(onClick = onPlus),
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 18.sp, color = SchedText)
            }
        }
    }
}

/** 1080 -> "18:00". */
private fun minuteToLabel(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    return String.format("%02d:%02d", h, m)
}
