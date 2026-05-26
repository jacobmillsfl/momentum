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
import com.momentum.app.data.repo.DomainCompletionTotals
import com.momentum.app.data.repo.SessionWithHabit
import com.momentum.app.data.repo.TodayMindBodyCompletionTotals
import com.momentum.app.data.repo.UnscheduledRow
import com.momentum.app.domain.HabitGoalDomain
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
    var weighInHabitsMissingTodaySession by remember { mutableStateOf<List<HabitEntity>>(emptyList()) }
    val expanded = remember { mutableStateOf(setOf<String>()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showQuickAdd by remember { mutableStateOf(false) }
    var logDetailHabitId by remember { mutableStateOf<String?>(null) }
    var logEntries by remember { mutableStateOf<List<LogEntity>>(emptyList()) }
    var weightDialogSession by remember { mutableStateOf<SessionWithHabit?>(null) }
    var weightInput by remember { mutableStateOf("") }
    var mindBodyToday by remember {
        mutableStateOf(
            TodayMindBodyCompletionTotals(
                mind = DomainCompletionTotals(0.0, 0.0),
                body = DomainCompletionTotals(0.0, 0.0),
            ),
        )
    }
    fun domainOf(habit: HabitEntity): HabitGoalDomain? =
        habit.goalDomain?.let { runCatching { HabitGoalDomain.valueOf(it) }.getOrNull() }

    fun domainOfSession(row: SessionWithHabit): HabitGoalDomain? =
        row.habitGoalDomain?.let { runCatching { HabitGoalDomain.valueOf(it) }.getOrNull() }

    val quickAddHabits = remember(allHabits) {
        allHabits.filter { !it.isScheduled && it.trackingMode != TrackingMode.WEIGHT.name }
    }

    val schedPosMind = remember(sessions) {
        sessions.filter { it.habitValence == HabitValence.POSITIVE.name && domainOfSession(it) == HabitGoalDomain.MIND }
            .sortedBy { it.habitTitle.lowercase() }
    }
    val schedPosBody = remember(sessions) {
        sessions.filter { it.habitValence == HabitValence.POSITIVE.name && domainOfSession(it) == HabitGoalDomain.BODY }
            .sortedBy { it.habitTitle.lowercase() }
    }
    val schedPosUnset = remember(sessions) {
        sessions.filter { it.habitValence == HabitValence.POSITIVE.name && domainOfSession(it) == null }
            .sortedBy { it.habitTitle.lowercase() }
    }
    val schedNegMind = remember(sessions) {
        sessions.filter { it.habitValence == HabitValence.NEGATIVE.name && domainOfSession(it) == HabitGoalDomain.MIND }
            .sortedBy { it.habitTitle.lowercase() }
    }
    val schedNegBody = remember(sessions) {
        sessions.filter { it.habitValence == HabitValence.NEGATIVE.name && domainOfSession(it) == HabitGoalDomain.BODY }
            .sortedBy { it.habitTitle.lowercase() }
    }
    val schedNegUnset = remember(sessions) {
        sessions.filter { it.habitValence == HabitValence.NEGATIVE.name && domainOfSession(it) == null }
            .sortedBy { it.habitTitle.lowercase() }
    }

    val unschedPosMind = remember(unscheduled) {
        unscheduled.filter { it.habit.valence == HabitValence.POSITIVE.name && domainOf(it.habit) == HabitGoalDomain.MIND }
            .sortedBy { it.habit.title.lowercase() }
    }
    val unschedPosBody = remember(unscheduled) {
        unscheduled.filter { it.habit.valence == HabitValence.POSITIVE.name && domainOf(it.habit) == HabitGoalDomain.BODY }
            .sortedBy { it.habit.title.lowercase() }
    }
    val unschedPosUnset = remember(unscheduled) {
        unscheduled.filter { it.habit.valence == HabitValence.POSITIVE.name && domainOf(it.habit) == null }
            .sortedBy { it.habit.title.lowercase() }
    }
    val unschedNegMind = remember(unscheduled) {
        unscheduled.filter { it.habit.valence == HabitValence.NEGATIVE.name && domainOf(it.habit) == HabitGoalDomain.MIND }
            .sortedBy { it.habit.title.lowercase() }
    }
    val unschedNegBody = remember(unscheduled) {
        unscheduled.filter { it.habit.valence == HabitValence.NEGATIVE.name && domainOf(it.habit) == HabitGoalDomain.BODY }
            .sortedBy { it.habit.title.lowercase() }
    }
    val unschedNegUnset = remember(unscheduled) {
        unscheduled.filter { it.habit.valence == HabitValence.NEGATIVE.name && domainOf(it.habit) == null }
            .sortedBy { it.habit.title.lowercase() }
    }

    val quickPosMind = remember(quickAddHabits) {
        quickAddHabits.filter { it.valence == HabitValence.POSITIVE.name && domainOf(it) == HabitGoalDomain.MIND }
            .sortedBy { it.title.lowercase() }
    }
    val quickPosBody = remember(quickAddHabits) {
        quickAddHabits.filter { it.valence == HabitValence.POSITIVE.name && domainOf(it) == HabitGoalDomain.BODY }
            .sortedBy { it.title.lowercase() }
    }
    val quickPosUnset = remember(quickAddHabits) {
        quickAddHabits.filter { it.valence == HabitValence.POSITIVE.name && domainOf(it) == null }
            .sortedBy { it.title.lowercase() }
    }
    val quickNegMind = remember(quickAddHabits) {
        quickAddHabits.filter { it.valence == HabitValence.NEGATIVE.name && domainOf(it) == HabitGoalDomain.MIND }
            .sortedBy { it.title.lowercase() }
    }
    val quickNegBody = remember(quickAddHabits) {
        quickAddHabits.filter { it.valence == HabitValence.NEGATIVE.name && domainOf(it) == HabitGoalDomain.BODY }
            .sortedBy { it.title.lowercase() }
    }
    val quickNegUnset = remember(quickAddHabits) {
        quickAddHabits.filter { it.valence == HabitValence.NEGATIVE.name && domainOf(it) == null }
            .sortedBy { it.title.lowercase() }
    }

    val exercisePosMind = remember(exerciseHabitsMissingTodaySession) {
        exerciseHabitsMissingTodaySession.filter { it.valence == HabitValence.POSITIVE.name && domainOf(it) == HabitGoalDomain.MIND }
            .sortedBy { it.title.lowercase() }
    }
    val exercisePosBody = remember(exerciseHabitsMissingTodaySession) {
        exerciseHabitsMissingTodaySession.filter { it.valence == HabitValence.POSITIVE.name && domainOf(it) == HabitGoalDomain.BODY }
            .sortedBy { it.title.lowercase() }
    }
    val exercisePosUnset = remember(exerciseHabitsMissingTodaySession) {
        exerciseHabitsMissingTodaySession.filter { it.valence == HabitValence.POSITIVE.name && domainOf(it) == null }
            .sortedBy { it.title.lowercase() }
    }
    val exerciseNegMind = remember(exerciseHabitsMissingTodaySession) {
        exerciseHabitsMissingTodaySession.filter { it.valence == HabitValence.NEGATIVE.name && domainOf(it) == HabitGoalDomain.MIND }
            .sortedBy { it.title.lowercase() }
    }
    val exerciseNegBody = remember(exerciseHabitsMissingTodaySession) {
        exerciseHabitsMissingTodaySession.filter { it.valence == HabitValence.NEGATIVE.name && domainOf(it) == HabitGoalDomain.BODY }
            .sortedBy { it.title.lowercase() }
    }
    val exerciseNegUnset = remember(exerciseHabitsMissingTodaySession) {
        exerciseHabitsMissingTodaySession.filter { it.valence == HabitValence.NEGATIVE.name && domainOf(it) == null }
            .sortedBy { it.title.lowercase() }
    }

    val weighPosMind = remember(weighInHabitsMissingTodaySession) {
        weighInHabitsMissingTodaySession.filter { it.valence == HabitValence.POSITIVE.name && domainOf(it) == HabitGoalDomain.MIND }
            .sortedBy { it.title.lowercase() }
    }
    val weighPosBody = remember(weighInHabitsMissingTodaySession) {
        weighInHabitsMissingTodaySession.filter { it.valence == HabitValence.POSITIVE.name && domainOf(it) == HabitGoalDomain.BODY }
            .sortedBy { it.title.lowercase() }
    }
    val weighPosUnset = remember(weighInHabitsMissingTodaySession) {
        weighInHabitsMissingTodaySession.filter { it.valence == HabitValence.POSITIVE.name && domainOf(it) == null }
            .sortedBy { it.title.lowercase() }
    }
    val weighNegMind = remember(weighInHabitsMissingTodaySession) {
        weighInHabitsMissingTodaySession.filter { it.valence == HabitValence.NEGATIVE.name && domainOf(it) == HabitGoalDomain.MIND }
            .sortedBy { it.title.lowercase() }
    }
    val weighNegBody = remember(weighInHabitsMissingTodaySession) {
        weighInHabitsMissingTodaySession.filter { it.valence == HabitValence.NEGATIVE.name && domainOf(it) == HabitGoalDomain.BODY }
            .sortedBy { it.title.lowercase() }
    }
    val weighNegUnset = remember(weighInHabitsMissingTodaySession) {
        weighInHabitsMissingTodaySession.filter { it.valence == HabitValence.NEGATIVE.name && domainOf(it) == null }
            .sortedBy { it.title.lowercase() }
    }

    fun load() {
        scope.launch {
            sessions = repo.sessionsForToday()
            unscheduled = repo.unscheduledForToday()
            allHabits = repo.listActiveHabits()
            exerciseHabitsMissingTodaySession =
                repo.scheduledExerciseHabitsMissingSessionOnDate(LocalDate.now())
            weighInHabitsMissingTodaySession =
                repo.scheduledWeighInHabitsMissingSessionOnDate(LocalDate.now())
            mindBodyToday = repo.todayMindBodyCompletionTotals()
        }
    }

    LaunchedEffect(Unit) {
        load()
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
                if (quickPosMind.isNotEmpty()) {
                    item { TodayValenceDomainLabel("Positive · Mind", positive = true) }
                    items(quickPosMind, key = { it.id }) { h ->
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
                if (quickPosBody.isNotEmpty()) {
                    item {
                        if (quickPosMind.isNotEmpty()) Spacer(Modifier.height(8.dp))
                        TodayValenceDomainLabel("Positive · Body", positive = true)
                    }
                    items(quickPosBody, key = { it.id }) { h ->
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
                if (quickPosUnset.isNotEmpty()) {
                    item {
                        if (quickPosMind.isNotEmpty() || quickPosBody.isNotEmpty()) Spacer(Modifier.height(8.dp))
                        TodayValenceDomainLabel("Positive · unset focus", positive = true)
                    }
                    items(quickPosUnset, key = { it.id }) { h ->
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
                if (quickNegMind.isNotEmpty()) {
                    item {
                        if (quickPosMind.isNotEmpty() || quickPosBody.isNotEmpty() || quickPosUnset.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                        }
                        TodayValenceDomainLabel("Negative · Mind", positive = false)
                    }
                    items(quickNegMind, key = { it.id }) { h ->
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
                if (quickNegBody.isNotEmpty()) {
                    item {
                        if (quickPosMind.isNotEmpty() || quickPosBody.isNotEmpty() || quickPosUnset.isNotEmpty() ||
                            quickNegMind.isNotEmpty()
                        ) {
                            Spacer(Modifier.height(8.dp))
                        }
                        TodayValenceDomainLabel("Negative · Body", positive = false)
                    }
                    items(quickNegBody, key = { it.id }) { h ->
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
                if (quickNegUnset.isNotEmpty()) {
                    item {
                        if (quickPosMind.isNotEmpty() || quickPosBody.isNotEmpty() || quickPosUnset.isNotEmpty() ||
                            quickNegMind.isNotEmpty() || quickNegBody.isNotEmpty()
                        ) {
                            Spacer(Modifier.height(8.dp))
                        }
                        TodayValenceDomainLabel("Negative · unset focus", positive = false)
                    }
                    items(quickNegUnset, key = { it.id }) { h ->
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
        TodayMindBodyGauge(
            mind = mindBodyToday.mind,
            body = mindBodyToday.body,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Text("Scheduled", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (sessions.isEmpty()) {
            Text(
                "No sessions scheduled for today.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            @Composable
            fun SessionRows(rows: List<SessionWithHabit>) {
                rows.forEach { row ->
                    val open = expanded.value.contains(row.session.id)
                    SessionCardExpandable(
                        row = row,
                        expanded = open,
                        onToggle = {
                            expanded.value =
                                if (open) expanded.value - row.session.id else expanded.value + row.session.id
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

            if (schedPosMind.isNotEmpty()) {
                TodayValenceDomainLabel("Positive · Mind", positive = true)
                Spacer(Modifier.height(6.dp))
                SessionRows(schedPosMind)
            }
            if (schedPosBody.isNotEmpty()) {
                if (schedPosMind.isNotEmpty()) Spacer(Modifier.height(8.dp))
                TodayValenceDomainLabel("Positive · Body", positive = true)
                Spacer(Modifier.height(6.dp))
                SessionRows(schedPosBody)
            }
            if (schedPosUnset.isNotEmpty()) {
                if (schedPosMind.isNotEmpty() || schedPosBody.isNotEmpty()) Spacer(Modifier.height(8.dp))
                TodayValenceDomainLabel("Positive · unset focus", positive = true)
                Spacer(Modifier.height(6.dp))
                SessionRows(schedPosUnset)
            }
            if (schedNegMind.isNotEmpty()) {
                if (schedPosMind.isNotEmpty() || schedPosBody.isNotEmpty() || schedPosUnset.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                }
                TodayValenceDomainLabel("Negative · Mind", positive = false)
                Spacer(Modifier.height(6.dp))
                SessionRows(schedNegMind)
            }
            if (schedNegBody.isNotEmpty()) {
                if (schedPosMind.isNotEmpty() || schedPosBody.isNotEmpty() || schedPosUnset.isNotEmpty() ||
                    schedNegMind.isNotEmpty()
                ) {
                    Spacer(Modifier.height(8.dp))
                }
                TodayValenceDomainLabel("Negative · Body", positive = false)
                Spacer(Modifier.height(6.dp))
                SessionRows(schedNegBody)
            }
            if (schedNegUnset.isNotEmpty()) {
                if (schedPosMind.isNotEmpty() || schedPosBody.isNotEmpty() || schedPosUnset.isNotEmpty() ||
                    schedNegMind.isNotEmpty() || schedNegBody.isNotEmpty()
                ) {
                    Spacer(Modifier.height(8.dp))
                }
                TodayValenceDomainLabel("Negative · unset focus", positive = false)
                Spacer(Modifier.height(6.dp))
                SessionRows(schedNegUnset)
            }
        }
        if (exerciseHabitsMissingTodaySession.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Add a workout for today", style = MaterialTheme.typography.titleMedium)
            Text(
                "Only if you added a scheduled workout habit in Habits. Not on your usual day? Start a session here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (exercisePosMind.isNotEmpty()) {
                TodayValenceDomainLabel("Positive · Mind", positive = true)
                Spacer(Modifier.height(6.dp))
                MissedScheduledStartButtons(
                    habits = exercisePosMind,
                    onStart = { id ->
                        scope.launch {
                            repo.ensureSessionForHabitOnDate(id, LocalDate.now())
                            load()
                        }
                    },
                )
            }
            if (exercisePosBody.isNotEmpty()) {
                if (exercisePosMind.isNotEmpty()) Spacer(Modifier.height(8.dp))
                TodayValenceDomainLabel("Positive · Body", positive = true)
                Spacer(Modifier.height(6.dp))
                MissedScheduledStartButtons(
                    habits = exercisePosBody,
                    onStart = { id ->
                        scope.launch {
                            repo.ensureSessionForHabitOnDate(id, LocalDate.now())
                            load()
                        }
                    },
                )
            }
            if (exercisePosUnset.isNotEmpty()) {
                if (exercisePosMind.isNotEmpty() || exercisePosBody.isNotEmpty()) Spacer(Modifier.height(8.dp))
                TodayValenceDomainLabel("Positive · unset focus", positive = true)
                Spacer(Modifier.height(6.dp))
                MissedScheduledStartButtons(
                    habits = exercisePosUnset,
                    onStart = { id ->
                        scope.launch {
                            repo.ensureSessionForHabitOnDate(id, LocalDate.now())
                            load()
                        }
                    },
                )
            }
            if (exerciseNegMind.isNotEmpty()) {
                if (exercisePosMind.isNotEmpty() || exercisePosBody.isNotEmpty() || exercisePosUnset.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                }
                TodayValenceDomainLabel("Negative · Mind", positive = false)
                Spacer(Modifier.height(6.dp))
                MissedScheduledStartButtons(
                    habits = exerciseNegMind,
                    onStart = { id ->
                        scope.launch {
                            repo.ensureSessionForHabitOnDate(id, LocalDate.now())
                            load()
                        }
                    },
                )
            }
            if (exerciseNegBody.isNotEmpty()) {
                if (exercisePosMind.isNotEmpty() || exercisePosBody.isNotEmpty() || exercisePosUnset.isNotEmpty() ||
                    exerciseNegMind.isNotEmpty()
                ) {
                    Spacer(Modifier.height(8.dp))
                }
                TodayValenceDomainLabel("Negative · Body", positive = false)
                Spacer(Modifier.height(6.dp))
                MissedScheduledStartButtons(
                    habits = exerciseNegBody,
                    onStart = { id ->
                        scope.launch {
                            repo.ensureSessionForHabitOnDate(id, LocalDate.now())
                            load()
                        }
                    },
                )
            }
            if (exerciseNegUnset.isNotEmpty()) {
                if (exercisePosMind.isNotEmpty() || exercisePosBody.isNotEmpty() || exercisePosUnset.isNotEmpty() ||
                    exerciseNegMind.isNotEmpty() || exerciseNegBody.isNotEmpty()
                ) {
                    Spacer(Modifier.height(8.dp))
                }
                TodayValenceDomainLabel("Negative · unset focus", positive = false)
                Spacer(Modifier.height(6.dp))
                MissedScheduledStartButtons(
                    habits = exerciseNegUnset,
                    onStart = { id ->
                        scope.launch {
                            repo.ensureSessionForHabitOnDate(id, LocalDate.now())
                            load()
                        }
                    },
                )
            }
        }
        if (weighInHabitsMissingTodaySession.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Add a weigh-in for today", style = MaterialTheme.typography.titleMedium)
            Text(
                "Only if you added a scheduled weigh-in habit in Habits. Use this when today is not on your weigh-in schedule.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (weighPosMind.isNotEmpty()) {
                TodayValenceDomainLabel("Positive · Mind", positive = true)
                Spacer(Modifier.height(6.dp))
                MissedScheduledStartButtons(
                    habits = weighPosMind,
                    onStart = { id ->
                        scope.launch {
                            repo.ensureSessionForHabitOnDate(id, LocalDate.now())
                            load()
                        }
                    },
                )
            }
            if (weighPosBody.isNotEmpty()) {
                if (weighPosMind.isNotEmpty()) Spacer(Modifier.height(8.dp))
                TodayValenceDomainLabel("Positive · Body", positive = true)
                Spacer(Modifier.height(6.dp))
                MissedScheduledStartButtons(
                    habits = weighPosBody,
                    onStart = { id ->
                        scope.launch {
                            repo.ensureSessionForHabitOnDate(id, LocalDate.now())
                            load()
                        }
                    },
                )
            }
            if (weighPosUnset.isNotEmpty()) {
                if (weighPosMind.isNotEmpty() || weighPosBody.isNotEmpty()) Spacer(Modifier.height(8.dp))
                TodayValenceDomainLabel("Positive · unset focus", positive = true)
                Spacer(Modifier.height(6.dp))
                MissedScheduledStartButtons(
                    habits = weighPosUnset,
                    onStart = { id ->
                        scope.launch {
                            repo.ensureSessionForHabitOnDate(id, LocalDate.now())
                            load()
                        }
                    },
                )
            }
            if (weighNegMind.isNotEmpty()) {
                if (weighPosMind.isNotEmpty() || weighPosBody.isNotEmpty() || weighPosUnset.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                }
                TodayValenceDomainLabel("Negative · Mind", positive = false)
                Spacer(Modifier.height(6.dp))
                MissedScheduledStartButtons(
                    habits = weighNegMind,
                    onStart = { id ->
                        scope.launch {
                            repo.ensureSessionForHabitOnDate(id, LocalDate.now())
                            load()
                        }
                    },
                )
            }
            if (weighNegBody.isNotEmpty()) {
                if (weighPosMind.isNotEmpty() || weighPosBody.isNotEmpty() || weighPosUnset.isNotEmpty() ||
                    weighNegMind.isNotEmpty()
                ) {
                    Spacer(Modifier.height(8.dp))
                }
                TodayValenceDomainLabel("Negative · Body", positive = false)
                Spacer(Modifier.height(6.dp))
                MissedScheduledStartButtons(
                    habits = weighNegBody,
                    onStart = { id ->
                        scope.launch {
                            repo.ensureSessionForHabitOnDate(id, LocalDate.now())
                            load()
                        }
                    },
                )
            }
            if (weighNegUnset.isNotEmpty()) {
                if (weighPosMind.isNotEmpty() || weighPosBody.isNotEmpty() || weighPosUnset.isNotEmpty() ||
                    weighNegMind.isNotEmpty() || weighNegBody.isNotEmpty()
                ) {
                    Spacer(Modifier.height(8.dp))
                }
                TodayValenceDomainLabel("Negative · unset focus", positive = false)
                Spacer(Modifier.height(6.dp))
                MissedScheduledStartButtons(
                    habits = weighNegUnset,
                    onStart = { id ->
                        scope.launch {
                            repo.ensureSessionForHabitOnDate(id, LocalDate.now())
                            load()
                        }
                    },
                )
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
            @Composable
            fun UnschedRows(rows: List<UnscheduledRow>, negativeOutline: Boolean) {
                rows.forEach { row ->
                    UnscheduledRowCard(
                        row = row,
                        onRefresh = { load() },
                        onLongPressCount = {
                            logDetailHabitId = row.habit.id
                            scope.launch { logEntries = repo.logsForHabitOnDate(row.habit.id, LocalDate.now()) }
                        },
                        negativeOutline = negativeOutline,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (unschedPosMind.isNotEmpty()) {
                TodayValenceDomainLabel("Positive · Mind", positive = true)
                Spacer(Modifier.height(6.dp))
                UnschedRows(unschedPosMind, negativeOutline = false)
            }
            if (unschedPosBody.isNotEmpty()) {
                if (unschedPosMind.isNotEmpty()) Spacer(Modifier.height(8.dp))
                TodayValenceDomainLabel("Positive · Body", positive = true)
                Spacer(Modifier.height(6.dp))
                UnschedRows(unschedPosBody, negativeOutline = false)
            }
            if (unschedPosUnset.isNotEmpty()) {
                if (unschedPosMind.isNotEmpty() || unschedPosBody.isNotEmpty()) Spacer(Modifier.height(8.dp))
                TodayValenceDomainLabel("Positive · unset focus", positive = true)
                Spacer(Modifier.height(6.dp))
                UnschedRows(unschedPosUnset, negativeOutline = false)
            }
            if (unschedNegMind.isNotEmpty()) {
                if (unschedPosMind.isNotEmpty() || unschedPosBody.isNotEmpty() || unschedPosUnset.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                }
                TodayValenceDomainLabel("Negative · Mind", positive = false)
                Spacer(Modifier.height(6.dp))
                UnschedRows(unschedNegMind, negativeOutline = true)
            }
            if (unschedNegBody.isNotEmpty()) {
                if (unschedPosMind.isNotEmpty() || unschedPosBody.isNotEmpty() || unschedPosUnset.isNotEmpty() ||
                    unschedNegMind.isNotEmpty()
                ) {
                    Spacer(Modifier.height(8.dp))
                }
                TodayValenceDomainLabel("Negative · Body", positive = false)
                Spacer(Modifier.height(6.dp))
                UnschedRows(unschedNegBody, negativeOutline = true)
            }
            if (unschedNegUnset.isNotEmpty()) {
                if (unschedPosMind.isNotEmpty() || unschedPosBody.isNotEmpty() || unschedPosUnset.isNotEmpty() ||
                    unschedNegMind.isNotEmpty() || unschedNegBody.isNotEmpty()
                ) {
                    Spacer(Modifier.height(8.dp))
                }
                TodayValenceDomainLabel("Negative · unset focus", positive = false)
                Spacer(Modifier.height(6.dp))
                UnschedRows(unschedNegUnset, negativeOutline = true)
            }
        }
    }
}

@Composable
private fun TodayValenceDomainLabel(text: String, positive: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun MissedScheduledStartButtons(
    habits: List<HabitEntity>,
    onStart: (String) -> Unit,
) {
    habits.forEach { h ->
        OutlinedButton(
            onClick = { onStart(h.id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start «${h.title}»")
        }
        Spacer(Modifier.height(6.dp))
    }
}
