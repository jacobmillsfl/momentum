package com.momentum.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.momentum.app.data.local.entity.ProjectEntity
import com.momentum.app.ui.LocalRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dueDisplayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

/**
 * Material3 [DatePicker] uses UTC midnight for [androidx.compose.material3.DatePickerState.selectedDateMillis].
 * Converting with the device zone shifts that instant to the previous calendar day west of UTC; use UTC for
 * calendar dates only (matches [LocalDate.toEpochDay] semantics we store as [ProjectEntity.dueEpochDay]).
 */
private fun localDateToPickerMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun pickerMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectDueDatePickerDialog(
    initialDue: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val initialMillis = remember(initialDue) {
        localDateToPickerMillis(initialDue ?: LocalDate.now())
    }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        initialDisplayedMonthMillis = initialMillis,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis ?: initialMillis
                    onConfirm(pickerMillisToLocalDate(millis))
                },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
fun ProjectsScreen(
    modifier: Modifier = Modifier,
    onEditProject: (String) -> Unit,
    /** Increment after project create/update/delete so the list reloads while this tab stays visible. */
    refreshSignal: Int = 0,
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf<List<ProjectEntity>>(emptyList()) }
    var completed by remember { mutableStateOf<List<ProjectEntity>>(emptyList()) }
    var filterTab by remember { mutableIntStateOf(0) }

    fun load() {
        scope.launch {
            active = repo.listProjectsActive()
            completed = repo.listProjectsCompleted()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) load()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(refreshSignal) {
        load()
    }

    Column(modifier.fillMaxSize()) {
        Text(
            "Projects",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "One-off work you want to remember. Optional due dates show on Progress.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filterTab == 0,
                onClick = { filterTab = 0 },
                label = { Text("Active") },
            )
            FilterChip(
                selected = filterTab == 1,
                onClick = { filterTab = 1 },
                label = { Text("Completed") },
            )
            FilterChip(
                selected = filterTab == 2,
                onClick = { filterTab = 2 },
                label = { Text("All") },
            )
        }
        Spacer(Modifier.height(12.dp))

        val showActive = filterTab == 0 || filterTab == 2
        val showCompleted = filterTab == 1 || filterTab == 2

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showActive && active.isNotEmpty()) {
                if (filterTab == 2) {
                    item {
                        Text(
                            "Open",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                items(active, key = { it.id }) { p ->
                    ProjectRowCard(
                        project = p,
                        onClick = { onEditProject(p.id) },
                        onToggleComplete = { done ->
                            scope.launch {
                                repo.updateProject(
                                    id = p.id,
                                    title = p.title,
                                    description = p.description,
                                    dueDate = p.dueEpochDay?.let { LocalDate.ofEpochDay(it) },
                                    completed = done,
                                )
                                load()
                            }
                        },
                    )
                }
            }
            if (showCompleted && completed.isNotEmpty()) {
                if (filterTab == 2) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Completed",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(completed, key = { it.id }) { p ->
                    ProjectRowCard(
                        project = p,
                        onClick = { onEditProject(p.id) },
                        onToggleComplete = { done ->
                            scope.launch {
                                repo.updateProject(
                                    id = p.id,
                                    title = p.title,
                                    description = p.description,
                                    dueDate = p.dueEpochDay?.let { LocalDate.ofEpochDay(it) },
                                    completed = done,
                                )
                                load()
                            }
                        },
                    )
                }
            }
            val showEmpty = when (filterTab) {
                0 -> active.isEmpty()
                1 -> completed.isEmpty()
                else -> active.isEmpty() && completed.isEmpty()
            }
            if (showEmpty) {
                item {
                    Text(
                        when (filterTab) {
                            0 -> "No open projects."
                            1 -> "No completed projects yet."
                            else -> "No projects yet. Tap + to add one."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectRowCard(
    project: ProjectEntity,
    onClick: () -> Unit,
    onToggleComplete: (Boolean) -> Unit,
) {
    val done = project.completedAtMs != null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = done,
                onCheckedChange = onToggleComplete,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    project.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                val due = project.dueEpochDay?.let { LocalDate.ofEpochDay(it) }
                Text(
                    when {
                        due != null -> "Due ${due.format(dueDisplayFmt)}"
                        else -> "No due date"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditorDialog(
    projectId: String?,
    onDismiss: () -> Unit,
    onProjectChanged: () -> Unit = {},
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var completed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showDueDatePicker by remember { mutableStateOf(false) }

    val isEdit = projectId != null

    LaunchedEffect(projectId) {
        if (projectId == null) {
            title = ""
            description = ""
            dueDate = null
            completed = false
        } else {
            val p = repo.projectById(projectId) ?: return@LaunchedEffect
            title = p.title
            description = p.description.orEmpty()
            dueDate = p.dueEpochDay?.let { LocalDate.ofEpochDay(it) }
            completed = p.completedAtMs != null
        }
    }

    if (confirmDelete && projectId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete project?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repo.deleteProject(projectId)
                            confirmDelete = false
                            onProjectChanged()
                            onDismiss()
                        }
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
        return
    }

    if (showDueDatePicker) {
        ProjectDueDatePickerDialog(
            initialDue = dueDate,
            onDismiss = { showDueDatePicker = false },
            onConfirm = { picked ->
                dueDate = picked
                error = null
                showDueDatePicker = false
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit project" else "New project") },
        text = {
            Column {
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Due date (optional)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    dueDate?.format(dueDisplayFmt) ?: "No due date",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = { showDueDatePicker = true }) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Pick date")
                    }
                    if (dueDate != null) {
                        TextButton(onClick = { dueDate = null; error = null }) {
                            Text("Clear")
                        }
                    }
                }
                if (isEdit) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = completed, onCheckedChange = { completed = it })
                        Text("Completed", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        try {
                            if (isEdit) {
                                repo.updateProject(
                                    id = projectId!!,
                                    title = title,
                                    description = description,
                                    dueDate = dueDate,
                                    completed = completed,
                                )
                            } else {
                                repo.createProject(
                                    title = title,
                                    description = description.trim().takeIf { it.isNotEmpty() },
                                    dueDate = dueDate,
                                )
                            }
                            onProjectChanged()
                            onDismiss()
                        } catch (e: IllegalStateException) {
                            error = e.message
                        }
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (isEdit) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
