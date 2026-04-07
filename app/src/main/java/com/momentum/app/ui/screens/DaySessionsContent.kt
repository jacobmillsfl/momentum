package com.momentum.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.momentum.app.data.local.entity.HabitEntity
import com.momentum.app.data.local.entity.LogEntity
import com.momentum.app.data.local.entity.TaskEntity
import com.momentum.app.data.repo.SessionWithHabit
import com.momentum.app.data.repo.UnscheduledRow
import com.momentum.app.domain.SessionStatus
import com.momentum.app.domain.TrackingMode
import com.momentum.app.ui.LocalRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val json = Json { ignoreUnknownKeys = true }

private fun parseCategories(categoriesJson: String): List<String> = try {
    json.decodeFromString(ListSerializer(serializer<String>()), categoriesJson)
} catch (_: Exception) {
    emptyList()
}

private val logTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")

private fun sessionStatusDisplay(status: String): String = when (status) {
    SessionStatus.COMPLETED.name -> "Completed"
    SessionStatus.PLANNED.name -> "Open"
    SessionStatus.MISSED.name -> "Not completed"
    else -> status
}

@Composable
fun LogNoteRow(
    log: LogEntity,
    onSaved: () -> Unit,
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    var note by remember(log.id) { mutableStateOf(log.notes.orEmpty()) }
    LaunchedEffect(log.id, log.notes) {
        note = log.notes.orEmpty()
    }
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            Instant.ofEpochMilli(log.loggedAt).atZone(ZoneId.systemDefault()).format(logTimeFormatter),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note") },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = {
            scope.launch {
                repo.updateLogNotes(log.id, note)
                onSaved()
            }
        }) {
            Text("Save note")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnscheduledRowCard(
    row: UnscheduledRow,
    onRefresh: () -> Unit,
    onLongPressCount: () -> Unit,
    allowUndo: Boolean = true,
    forDate: LocalDate? = null,
    negativeOutline: Boolean = false,
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(12.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (negativeOutline) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.error, shape)
                } else {
                    Modifier
                },
            ),
        shape = shape,
    ) {
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.habit.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (row.habit.isScheduled) "Scheduled" else "Unscheduled",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val countVal = row.todayDisplay.toIntOrNull() ?: 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            if (forDate == null) repo.decrementCount(row.habit.id)
                            else repo.decrementCountOnDay(row.habit.id, forDate)
                            onRefresh()
                        }
                    },
                    enabled = allowUndo && countVal > 0,
                ) { Text("−") }
                Text(
                    row.todayDisplay,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onLongPressCount,
                    ),
                )
                Button(onClick = {
                    scope.launch {
                        if (forDate == null) repo.addCountLog(row.habit.id)
                        else repo.addCountLogOnDay(row.habit.id, forDate)
                        onRefresh()
                    }
                }) { Text("+") }
            }
        }
    }
}

@Composable
private fun SessionCardTitleBlock(
    row: SessionWithHabit,
    isExerciseSession: Boolean,
    categoriesLine: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(row.habitTitle, style = MaterialTheme.typography.titleMedium)
        if (row.habitTrackingMode == TrackingMode.WEIGHT.name) {
            Text(
                "Weigh-in · lbs",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        if (isExerciseSession) {
            Text(
                categoriesLine.ifEmpty { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Status: ${sessionStatusDisplay(row.session.status)}",
            style = MaterialTheme.typography.bodySmall,
        )
        if (row.session.completionValue != null) {
            Text(
                "Logged: ${row.session.completionValue} lbs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionCardExpandable(
    row: SessionWithHabit,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddTask: suspend (String) -> Unit,
    onComplete: () -> Unit,
    onReset: () -> Unit,
    onNotesChange: (String) -> Unit,
    onTaskToggle: suspend (String, Boolean) -> Unit,
    loadTasks: suspend () -> List<TaskEntity>,
) {
    val scope = rememberCoroutineScope()
    var newTaskTitle by remember(row.session.id) { mutableStateOf("") }
    var tasks by remember(row.session.id) { mutableStateOf<List<TaskEntity>>(emptyList()) }
    var notes by remember(row.session.id) { mutableStateOf(row.session.notes.orEmpty()) }

    LaunchedEffect(row.session.id) {
        tasks = loadTasks()
    }

    val isExerciseSession = row.habitTrackingMode != TrackingMode.WEIGHT.name
    val cats = parseCategories(row.session.categoriesJson).joinToString(", ")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (isExerciseSession) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = onToggle, onLongClick = {}),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SessionCardTitleBlock(row, isExerciseSession, cats, Modifier.weight(1f))
                    Text(if (expanded) "▼" else "▶", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SessionCardTitleBlock(row, isExerciseSession, cats, Modifier.weight(1f))
                }
            }
            val todosIncomplete =
                isExerciseSession && tasks.isNotEmpty() && !tasks.all { it.completed }
            val canCompleteSession =
                row.session.status == SessionStatus.PLANNED.name &&
                    (!isExerciseSession || !todosIncomplete)
            val canUpdateWeight =
                row.habitTrackingMode == TrackingMode.WEIGHT.name &&
                    row.session.status == SessionStatus.COMPLETED.name
            val canResetSession =
                isExerciseSession &&
                    (row.session.status == SessionStatus.COMPLETED.name ||
                        row.session.status == SessionStatus.MISSED.name)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                when {
                    canUpdateWeight -> {
                        Button(onClick = onComplete) {
                            Text("Update weight")
                        }
                    }
                    else -> {
                        Button(onClick = onComplete, enabled = canCompleteSession) {
                            Text("Complete")
                        }
                    }
                }
                if (isExerciseSession) {
                    OutlinedButton(onClick = onReset, enabled = canResetSession) {
                        Text("Reset session")
                    }
                }
            }
            if (todosIncomplete && row.session.status == SessionStatus.PLANNED.name) {
                Text(
                    "Check off all to-dos to complete this session.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            AnimatedVisibility(visible = expanded && isExerciseSession) {
                Column(Modifier.padding(top = 12.dp)) {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = {
                            notes = it
                            onNotesChange(it)
                        },
                        label = { Text("Session notes") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    Text("To-dos", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    tasks.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = t.completed,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        onTaskToggle(t.id, checked)
                                        tasks = loadTasks()
                                    }
                                },
                            )
                            Text(t.title, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newTaskTitle,
                            onValueChange = { newTaskTitle = it },
                            label = { Text("Add to-do") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        TextButton(
                            onClick = {
                                val t = newTaskTitle.trim()
                                if (t.isNotEmpty()) {
                                    scope.launch {
                                        onAddTask(t)
                                        tasks = loadTasks()
                                        newTaskTitle = ""
                                    }
                                }
                            },
                        ) {
                            Text("Add")
                        }
                    }
                }
            }
        }
    }
}
