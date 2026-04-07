package com.momentum.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.momentum.app.data.local.entity.HabitEntity
import com.momentum.app.data.repo.DayTrackingSummary
import com.momentum.app.data.repo.SessionWithHabit
import com.momentum.app.domain.HabitValence
import com.momentum.app.domain.SessionStatus
import com.momentum.app.domain.TrackingMode
import com.momentum.app.ui.LocalRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val headerFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")

private fun sessionStatusLabel(status: String): String = when (status) {
    SessionStatus.COMPLETED.name -> "Completed"
    SessionStatus.MISSED.name -> "Not completed"
    SessionStatus.PLANNED.name -> "Open"
    else -> status
}

/**
 * Past calendar day: choose Insert vs Edit, then either pick a habit to log or edit the day like Today.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarPastDayFlow(
    date: LocalDate?,
    onDismiss: () -> Unit,
    onDataChanged: () -> Unit,
) {
    if (date == null) return

    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    var step by remember(date) { mutableStateOf(PastStep.PickMode) }
    var habits by remember { mutableStateOf<List<HabitEntity>>(emptyList()) }
    var insertMessage by remember { mutableStateOf<String?>(null) }
    var weightSession by remember { mutableStateOf<SessionWithHabit?>(null) }
    var weightInput by remember { mutableStateOf("") }
    var confirmPlainSession by remember { mutableStateOf<SessionWithHabit?>(null) }
    var daySummary by remember { mutableStateOf<DayTrackingSummary?>(null) }

    LaunchedEffect(date) {
        habits = repo.listActiveHabits()
        daySummary = repo.dayTrackingSummary(date)
    }

    fun refreshAll() {
        scope.launch {
            habits = repo.listActiveHabits()
            onDataChanged()
        }
    }

    when (step) {
        PastStep.PickMode -> {
            Dialog(onDismissRequest = onDismiss) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            date.format(headerFmt),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Insert a new entry for one habit, or edit everything logged for this day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "That day",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        val sum = daySummary
                        if (sum == null) {
                            Text("Loading…", style = MaterialTheme.typography.bodySmall)
                        } else if (sum.scheduled.isEmpty() && sum.unscheduled.isEmpty()) {
                            Text(
                                "Nothing logged yet (no scheduled sessions with activity and no unscheduled counts).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            sum.scheduled.forEach { row ->
                                Text(row.habitTitle, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    sessionStatusLabel(row.status),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (row.trackingMode == TrackingMode.WEIGHT.name) {
                                    Text(
                                        if (row.weightLbs != null) {
                                            "Weight: ${"%.1f".format(row.weightLbs)} lbs"
                                        } else {
                                            "No weigh-in logged"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (row.taskLines.isNotEmpty()) {
                                    Text("Tasks:", style = MaterialTheme.typography.bodySmall)
                                    row.taskLines.forEach { line ->
                                        Text(
                                            "  $line",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                            if (sum.unscheduled.isNotEmpty()) {
                                Text("Unscheduled habits", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                sum.unscheduled.forEach { u ->
                                    Text(
                                        "${u.habitTitle} (${u.valenceLabel}): ${u.summaryText}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        TextButton(
                            onClick = { step = PastStep.InsertPickHabit },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Insert")
                        }
                        TextButton(
                            onClick = { step = PastStep.EditSheet },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Edit")
                        }
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
        PastStep.InsertPickHabit -> {
            AlertDialog(
                onDismissRequest = { step = PastStep.PickMode },
                title = { Text("Choose habit to log") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        habits.forEach { h ->
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        if (h.isScheduled) {
                                            val s = repo.ensureSessionForHabitOnDate(h.id, date)
                                            if (s == null) {
                                                insertMessage = "Could not add a session for «${h.title}» on this day."
                                                return@launch
                                            }
                                            val row = SessionWithHabit(
                                                s,
                                                h.title,
                                                h.trackingMode,
                                                h.unit,
                                            )
                                            when (s.status) {
                                                SessionStatus.COMPLETED.name -> {
                                                    insertMessage =
                                                        "That session is already completed. Use Edit to view or change weight."
                                                }
                                                else -> {
                                                    if (h.trackingMode == TrackingMode.WEIGHT.name) {
                                                        weightInput = s.completionValue?.toString().orEmpty()
                                                        weightSession = row
                                                    } else {
                                                        confirmPlainSession = row
                                                    }
                                                }
                                            }
                                        } else {
                                            if (h.trackingMode == TrackingMode.WEIGHT.name) {
                                                insertMessage = "Weigh-ins use scheduled sessions only."
                                                return@launch
                                            }
                                            repo.quickLogForHabitOnDay(h.id, date)
                                            refreshAll()
                                            onDismiss()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(h.title)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { step = PastStep.PickMode }) {
                        Text("Back")
                    }
                },
            )
        }
        PastStep.EditSheet -> {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = sheetState,
            ) {
                PastDayEditContent(
                    date = date,
                    onClose = onDismiss,
                    onRefreshParent = { refreshAll() },
                )
            }
        }
    }

    insertMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { insertMessage = null },
            title = { Text("Can't insert") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { insertMessage = null }) {
                    Text("OK")
                }
            },
        )
    }

    weightSession?.let { row ->
        AlertDialog(
            onDismissRequest = {
                weightSession = null
                weightInput = ""
            },
            title = { Text("Log weight (lbs)") },
            text = {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    label = { Text("Weight") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                                weightSession = null
                                weightInput = ""
                                refreshAll()
                                onDismiss()
                            }
                        }
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    weightSession = null
                    weightInput = ""
                }) {
                    Text("Cancel")
                }
            },
        )
    }

    confirmPlainSession?.let { row ->
        AlertDialog(
            onDismissRequest = { confirmPlainSession = null },
            title = { Text("Complete session") },
            text = { Text("Mark «${row.habitTitle}» complete for this day?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repo.markSession(row.session.id, SessionStatus.COMPLETED)
                            confirmPlainSession = null
                            refreshAll()
                            onDismiss()
                        }
                    },
                ) {
                    Text("Complete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPlainSession = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private enum class PastStep { PickMode, InsertPickHabit, EditSheet }

@Composable
private fun PastDayEditContent(
    date: LocalDate,
    onClose: () -> Unit,
    onRefreshParent: () -> Unit,
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<SessionWithHabit>>(emptyList()) }
    var unscheduled by remember { mutableStateOf<List<com.momentum.app.data.repo.UnscheduledRow>>(emptyList()) }
    val expanded = remember { mutableStateOf(setOf<String>()) }
    var weightDialogSession by remember { mutableStateOf<SessionWithHabit?>(null) }
    var weightInput by remember { mutableStateOf("") }
    var logDetailHabitId by remember { mutableStateOf<String?>(null) }
    var logEntries by remember { mutableStateOf(emptyList<com.momentum.app.data.local.entity.LogEntity>()) }

    fun load() {
        scope.launch {
            sessions = repo.sessionsForDate(date)
            unscheduled = repo.unscheduledForDate(date)
        }
    }

    LaunchedEffect(date) {
        load()
    }

    LaunchedEffect(logDetailHabitId, date) {
        val hid = logDetailHabitId ?: return@LaunchedEffect
        logEntries = repo.logsForHabitOnDate(hid, date)
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                                onRefreshParent()
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
            title = { Text("Logs for this day") },
            text = {
                Column {
                    if (logEntries.isEmpty()) {
                        Text("No entries.")
                    } else {
                        logEntries.forEach { log ->
                            LogNoteRow(log = log, onSaved = {
                                scope.launch { logEntries = repo.logsForHabitOnDate(hid, date) }
                            })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    logDetailHabitId = null
                    load()
                    onRefreshParent()
                }) {
                    Text("Close")
                }
            },
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            date.format(headerFmt),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Same layout as Today: scheduled sessions first, then unscheduled habits. Completed sessions cannot be marked un-done.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text("Scheduled", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (sessions.isEmpty()) {
            Text(
                "No sessions on this day.",
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
                        expanded.value =
                            if (open) expanded.value - row.session.id else expanded.value + row.session.id
                    },
                    onAddTask = { title ->
                        repo.addTask(row.session.id, title)
                        load()
                        onRefreshParent()
                    },
                    onComplete = {
                        if (row.habitTrackingMode == TrackingMode.WEIGHT.name) {
                            weightInput = row.session.completionValue?.toString().orEmpty()
                            weightDialogSession = row
                        } else {
                            scope.launch {
                                repo.markSession(row.session.id, SessionStatus.COMPLETED)
                                load()
                                onRefreshParent()
                            }
                        }
                    },
                    onReset = {
                        scope.launch {
                            repo.resetSession(row.session.id)
                            load()
                            onRefreshParent()
                        }
                    },
                    onNotesChange = { notes ->
                        scope.launch {
                            repo.updateSessionNotes(row.session.id, notes)
                            load()
                            onRefreshParent()
                        }
                    },
                    onTaskToggle = { taskId, done ->
                        repo.setTaskCompleted(taskId, done)
                        load()
                        onRefreshParent()
                    },
                    loadTasks = { repo.tasksForSession(row.session.id) },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Habits", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (unscheduled.isEmpty()) {
            Text(
                "No unscheduled habits.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            unscheduled.forEach { row ->
                UnscheduledRowCard(
                    row = row,
                    onRefresh = {
                        load()
                        onRefreshParent()
                    },
                    onLongPressCount = {
                        logDetailHabitId = row.habit.id
                        scope.launch { logEntries = repo.logsForHabitOnDate(row.habit.id, date) }
                    },
                    allowUndo = true,
                    forDate = date,
                    negativeOutline = row.habit.valence == HabitValence.NEGATIVE.name,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}
