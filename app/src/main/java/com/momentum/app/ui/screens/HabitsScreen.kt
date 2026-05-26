package com.momentum.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.momentum.app.data.local.entity.HabitEntity
import com.momentum.app.domain.HabitGoalDomain
import com.momentum.app.domain.HabitValence
import com.momentum.app.ui.LocalRepository
import kotlinx.coroutines.launch

@Composable
fun HabitsScreen(
    onOpenHabit: (String) -> Unit,
    modifier: Modifier = Modifier,
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

    fun domainOf(h: HabitEntity): HabitGoalDomain? =
        h.goalDomain?.let { runCatching { HabitGoalDomain.valueOf(it) }.getOrNull() }

    val posMind = positive.filter { domainOf(it) == HabitGoalDomain.MIND }
    val posBody = positive.filter { domainOf(it) == HabitGoalDomain.BODY }
    val posUnset = positive.filter { domainOf(it) == null }
    val negMind = negative.filter { domainOf(it) == HabitGoalDomain.MIND }
    val negBody = negative.filter { domainOf(it) == HabitGoalDomain.BODY }
    val negUnset = negative.filter { domainOf(it) == null }

    Column(
        modifier
            .fillMaxSize(),
    ) {
            Text(
                "Habits",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Behaviors you track over time. Scheduled workouts and weigh-ins also appear on Today—open a habit below to log or edit it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (habits.isEmpty()) {
                Text(
                    "No habits yet. Tap + at the bottom of this screen to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (posMind.isNotEmpty()) {
                        item {
                            Text(
                                "Positive · Mind",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(posMind, key = { it.id }) { h ->
                            HabitRowCard(habit = h, onClick = { onOpenHabit(h.id) })
                        }
                    }
                    if (posBody.isNotEmpty()) {
                        item {
                            if (posMind.isNotEmpty()) Spacer(Modifier.height(8.dp))
                            Text(
                                "Positive · Body",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(posBody, key = { it.id }) { h ->
                            HabitRowCard(habit = h, onClick = { onOpenHabit(h.id) })
                        }
                    }
                    if (posUnset.isNotEmpty()) {
                        item {
                            if (posMind.isNotEmpty() || posBody.isNotEmpty()) Spacer(Modifier.height(8.dp))
                            Text(
                                "Positive · unset focus",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(posUnset, key = { it.id }) { h ->
                            HabitRowCard(habit = h, onClick = { onOpenHabit(h.id) })
                        }
                    }
                    if (negMind.isNotEmpty()) {
                        item {
                            if (positive.isNotEmpty()) Spacer(Modifier.height(8.dp))
                            Text(
                                "Negative · Mind",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        items(negMind, key = { it.id }) { h ->
                            HabitRowCard(habit = h, onClick = { onOpenHabit(h.id) })
                        }
                    }
                    if (negBody.isNotEmpty()) {
                        item {
                            if (positive.isNotEmpty() || negMind.isNotEmpty()) Spacer(Modifier.height(8.dp))
                            Text(
                                "Negative · Body",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        items(negBody, key = { it.id }) { h ->
                            HabitRowCard(habit = h, onClick = { onOpenHabit(h.id) })
                        }
                    }
                    if (negUnset.isNotEmpty()) {
                        item {
                            if (positive.isNotEmpty() || negMind.isNotEmpty() || negBody.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(
                                "Negative · unset focus",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        items(negUnset, key = { it.id }) { h ->
                            HabitRowCard(habit = h, onClick = { onOpenHabit(h.id) })
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
