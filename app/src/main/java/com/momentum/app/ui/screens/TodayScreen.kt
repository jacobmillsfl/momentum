package com.momentum.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.momentum.app.data.local.entity.HabitEntity
import com.momentum.app.data.local.entity.LogEntity
import com.momentum.app.data.repo.SessionWithHabit
import com.momentum.app.data.repo.UnscheduledRow
import com.momentum.app.domain.HabitValence
import com.momentum.app.domain.SessionStatus
import com.momentum.app.domain.TrackingMode
import com.momentum.app.ui.LocalRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodayScreen() {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var sessions by remember { mutableStateOf<List<SessionWithHabit>>(emptyList()) }
    var unscheduled by remember { mutableStateOf<List<UnscheduledRow>>(emptyList()) }
    var allHabits by remember { mutableStateOf<List<HabitEntity>>(emptyList()) }
    var exerciseHabitsMissingTodaySession by remember { mutableStateOf<List<HabitEntity>>(emptyList()) }
    val expanded = remember { mutableStateOf(setOf<String>()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showQuickAdd by remember { mutableStateOf(false) }
    var logDetailHabitId by remember { mutableStateOf<String?>(null) }
    var logEntries by remember { mutableStateOf<List<LogEntity>>(emptyList()) }
    var weightDialogSession by remember { mutableStateOf<SessionWithHabit?>(null) }
    var weightInput by remember { mutableStateOf("") }

    val unscheduledPositive = remember(unscheduled) {
        unscheduled.filter { it.habit.valence == HabitValence.POSITIVE.name }
    }
    val unscheduledNegative = remember(unscheduled) {
        unscheduled.filter { it.habit.valence == HabitValence.NEGATIVE.name }
    }
    val quickAddHabits = remember(allHabits) {
        allHabits
            .filter { it.trackingMode != TrackingMode.WEIGHT.name }
            .sortedWith(
                compareBy<HabitEntity> {
                    if (it.valence == HabitValence.POSITIVE.name) 0 else 1
                }.thenBy { it.title.lowercase() },
            )
    }

    fun load() {
        scope.launch {
            sessions = repo.sessionsForToday()
            unscheduled = repo.unscheduledForToday()
            allHabits = repo.listActiveHabits()
            exerciseHabitsMissingTodaySession =
                repo.scheduledExerciseHabitsMissingSessionOnDate(LocalDate.now())
        }
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) load()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(logDetailHabitId) {
        val hid = logDetailHabitId ?: return@LaunchedEffect
        logEntries = repo.logsForHabitOnDate(hid, LocalDate.now())
    }

    weightDialogSession?.let { row ->
        AlertDialog(
            onDismissRequest = {
                weightDialogSession = null
                weightInput = ""
            },
            title = { Text("Log weight (lbs)") },
            text = {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    label = { Text("Weight") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val v = weightInput.toDoubleOrNull()
                        if (v != null && v > 0) {
                            scope.launch {
                                repo.markSession(row.session.id, SessionStatus.COMPLETED, completionValue = v)
                                weightDialogSession = null
                                weightInput = ""
                                load()
                            }
                        }
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    weightDialogSession = null
                    weightInput = ""
                }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (logDetailHabitId != null) {
        val hid = logDetailHabitId!!
        AlertDialog(
            onDismissRequest = { logDetailHabitId = null },
            title = { Text("Today's logs") },
            text = {
                Column {
                    if (logEntries.isEmpty()) {
                        Text("No entries yet.")
                    } else {
                        logEntries.forEach { log ->
                            LogNoteRow(log = log, onSaved = {
                                scope.launch { logEntries = repo.logsForHabitOnDate(hid, LocalDate.now()) }
                            })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    logDetailHabitId = null
                    load()
                }) {
                    Text("Close")
                }
            },
        )
    }

    if (showQuickAdd) {
        ModalBottomSheet(
            onDismissRequest = { showQuickAdd = false },
            sheetState = sheetState,
        ) {
            Text(
                "Log quickly",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
            LazyColumn(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                items(quickAddHabits, key = { it.id }) { h ->
                    TextButton(
                        onClick = {
                            scope.launch {
                                repo.quickLogForHabit(h.id)
                                showQuickAdd = false
                                load()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(h.title)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Today", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("Scheduled", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (sessions.isEmpty()) {
            Text(
                "No sessions scheduled for today.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            sessions.forEach { row ->
                val open = expanded.value.contains(row.session.id)
                SessionCardExpandable(
                    row = row,
                    expanded = open,
                    onToggle = {
                        expanded.value = if (open) expanded.value - row.session.id else expanded.value + row.session.id
                    },
                    onAddTask = { title ->
                        repo.addTask(row.session.id, title)
                        sessions = repo.sessionsForToday()
                        unscheduled = repo.unscheduledForToday()
                        allHabits = repo.listActiveHabits()
                    },
                    onComplete = {
                        if (row.habitTrackingMode == TrackingMode.WEIGHT.name) {
                            weightInput = row.session.completionValue?.toString().orEmpty()
                            weightDialogSession = row
                        } else {
                            scope.launch {
                                repo.markSession(row.session.id, SessionStatus.COMPLETED)
                                load()
                            }
                        }
                    },
                    onReset = {
                        scope.launch {
                            repo.resetSession(row.session.id)
                            load()
                        }
                    },
                    onNotesChange = { notes ->
                        scope.launch {
                            repo.updateSessionNotes(row.session.id, notes)
                            load()
                        }
                    },
                    onTaskToggle = { taskId, done ->
                        repo.setTaskCompleted(taskId, done)
                        sessions = repo.sessionsForToday()
                        unscheduled = repo.unscheduledForToday()
                        allHabits = repo.listActiveHabits()
                    },
                    loadTasks = { repo.tasksForSession(row.session.id) },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        if (exerciseHabitsMissingTodaySession.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Add a workout for today", style = MaterialTheme.typography.titleMedium)
            Text(
                "Not on your usual calendar day? Start a session here; the grid still shows your plan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            exerciseHabitsMissingTodaySession.forEach { h ->
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            repo.ensureSessionForHabitOnDate(h.id, LocalDate.now())
                            load()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start «${h.title}»")
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Habits", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showQuickAdd = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Quick add log")
            }
        }
        Spacer(Modifier.height(12.dp))
        if (unscheduled.isEmpty()) {
            Text(
                "No unscheduled habits.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (unscheduledPositive.isNotEmpty()) {
                Text(
                    "Positive habits",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                unscheduledPositive.forEach { row ->
                    UnscheduledRowCard(
                        row = row,
                        onRefresh = { load() },
                        onLongPressCount = {
                            logDetailHabitId = row.habit.id
                            scope.launch { logEntries = repo.logsForHabitOnDate(row.habit.id, LocalDate.now()) }
                        },
                        negativeOutline = false,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (unscheduledNegative.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Negative habits",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(6.dp))
                unscheduledNegative.forEach { row ->
                    UnscheduledRowCard(
                        row = row,
                        onRefresh = { load() },
                        onLongPressCount = {
                            logDetailHabitId = row.habit.id
                            scope.launch { logEntries = repo.logsForHabitOnDate(row.habit.id, LocalDate.now()) }
                        },
                        negativeOutline = true,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
