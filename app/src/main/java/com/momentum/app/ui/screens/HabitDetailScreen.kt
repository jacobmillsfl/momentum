package com.momentum.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.momentum.app.data.local.entity.HabitEntity
import com.momentum.app.data.local.entity.LogEntity
import com.momentum.app.data.local.entity.SessionEntity
import com.momentum.app.domain.HabitGoalDomain
import com.momentum.app.domain.HabitValence
import com.momentum.app.domain.TrackingMode
import com.momentum.app.ui.LocalRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val logFmt = DateTimeFormatter.ofPattern("MMM d, h:mm a")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    habitId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMergedToNewHabit: (newHabitId: String) -> Unit,
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var habit by remember { mutableStateOf<HabitEntity?>(null) }
    var sessions by remember { mutableStateOf<List<SessionEntity>>(emptyList()) }
    var logs by remember { mutableStateOf<List<LogEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var confirmArchive by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showMerge by remember { mutableStateOf(false) }
    var mergeNewTitle by remember { mutableStateOf("") }
    var mergeOtherId by remember { mutableStateOf<String?>(null) }
    var mergeCandidates by remember { mutableStateOf<List<HabitEntity>>(emptyList()) }
    var mergeBusy by remember { mutableStateOf(false) }
    var mergeError by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            val h = repo.habitById(habitId)
            habit = h
            if (h != null) {
                if (h.isScheduled) {
                    sessions = repo.sessionsForHabitWindow(habitId, days = 42)
                    logs = emptyList()
                } else {
                    sessions = emptyList()
                    logs = repo.logsForHabitWindow(habitId, days = 30)
                }
            }
            loading = false
        }
    }

    LaunchedEffect(habitId) {
        load()
    }

    LaunchedEffect(showMerge, habitId, habit) {
        if (!showMerge || habit == null) return@LaunchedEffect
        val h = habit!!
        mergeCandidates = repo.listActiveHabits()
            .filter { o ->
                o.id != habitId &&
                    o.id != h.id &&
                    o.valence == h.valence &&
                    o.isScheduled == h.isScheduled &&
                    o.trackingMode == h.trackingMode &&
                    o.goalDomain != null &&
                    h.goalDomain != null &&
                    o.goalDomain == h.goalDomain
            }
            .sortedBy { it.title.lowercase() }
    }

    LaunchedEffect(mergeCandidates, habitId) {
        if (mergeOtherId != null && mergeOtherId == habitId) {
            mergeOtherId = null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) load()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(habit?.title ?: "Habit") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    TextButton(onClick = onBack) { Text("Go back") }
                }
                return@Scaffold
            }
        }

        val h = habit!!
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(
                if (h.valence == HabitValence.POSITIVE.name) "Positive" else "Negative",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            val goalDomain = h.goalDomain?.let { runCatching { HabitGoalDomain.valueOf(it) }.getOrNull() }
            if (goalDomain != null) {
                Text(
                    when (goalDomain) {
                        HabitGoalDomain.MIND -> "Focus: Mind (mental, emotional, social)"
                        HabitGoalDomain.BODY -> "Focus: Body (physical)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                if (h.isScheduled) "Scheduled" else "Unscheduled · ${h.trackingMode ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            h.notes?.let { n ->
                Spacer(Modifier.height(12.dp))
                Text(n, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(24.dp))
            if (h.isScheduled) {
                Text("Recent sessions", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (sessions.isEmpty()) {
                    Text(
                        "No sessions in the last six weeks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    sessions.sortedByDescending { it.scheduledAt }.forEach { s ->
                        val day = Instant.ofEpochMilli(s.scheduledAt).atZone(ZoneId.systemDefault()).toLocalDate()
                        val weightPart = if (h.trackingMode == TrackingMode.WEIGHT.name && s.completionValue != null) {
                            " · ${s.completionValue} lbs"
                        } else {
                            ""
                        }
                        Text(
                            "${day.format(dayFmt)} · ${s.status}$weightPart",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            } else {
                Text("Recent logs", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (logs.isEmpty()) {
                    Text(
                        "No logs in the last 30 days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    logs.sortedByDescending { it.loggedAt }.forEach { log ->
                        val line = when (h.trackingMode) {
                            TrackingMode.COUNT.name -> "+${log.numericValue?.toInt() ?: 0}"
                            TrackingMode.BOOLEAN.name -> if (log.booleanValue == 1) "On" else "Off"
                            else -> log.notes ?: "—"
                        }
                        val time = Instant.ofEpochMilli(log.loggedAt).atZone(ZoneId.systemDefault()).format(logFmt)
                        Text(
                            "$time · $line${log.notes?.let { " · $it" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Text("Actions", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Edit")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    mergeError = null
                    mergeOtherId = null
                    mergeNewTitle = ""
                    showMerge = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Merge with another habit…")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { confirmArchive = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Archive habit")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Delete habit permanently")
            }
        }
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Archive this habit?") },
            text = { Text("Archived habits are hidden from lists. This cannot be undone from the app in v1.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repo.archiveHabit(habitId)
                            confirmArchive = false
                            onBack()
                        }
                    },
                ) {
                    Text("Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmArchive = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this habit?") },
            text = {
                Text(
                    "This permanently deletes the habit and all of its logs and history. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repo.deleteHabitPermanently(habitId)
                            confirmDelete = false
                            onBack()
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
    }

    if (showMerge) {
        AlertDialog(
            onDismissRequest = {
                if (!mergeBusy) showMerge = false
            },
            title = { Text("Merge habits") },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "Pick another habit of the same type and focus. Logs move to a new habit with the name you enter; the two originals are removed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = mergeNewTitle,
                        onValueChange = { mergeNewTitle = it },
                        label = { Text("New habit name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !mergeBusy,
                    )
                    mergeError?.let { err ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Merge with:", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    when {
                        mergeCandidates.isEmpty() -> {
                            Text(
                                "No compatible habits. Another habit must match type (positive/negative), scheduled mode, tracking kind, and Mind/Body focus.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            mergeCandidates.forEach { o ->
                                val selected = mergeOtherId == o.id
                                TextButton(
                                    onClick = {
                                        if (o.id == habitId) return@TextButton
                                        mergeOtherId = o.id
                                        mergeError = null
                                    },
                                    enabled = !mergeBusy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        if (selected) "✓ ${o.title}" else o.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val other = mergeOtherId ?: return@TextButton
                        if (other == habitId) {
                            mergeError = "Pick a different habit than the one you are merging from"
                            return@TextButton
                        }
                        val name = mergeNewTitle.trim()
                        if (name.isEmpty()) {
                            mergeError = "Enter a name for the merged habit"
                            return@TextButton
                        }
                        mergeBusy = true
                        mergeError = null
                        scope.launch {
                            try {
                                val newId = repo.mergeHabitsIntoNew(habitId, other, name)
                                showMerge = false
                                onMergedToNewHabit(newId)
                            } catch (e: IllegalStateException) {
                                mergeError = e.message
                            } catch (e: IllegalArgumentException) {
                                mergeError = e.message
                            } finally {
                                mergeBusy = false
                            }
                        }
                    },
                    enabled = !mergeBusy &&
                        mergeOtherId != null &&
                        mergeOtherId != habitId &&
                        mergeNewTitle.isNotBlank() &&
                        mergeCandidates.isNotEmpty(),
                ) {
                    Text("Merge")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!mergeBusy) showMerge = false },
                    enabled = !mergeBusy,
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}
