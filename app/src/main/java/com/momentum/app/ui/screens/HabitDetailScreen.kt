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
import androidx.compose.material3.OutlinedButton
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
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var habit by remember { mutableStateOf<HabitEntity?>(null) }
    var sessions by remember { mutableStateOf<List<SessionEntity>>(emptyList()) }
    var logs by remember { mutableStateOf<List<LogEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var confirmArchive by remember { mutableStateOf(false) }

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
            Text(
                if (h.isScheduled) "Scheduled" else "Unscheduled · ${h.trackingMode ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            h.unit?.let { u ->
                Spacer(Modifier.height(4.dp))
                Text("Unit: $u", style = MaterialTheme.typography.bodySmall)
            }
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
                            " · ${s.completionValue} ${h.unit ?: ""}"
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
            OutlinedButton(
                onClick = { confirmArchive = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Archive habit")
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
}
