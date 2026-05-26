package com.momentum.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.momentum.app.data.local.entity.HabitEntity
import com.momentum.app.domain.HabitGoalDomain
import com.momentum.app.domain.RecurrenceDayRule
import com.momentum.app.domain.TrackingMode
import com.momentum.app.ui.LocalRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val isoLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private val editJson = Json { ignoreUnknownKeys = true }

private fun parseRecurrenceRules(recurrenceJson: String?): List<RecurrenceDayRule> {
    if (recurrenceJson.isNullOrBlank()) return emptyList()
    return try {
        editJson.decodeFromString(ListSerializer(RecurrenceDayRule.serializer()), recurrenceJson)
    } catch (_: Exception) {
        emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditScreen(
    habitId: String,
    onBack: () -> Unit,
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    var habit by remember { mutableStateOf<HabitEntity?>(null) }
    var loading by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var goalDomain by remember { mutableStateOf<HabitGoalDomain?>(null) }
    val dayActive = remember { mutableStateMapOf<Int, Boolean>() }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    fun applyHabit(h: HabitEntity) {
        habit = h
        title = h.title
        notes = h.notes.orEmpty()
        goalDomain = h.goalDomain?.let { runCatching { HabitGoalDomain.valueOf(it) }.getOrNull() }
        dayActive.clear()
        if (h.isScheduled) {
            val rules = parseRecurrenceRules(h.recurrenceJson)
            for (iso in 1..7) {
                dayActive[iso] = rules.any { it.dayOfWeek == iso }
            }
        }
    }

    LaunchedEffect(habitId) {
        loading = true
        val h = repo.habitById(habitId)
        if (h != null) applyHabit(h)
        else habit = null
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit habit") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val h = habit ?: return@TextButton
                            val trimmed = title.trim()
                            if (trimmed.isEmpty()) {
                                saveError = "Name is required"
                                return@TextButton
                            }
                            if (h.isScheduled) {
                                val anyDay = (1..7).any { dayActive[it] == true }
                                if (!anyDay) {
                                    saveError = "Select at least one day for the schedule"
                                    return@TextButton
                                }
                            }
                            saving = true
                            saveError = null
                            scope.launch {
                                try {
                                    repo.updateHabitMetadata(
                                        habitId = habitId,
                                        title = trimmed,
                                        notes = notes.trim().takeIf { it.isNotEmpty() },
                                        goalDomain = goalDomain,
                                    )
                                    if (h.isScheduled) {
                                        val rules = ArrayList<RecurrenceDayRule>()
                                        for (iso in 1..7) {
                                            if (dayActive[iso] != true) continue
                                            rules.add(
                                                RecurrenceDayRule(
                                                    dayOfWeek = iso,
                                                    categories = listOf("GENERAL"),
                                                    defaultNotes = null,
                                                ),
                                            )
                                        }
                                        repo.updateScheduledHabitWeeklyPlan(habitId, rules)
                                    }
                                    onBack()
                                } catch (e: IllegalStateException) {
                                    saveError = e.message ?: "Could not save"
                                } catch (e: IllegalArgumentException) {
                                    saveError = e.message ?: "Could not save"
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        enabled = !saving && habit != null,
                    ) {
                        Text("Save")
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                ) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }
            habit == null -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(20.dp),
                ) {
                    Text("Habit not found.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onBack) { Text("Go back") }
                }
                return@Scaffold
            }
        }

        val h = habit!!
        val isWeighIn = h.isScheduled && h.trackingMode == TrackingMode.WEIGHT.name
        val isWorkout = h.isScheduled && !isWeighIn

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            saveError?.let { err ->
                Text(
                    err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !saving,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                enabled = !saving,
            )
            Spacer(Modifier.height(16.dp))
            Text("Focus (Mind / Body)", style = MaterialTheme.typography.labelLarge)
            when {
                isWeighIn -> {
                    Text(
                        "Weigh-ins are fixed as Mind for trends.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                isWorkout -> {
                    Text(
                        "Workouts are fixed as Body for trends.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Text(
                        "Choose whether this habit mainly supports Mind or Body tracking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = goalDomain == HabitGoalDomain.MIND,
                            onClick = { goalDomain = HabitGoalDomain.MIND },
                            enabled = !saving,
                            label = { Text("Mind") },
                        )
                        FilterChip(
                            selected = goalDomain == HabitGoalDomain.BODY,
                            onClick = { goalDomain = HabitGoalDomain.BODY },
                            enabled = !saving,
                            label = { Text("Body") },
                        )
                    }
                }
            }
            if (h.isScheduled) {
                Spacer(Modifier.height(20.dp))
                Text("Weekly schedule", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (isWeighIn) {
                        "Enable each day you plan to weigh in."
                    } else {
                        "Enable each day you plan to work out."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        for (iso in 1..7) dayActive[iso] = true
                    },
                    enabled = !saving,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    Text("Select all days")
                }
                for (iso in 1..7) {
                    val label = isoLabels[iso - 1]
                    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label)
                            Switch(
                                checked = dayActive[iso] == true,
                                onCheckedChange = { on -> dayActive[iso] = on },
                                enabled = !saving,
                            )
                        }
                    }
                }
            }
        }
    }
}
