package com.momentum.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.momentum.app.data.repo.NewHabitInput
import com.momentum.app.domain.HabitGoalDomain
import com.momentum.app.domain.HabitValence
import com.momentum.app.domain.RecurrenceDayRule
import com.momentum.app.domain.TrackingMode
import com.momentum.app.ui.LocalRepository
import kotlinx.coroutines.launch

private val ISO_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private val EXAMPLE_POSITIVE_TITLES = listOf(
    "Drink Gallon of Water",
    "Read a Book",
    "10-minute stretch",
    "Walk",
    "Wake Up Early",
    "Eat Healthy Meal",
    "Study",
)

private val EXAMPLE_NEGATIVE_TITLES = listOf(
    "Eat Unhealthy Meal",
    "Doomscroll",
    "Alcohol",
    "Smoking / vaping",
    "Late-night snacking",
    "Impulse shopping",
    "Stayed up too late",
    "Procrastination",
    "Skipped workout",
    "Energy drinks",
    "Extra caffeine",
    "Binge watching",
)

/** Positive habits: workout (scheduled), weigh-in (scheduled), or unscheduled count. */
private const val KIND_WORKOUT = "WORKOUT"
private const val KIND_WEIGH_IN = "WEIGH_IN"
private const val KIND_UNSCHEDULED = "UNSCHEDULED"

private const val DEFAULT_TITLE_WORKOUT = "Workout"
private const val DEFAULT_TITLE_WEIGH_IN = "Weigh-in"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PositiveExampleChips(onPick: (String) -> Unit) {
    Text("Common examples", style = MaterialTheme.typography.labelLarge)
    SectionDescription("Tap a suggestion to fill the title. You can edit it.")
    Spacer(Modifier.height(4.dp))
    Text(
        "Good habits (positive)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    SectionDescription("Behaviors you want to repeat, build, or keep.")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EXAMPLE_POSITIVE_TITLES.forEach { line ->
            SuggestionChip(
                onClick = { onPick(line) },
                label = { Text(line, style = MaterialTheme.typography.bodySmall) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NegativeExampleChips(onPick: (String) -> Unit) {
    Text("Common examples", style = MaterialTheme.typography.labelLarge)
    SectionDescription("Tap a suggestion to fill the title. You can edit it.")
    Spacer(Modifier.height(4.dp))
    Text(
        "Bad habits to cut back (negative)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.error,
    )
    SectionDescription("Behaviors you want to notice and reduce over time.")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EXAMPLE_NEGATIVE_TITLES.forEach { line ->
            SuggestionChip(
                onClick = { onPick(line) },
                label = { Text(line, style = MaterialTheme.typography.bodySmall) },
            )
        }
    }
}

@Composable
private fun SectionDescription(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
fun HabitWizardScreen(onDone: () -> Unit) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(1) }
    var title by remember { mutableStateOf("") }
    var valence by remember { mutableStateOf<HabitValence?>(null) }
    var notes by remember { mutableStateOf("") }
    /** For positive: workout, weigh-in, or unscheduled. */
    var positiveKind by remember { mutableStateOf<String?>(null) }
    var trackingMode by remember { mutableStateOf<TrackingMode?>(null) }
    val dayActive = remember { mutableStateMapOf<Int, Boolean>() }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var hasScheduledWorkoutAlready by remember { mutableStateOf(false) }
    var hasScheduledWeighInAlready by remember { mutableStateOf(false) }
    var goalDomain by remember { mutableStateOf<HabitGoalDomain?>(null) }

    LaunchedEffect(Unit) {
        hasScheduledWorkoutAlready = repo.hasScheduledWorkoutHabit()
        hasScheduledWeighInAlready = repo.hasScheduledWeighInHabit()
    }

    fun selectWorkout() {
        valence = HabitValence.POSITIVE
        positiveKind = KIND_WORKOUT
        trackingMode = null
        title = DEFAULT_TITLE_WORKOUT
        goalDomain = HabitGoalDomain.BODY
    }

    fun selectWeighIn() {
        valence = HabitValence.POSITIVE
        positiveKind = KIND_WEIGH_IN
        trackingMode = TrackingMode.WEIGHT
        title = DEFAULT_TITLE_WEIGH_IN
        goalDomain = HabitGoalDomain.MIND
    }

    fun selectUnscheduledPositive() {
        valence = HabitValence.POSITIVE
        positiveKind = KIND_UNSCHEDULED
        trackingMode = TrackingMode.COUNT
        title = ""
        goalDomain = null
    }

    fun selectNegative() {
        valence = HabitValence.NEGATIVE
        positiveKind = null
        trackingMode = TrackingMode.COUNT
        title = ""
        goalDomain = null
    }

    val resolvedTitle = effectiveHabitTitle(title, valence, positiveKind)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("New habit · Step $step of 4", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        when (step) {
            1 -> {
                Text("Habit kind", style = MaterialTheme.typography.labelLarge)
                SectionDescription(
                    "Start here. Workout and Weigh-in use simple default names; name your habit on the next step only if you pick Unscheduled or Negative. " +
                        "You can only have one scheduled workout and one scheduled weigh-in—archive an existing one in Habits to replace it.",
                )
                Text("Positive", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = valence == HabitValence.POSITIVE && positiveKind == KIND_WORKOUT,
                        onClick = { selectWorkout() },
                        enabled = !hasScheduledWorkoutAlready,
                        label = { Text("Workout") },
                    )
                    FilterChip(
                        selected = valence == HabitValence.POSITIVE && positiveKind == KIND_WEIGH_IN,
                        onClick = { selectWeighIn() },
                        enabled = !hasScheduledWeighInAlready,
                        label = { Text("Weigh-in") },
                    )
                    FilterChip(
                        selected = valence == HabitValence.POSITIVE && positiveKind == KIND_UNSCHEDULED,
                        onClick = { selectUnscheduledPositive() },
                        label = { Text("Other") },
                    )
                }
                if (hasScheduledWorkoutAlready || hasScheduledWeighInAlready) {
                    SectionDescription(
                        buildString {
                            if (hasScheduledWorkoutAlready) {
                                append("Scheduled workout: already set up—archive it under Habits to add another here. ")
                            }
                            if (hasScheduledWeighInAlready) {
                                append("Scheduled weigh-in: already set up—archive it under Habits to add another here.")
                            }
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Negative", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                FilterChip(
                    selected = valence == HabitValence.NEGATIVE,
                    onClick = { selectNegative() },
                    label = { Text("Track a habit to cut back") },
                )
                Spacer(Modifier.height(16.dp))
                when {
                    valence == HabitValence.POSITIVE && (positiveKind == KIND_WORKOUT || positiveKind == KIND_WEIGH_IN) -> {
                        Text("Weekly plan", style = MaterialTheme.typography.titleSmall)
                        SectionDescription(
                            if (positiveKind == KIND_WEIGH_IN) {
                                "Enable each day you plan to weigh in."
                            } else {
                                "Enable each day you plan to work out. Add details in Notes on the next step if you like."
                            },
                        )
                        TextButton(
                            onClick = {
                                for (iso in 1..7) {
                                    dayActive[iso] = true
                                }
                            },
                            modifier = Modifier.padding(bottom = 8.dp),
                        ) {
                            Text("Select all days")
                        }
                        for (iso in 1..7) {
                            val label = ISO_LABELS[iso - 1]
                            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(label)
                                        Switch(
                                            checked = dayActive[iso] == true,
                                            onCheckedChange = { on -> dayActive[iso] = on },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    valence == HabitValence.POSITIVE && positiveKind == KIND_UNSCHEDULED -> {
                        SectionDescription(
                            "Log anytime with + and − on Today (counts only). You will choose Mind or Body, then name the habit.",
                        )
                    }
                    valence == HabitValence.NEGATIVE -> {
                        SectionDescription(
                            "Use + and − on Today to log how often this comes up. Next you will choose Mind or Body, then name the habit.",
                        )
                    }
                    else -> {
                        SectionDescription("Choose a kind above to continue.")
                    }
                }
            }
            2 -> {
                Text("Focus area", style = MaterialTheme.typography.labelLarge)
                when {
                    valence == HabitValence.POSITIVE && positiveKind == KIND_WORKOUT -> {
                        SectionDescription(
                            "Workouts always count toward physical health. This habit is fixed as Body.",
                        )
                        Text(
                            "Body · physical health",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    valence == HabitValence.POSITIVE && positiveKind == KIND_WEIGH_IN -> {
                        SectionDescription(
                            "Weigh-ins are treated as a wellness check-in and always count toward Mind for trends.",
                        )
                        Text(
                            "Mind · mental, emotional & social health",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    valence == HabitValence.POSITIVE && positiveKind == KIND_UNSCHEDULED -> {
                        SectionDescription(
                            "Choose whether this habit mainly supports how you feel and connect (Mind) or your physical health (Body).",
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = goalDomain == HabitGoalDomain.MIND,
                                onClick = { goalDomain = HabitGoalDomain.MIND },
                                label = { Text("Mind") },
                            )
                            FilterChip(
                                selected = goalDomain == HabitGoalDomain.BODY,
                                onClick = { goalDomain = HabitGoalDomain.BODY },
                                label = { Text("Body") },
                            )
                        }
                    }
                    valence == HabitValence.NEGATIVE -> {
                        SectionDescription(
                            "Choose whether this habit mainly harms Mind (mental, emotional, social) or Body (physical) well-being in your tracking.",
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = goalDomain == HabitGoalDomain.MIND,
                                onClick = { goalDomain = HabitGoalDomain.MIND },
                                label = { Text("Mind") },
                            )
                            FilterChip(
                                selected = goalDomain == HabitGoalDomain.BODY,
                                onClick = { goalDomain = HabitGoalDomain.BODY },
                                label = { Text("Body") },
                            )
                        }
                    }
                    else -> {
                        Text("Go back and choose a habit kind.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            3 -> {
                when {
                    valence == HabitValence.POSITIVE && (positiveKind == KIND_WORKOUT || positiveKind == KIND_WEIGH_IN) -> {
                        Text("Name (optional)", style = MaterialTheme.typography.labelLarge)
                        SectionDescription(
                            "Defaults to «$DEFAULT_TITLE_WORKOUT» or «$DEFAULT_TITLE_WEIGH_IN». Change only if you want a different label in the app.",
                        )
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Display name") },
                            placeholder = {
                                Text(
                                    if (positiveKind == KIND_WEIGH_IN) DEFAULT_TITLE_WEIGH_IN else DEFAULT_TITLE_WORKOUT,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                        )
                    }
                    valence == HabitValence.POSITIVE && positiveKind == KIND_UNSCHEDULED -> {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(16.dp))
                        PositiveExampleChips { line -> title = line }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                        )
                    }
                    valence == HabitValence.NEGATIVE -> {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(16.dp))
                        NegativeExampleChips { line -> title = line }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                        )
                    }
                    else -> {
                        Text("Go back and choose a habit kind.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            4 -> {
                Text("Review", style = MaterialTheme.typography.titleMedium)
                saveError?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(resolvedTitle.ifBlank { "—" }, style = MaterialTheme.typography.headlineSmall)
                Text(
                    buildString {
                        append(valence?.name ?: "")
                        when (valence) {
                            HabitValence.NEGATIVE -> append(" · Unscheduled · Incremental")
                            HabitValence.POSITIVE -> when (positiveKind) {
                                KIND_WORKOUT -> append(" · Scheduled workout")
                                KIND_WEIGH_IN -> append(" · Scheduled weigh-in (lbs)")
                                KIND_UNSCHEDULED -> append(" · Other · Incremental")
                                else -> {}
                            }
                            null -> {}
                        }
                        val gd = when {
                            positiveKind == KIND_WORKOUT -> HabitGoalDomain.BODY
                            positiveKind == KIND_WEIGH_IN -> HabitGoalDomain.MIND
                            else -> goalDomain
                        }
                        gd?.let { append(" · ${it.name}") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    when {
                        step == 1 -> onDone()
                        else -> step--
                    }
                },
                enabled = !saving,
            ) {
                Text(if (step == 1) "Cancel" else "Back")
            }
            Button(
                onClick = {
                    when (step) {
                        1 -> {
                            if (!validateStep1(valence, positiveKind, dayActive)) return@Button
                            step = 2
                        }
                        2 -> {
                            if (!validateStepGoalDomain(valence, positiveKind, goalDomain)) return@Button
                            step = 3
                        }
                        3 -> {
                            if (!validateStepName(valence, positiveKind, title)) return@Button
                            step = 4
                        }
                        4 -> {
                            saveError = null
                            val input = buildInput(
                                resolvedTitle,
                                valence!!,
                                notes,
                                positiveKind,
                                goalDomain,
                                dayActive,
                            ) ?: return@Button
                            saving = true
                            scope.launch {
                                try {
                                    repo.createHabit(input)
                                    onDone()
                                } catch (e: IllegalStateException) {
                                    saveError = e.message ?: "Could not save habit."
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    }
                },
                enabled = !saving,
            ) {
                Text(
                    when (step) {
                        4 -> "Save"
                        else -> "Next"
                    },
                )
            }
        }
    }
}

private fun effectiveHabitTitle(
    title: String,
    valence: HabitValence?,
    positiveKind: String?,
): String {
    val t = title.trim()
    if (t.isNotEmpty()) return t
    if (valence == HabitValence.POSITIVE && positiveKind == KIND_WORKOUT) return DEFAULT_TITLE_WORKOUT
    if (valence == HabitValence.POSITIVE && positiveKind == KIND_WEIGH_IN) return DEFAULT_TITLE_WEIGH_IN
    return ""
}

private fun validateStep1(
    valence: HabitValence?,
    positiveKind: String?,
    dayActive: Map<Int, Boolean>,
): Boolean {
    if (valence == null) return false
    if (valence == HabitValence.NEGATIVE) return true
    if (positiveKind == null) return false
    if (positiveKind == KIND_WORKOUT || positiveKind == KIND_WEIGH_IN) {
        return (1..7).any { dayActive[it] == true }
    }
    return positiveKind == KIND_UNSCHEDULED
}

private fun validateStepGoalDomain(
    valence: HabitValence?,
    positiveKind: String?,
    goalDomain: HabitGoalDomain?,
): Boolean {
    if (valence == null) return false
    if (valence == HabitValence.POSITIVE && (positiveKind == KIND_WORKOUT || positiveKind == KIND_WEIGH_IN)) {
        return true
    }
    return goalDomain != null
}

private fun validateStepName(
    valence: HabitValence?,
    positiveKind: String?,
    title: String,
): Boolean {
    if (valence == null) return false
    if (valence == HabitValence.POSITIVE && (positiveKind == KIND_WORKOUT || positiveKind == KIND_WEIGH_IN)) {
        return true
    }
    return effectiveHabitTitle(title, valence, positiveKind).isNotEmpty()
}

private fun buildInput(
    title: String,
    valence: HabitValence,
    notes: String,
    positiveKind: String?,
    goalDomain: HabitGoalDomain?,
    dayActive: Map<Int, Boolean>,
): NewHabitInput? {
    return when (valence) {
        HabitValence.NEGATIVE -> NewHabitInput(
            title = title,
            valence = valence,
            notes = notes.takeIf { it.isNotBlank() },
            isScheduled = false,
            trackingMode = TrackingMode.COUNT,
            unit = null,
            recurrence = null,
            goalDomain = goalDomain ?: return null,
        )
        HabitValence.POSITIVE -> when (positiveKind) {
            KIND_WORKOUT, KIND_WEIGH_IN -> {
                val rules = ArrayList<RecurrenceDayRule>()
                for (iso in 1..7) {
                    if (dayActive[iso] != true) continue
                    rules.add(
                        RecurrenceDayRule(
                            dayOfWeek = iso,
                            categories = listOf("GENERAL"),
                            defaultNotes = null,
                        ),
                    )
                }
                if (rules.isEmpty()) return null
                val tm = if (positiveKind == KIND_WEIGH_IN) TrackingMode.WEIGHT else null
                NewHabitInput(
                    title = title,
                    valence = valence,
                    notes = notes.takeIf { it.isNotBlank() },
                    isScheduled = true,
                    trackingMode = tm,
                    unit = null,
                    recurrence = rules.sortedBy { it.dayOfWeek },
                    goalDomain = if (positiveKind == KIND_WEIGH_IN) HabitGoalDomain.MIND else HabitGoalDomain.BODY,
                )
            }
            KIND_UNSCHEDULED -> NewHabitInput(
                title = title,
                valence = valence,
                notes = notes.takeIf { it.isNotBlank() },
                isScheduled = false,
                trackingMode = TrackingMode.COUNT,
                unit = null,
                recurrence = null,
                goalDomain = goalDomain ?: return null,
            )
            else -> null
        }
    }
}
