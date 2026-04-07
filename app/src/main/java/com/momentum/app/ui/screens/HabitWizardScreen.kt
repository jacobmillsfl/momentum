package com.momentum.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.momentum.app.data.repo.NewHabitInput
import com.momentum.app.domain.HabitValence
import com.momentum.app.domain.RecurrenceDayRule
import com.momentum.app.domain.TrackingMode
import com.momentum.app.ui.LocalRepository
import kotlinx.coroutines.launch

private val CATEGORY_PRESETS = listOf(
    "GENERAL",
    "UPPER_BODY",
    "LOWER_BODY",
    "ABS",
    "CARDIO",
    "STRETCH",
)

private val ISO_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable
private fun SectionDescription(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
fun HabitWizardScreen(onDone: () -> Unit) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(1) }
    var title by remember { mutableStateOf("") }
    var valence by remember { mutableStateOf<HabitValence?>(null) }
    var notes by remember { mutableStateOf("") }
    var scheduleKind by remember { mutableStateOf<String?>(null) }
    /** For positive scheduled: "STANDARD" (workouts/tasks) or "WEIGHT" (weigh-in). */
    var scheduledSessionKind by remember { mutableStateOf<String?>(null) }
    var trackingMode by remember { mutableStateOf<TrackingMode?>(null) }
    var unit by remember { mutableStateOf("") }
    val dayActive = remember { mutableStateMapOf<Int, Boolean>() }
    val dayCategories = remember { mutableStateMapOf<Int, Set<String>>() }
    val dayNotes = remember { mutableStateMapOf<Int, String>() }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    fun defaultCatsForDay(iso: Int) {
        if (!dayCategories.containsKey(iso)) {
            dayCategories[iso] = setOf("GENERAL")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("New habit · Step $step of 3", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        when (step) {
            1 -> {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(16.dp))
                Text("Habit type", style = MaterialTheme.typography.labelLarge)
                SectionDescription("Choose whether you are building a helpful habit or tracking something you want to cut back.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HabitTypeCard(
                        title = "Positive",
                        description = "Behaviors you want to repeat or grow.",
                        selected = valence == HabitValence.POSITIVE,
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = { valence = HabitValence.POSITIVE },
                    )
                    HabitTypeCard(
                        title = "Negative",
                        description = "Behaviors you want to notice and reduce over time.",
                        selected = valence == HabitValence.NEGATIVE,
                        accent = MaterialTheme.colorScheme.error,
                        onClick = { valence = HabitValence.NEGATIVE },
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }
            2 -> when (valence) {
                HabitValence.NEGATIVE -> {
                    Text("Unit", style = MaterialTheme.typography.labelLarge)
                    SectionDescription("Unscheduled habits use counts only. Tap + / − on Today to log how often this comes up (cigarettes, snacks, etc.).")
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                HabitValence.POSITIVE -> {
                    Text("Schedule", style = MaterialTheme.typography.labelLarge)
                    SectionDescription("Scheduled habits get planned sessions on specific days. Unscheduled habits are logged whenever you need, without a calendar.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = scheduleKind == "SCHEDULED",
                            onClick = {
                                scheduleKind = "SCHEDULED"
                                if (scheduledSessionKind == null) scheduledSessionKind = "STANDARD"
                            },
                            label = { Text("Scheduled") },
                        )
                        FilterChip(
                            selected = scheduleKind == "UNSCHEDULED",
                            onClick = {
                                scheduleKind = "UNSCHEDULED"
                                scheduledSessionKind = null
                                trackingMode = TrackingMode.COUNT
                            },
                            label = { Text("Unscheduled") },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    when (scheduleKind) {
                        "SCHEDULED" -> {
                            Text("Session type", style = MaterialTheme.typography.labelLarge)
                            SectionDescription("Workouts use optional to-dos and notes. Weigh-in stores a measurement when you complete the session.")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = scheduledSessionKind == "STANDARD",
                                    onClick = { scheduledSessionKind = "STANDARD" },
                                    label = { Text("Workouts / tasks") },
                                )
                                FilterChip(
                                    selected = scheduledSessionKind == "WEIGHT",
                                    onClick = { scheduledSessionKind = "WEIGHT" },
                                    label = { Text("Weigh-in") },
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Weekly plan", style = MaterialTheme.typography.titleSmall)
                            SectionDescription("Enable each day you train, then choose focus tags and optional default notes for that day.")
                            TextButton(
                                onClick = {
                                    for (iso in 1..7) {
                                        dayActive[iso] = true
                                        defaultCatsForDay(iso)
                                    }
                                },
                                modifier = Modifier.padding(bottom = 8.dp),
                            ) {
                                Text("Select all days")
                            }
                            for (iso in 1..7) {
                                val label = ISO_LABELS[iso - 1]
                                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(label)
                                            Switch(
                                                checked = dayActive[iso] == true,
                                                onCheckedChange = { on ->
                                                    dayActive[iso] = on
                                                    if (on) defaultCatsForDay(iso)
                                                },
                                            )
                                        }
                                        if (dayActive[iso] == true) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                CATEGORY_PRESETS.forEach { cat ->
                                                    val set = dayCategories[iso] ?: setOf("GENERAL")
                                                    val sel = cat in set
                                                    FilterChip(
                                                        selected = sel,
                                                        onClick = {
                                                            val next = set.toMutableSet()
                                                            if (next.contains(cat)) {
                                                                next.remove(cat)
                                                                if (next.isEmpty()) next.add("GENERAL")
                                                            } else {
                                                                next.add(cat)
                                                            }
                                                            dayCategories[iso] = next
                                                        },
                                                        label = { Text(cat, maxLines = 1) },
                                                    )
                                                }
                                            }
                                            OutlinedTextField(
                                                value = dayNotes[iso].orEmpty(),
                                                onValueChange = { dayNotes[iso] = it },
                                                label = { Text("Default notes") },
                                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                                singleLine = true,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        "UNSCHEDULED" -> {
                            Text("Unit", style = MaterialTheme.typography.labelLarge)
                            SectionDescription("Unscheduled habits are count-based. Use + and − on Today to log totals (pages, glasses of water, etc.).")
                            OutlinedTextField(
                                value = unit,
                                onValueChange = { unit = it },
                                label = { Text("Unit") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                        }
                        else -> {}
                    }
                }
                null -> {}
            }
            3 -> {
                Text("Review", style = MaterialTheme.typography.titleMedium)
                saveError?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(title.ifBlank { "—" }, style = MaterialTheme.typography.headlineSmall)
                Text(
                    buildString {
                        append(valence?.name ?: "")
                        if (valence == HabitValence.NEGATIVE || scheduleKind == "UNSCHEDULED") {
                            append(" · Incremental")
                            if (unit.isNotBlank()) append(" ($unit)")
                        }
                        if (valence == HabitValence.POSITIVE && scheduleKind == "SCHEDULED") {
                            append(
                                when (scheduledSessionKind) {
                                    "WEIGHT" -> " · Scheduled weigh-in (lbs)"
                                    else -> " · Scheduled"
                                },
                            )
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    when {
                        step == 1 -> onDone()
                        else -> step--
                    }
                },
                enabled = !saving,
            ) {
                Text(if (step == 1) "Cancel" else "Back")
            }
            Button(
                onClick = {
                    when (step) {
                        1 -> {
                            if (title.isBlank() || valence == null) return@Button
                            if (valence == HabitValence.NEGATIVE) {
                                trackingMode = TrackingMode.COUNT
                            }
                            step = 2
                        }
                        2 -> {
                            if (!validateStep2(
                                    valence,
                                    scheduleKind,
                                    scheduledSessionKind,
                                    unit,
                                    dayActive,
                                )
                            ) {
                                return@Button
                            }
                            step = 3
                        }
                        3 -> {
                            saveError = null
                            val input = buildInput(
                                title,
                                valence!!,
                                notes,
                                scheduleKind,
                                scheduledSessionKind,
                                unit,
                                dayActive,
                                dayNotes,
                                dayCategories,
                            ) ?: return@Button
                            saving = true
                            scope.launch {
                                try {
                                    repo.createHabit(input)
                                    onDone()
                                } catch (e: IllegalStateException) {
                                    saveError = e.message ?: "Could not save habit."
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    }
                },
                enabled = !saving,
            ) {
                Text(
                    when (step) {
                        3 -> "Save"
                        else -> "Next"
                    },
                )
            }
        }
    }
}

@Composable
private fun RowScope.HabitTypeCard(
    title: String,
    description: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Card(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 104.dp)
            .selectable(selected, onClick = onClick, role = Role.Button),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
        ),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 3.dp else 0.dp),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .background(accent.copy(alpha = if (selected) 1f else 0.45f), RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun validateStep2(
    valence: HabitValence?,
    scheduleKind: String?,
    scheduledSessionKind: String?,
    unit: String,
    dayActive: Map<Int, Boolean>,
): Boolean {
    if (valence == null) return false
    if (valence == HabitValence.NEGATIVE) {
        return unit.isNotBlank()
    }
    if (scheduleKind == null) return false
    if (scheduleKind == "SCHEDULED") {
        val any = (1..7).any { dayActive[it] == true }
        if (!any) return false
        if (scheduledSessionKind == null) return false
        return true
    }
    if (scheduleKind == "UNSCHEDULED") {
        return unit.isNotBlank()
    }
    return false
}

private fun buildInput(
    title: String,
    valence: HabitValence,
    notes: String,
    scheduleKind: String?,
    scheduledSessionKind: String?,
    unit: String,
    dayActive: Map<Int, Boolean>,
    dayNotes: Map<Int, String>,
    dayCategories: Map<Int, Set<String>>,
): NewHabitInput? {
    return when (valence) {
        HabitValence.NEGATIVE -> NewHabitInput(
            title = title,
            valence = valence,
            notes = notes.takeIf { it.isNotBlank() },
            isScheduled = false,
            trackingMode = TrackingMode.COUNT,
            unit = unit.trim().takeIf { it.isNotEmpty() } ?: return null,
            recurrence = null,
        )
        HabitValence.POSITIVE -> when (scheduleKind) {
            "SCHEDULED" -> {
                val rules = ArrayList<RecurrenceDayRule>()
                for (iso in 1..7) {
                    if (dayActive[iso] != true) continue
                    val cats = dayCategories[iso]?.toList() ?: listOf("GENERAL")
                    val dn = dayNotes[iso]?.trim().orEmpty()
                    rules.add(
                        RecurrenceDayRule(
                            dayOfWeek = iso,
                            categories = cats,
                            defaultNotes = dn.ifEmpty { null },
                        ),
                    )
                }
                if (rules.isEmpty()) return null
                val tm = when (scheduledSessionKind) {
                    "WEIGHT" -> TrackingMode.WEIGHT
                    else -> null
                }
                NewHabitInput(
                    title = title,
                    valence = valence,
                    notes = notes.takeIf { it.isNotBlank() },
                    isScheduled = true,
                    trackingMode = tm,
                    unit = if (tm == TrackingMode.WEIGHT) "lbs" else null,
                    recurrence = rules.sortedBy { it.dayOfWeek },
                )
            }
            "UNSCHEDULED" -> NewHabitInput(
                title = title,
                valence = valence,
                notes = notes.takeIf { it.isNotBlank() },
                isScheduled = false,
                trackingMode = TrackingMode.COUNT,
                unit = unit.trim().takeIf { it.isNotEmpty() } ?: return null,
                recurrence = null,
            )
            else -> null
        }
    }
}
