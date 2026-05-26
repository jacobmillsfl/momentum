package com.momentum.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
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
import com.momentum.app.ui.LocalRepository
import kotlinx.coroutines.launch

/**
 * One-time flow after upgrade: assign Mind or Body to habits that could not be inferred
 * (scheduled workouts → Body and weigh-ins → Mind are applied automatically before this screen).
 */
@Composable
fun GoalDomainMigrationScreen(onComplete: () -> Unit) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    var habits by remember { mutableStateOf<List<HabitEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val picks = remember { mutableStateMapOf<String, HabitGoalDomain>() }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        habits = repo.listHabitsNeedingGoalDomainClassification()
        loading = false
    }

    if (loading) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("One quick update", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "Habits are now grouped into Mind (mental, emotional, social) and Body (physical) for clearer trends. " +
                "Scheduled workouts and weigh-ins were updated automatically. Choose Mind or Body for each habit below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        if (habits.isEmpty()) {
            Text("Nothing to update.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        repo.completeGoalDomainMigration(emptyMap())
                        onComplete()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue")
            }
        } else {
            habits.forEach { h ->
                Text(h.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = picks[h.id] == HabitGoalDomain.MIND,
                        onClick = {
                            picks[h.id] = HabitGoalDomain.MIND
                            errorText = null
                        },
                        label = { Text("Mind") },
                    )
                    FilterChip(
                        selected = picks[h.id] == HabitGoalDomain.BODY,
                        onClick = {
                            picks[h.id] = HabitGoalDomain.BODY
                            errorText = null
                        },
                        label = { Text("Body") },
                    )
                }
            }
            errorText?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val missing = habits.filter { picks[it.id] == null }
                    if (missing.isNotEmpty()) {
                        errorText = "Choose Mind or Body for every habit."
                        return@Button
                    }
                    scope.launch {
                        try {
                            repo.completeGoalDomainMigration(
                                habits.associate { it.id to picks[it.id]!! },
                            )
                            onComplete()
                        } catch (e: Exception) {
                            errorText = e.message ?: "Could not save."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save and continue")
            }
        }
    }
}
