package com.momentum.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.momentum.app.domain.HabitValence
import com.momentum.app.ui.LocalRepository
import kotlinx.coroutines.launch

@Composable
fun HabitsScreen(
    onNewHabit: () -> Unit,
    onOpenHabit: (String) -> Unit,
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var habits by remember { mutableStateOf<List<HabitEntity>>(emptyList()) }

    fun load() {
        scope.launch { habits = repo.listActiveHabits() }
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) load()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val positive = habits.filter { it.valence == HabitValence.POSITIVE.name }
    val negative = habits.filter { it.valence == HabitValence.NEGATIVE.name }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onNewHabit) {
                Icon(Icons.Default.Add, contentDescription = "New habit")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
        ) {
            Text("Habits", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            if (habits.isEmpty()) {
                Text(
                    "No habits yet. Tap + to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (positive.isNotEmpty()) {
                        item {
                            Text("Positive", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        items(positive, key = { it.id }) { h ->
                            HabitRowCard(habit = h, onClick = { onOpenHabit(h.id) })
                        }
                    }
                    if (negative.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text("Negative", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        }
                        items(negative, key = { it.id }) { h ->
                            HabitRowCard(habit = h, onClick = { onOpenHabit(h.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitRowCard(
    habit: HabitEntity,
    onClick: () -> Unit,
) {
    val typeLabel = if (habit.isScheduled) "Scheduled" else "Unscheduled"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(habit.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                typeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
