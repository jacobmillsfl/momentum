package com.momentum.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.momentum.app.data.repo.CalendarDayEmoji
import com.momentum.app.data.repo.CalendarDayState
import com.momentum.app.data.repo.StreakSnapshot
import com.momentum.app.data.repo.HabitTrendDay
import com.momentum.app.data.repo.WeightPoint
import com.momentum.app.ui.LocalRepository
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val monthDayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

private val unscheduledAxisDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

private val weekDaysSunFirst: List<DayOfWeek> = listOf(
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
)

@Composable
fun ProgressScreen() {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var streak by remember { mutableStateOf<StreakSnapshot?>(null) }
    var calendarDays by remember { mutableStateOf<List<CalendarDayState>>(emptyList()) }
    var weightPoints by remember { mutableStateOf<List<WeightPoint>>(emptyList()) }
    var hasAnyWeighIn by remember { mutableStateOf(false) }
    var weightWindowDays by remember { mutableIntStateOf(7) }
    var windowDays by remember { mutableIntStateOf(7) }
    var habitTrend by remember { mutableStateOf<List<HabitTrendDay>>(emptyList()) }
    var pastCalendarDay by remember { mutableStateOf<LocalDate?>(null) }
    var targetWeightLbs by remember { mutableStateOf<Double?>(null) }
    var startingWeightLbs by remember { mutableStateOf<Double?>(null) }

    fun refresh() {
        scope.launch {
            streak = repo.streakSnapshot()
            calendarDays = repo.scheduledCalendarFiveWeeks()
            hasAnyWeighIn = repo.weightHistory().isNotEmpty()
            weightPoints = repo.weightHistoryForWindow(weightWindowDays)
            habitTrend = repo.habitTrendDaily(windowDays)
            targetWeightLbs = repo.targetWeightLbs()
            startingWeightLbs = repo.startingWeightLbs()
        }
    }

    LaunchedEffect(windowDays, weightWindowDays) {
        streak = repo.streakSnapshot()
        calendarDays = repo.scheduledCalendarFiveWeeks()
        hasAnyWeighIn = repo.weightHistory().isNotEmpty()
        weightPoints = repo.weightHistoryForWindow(weightWindowDays)
        habitTrend = repo.habitTrendDaily(windowDays)
        targetWeightLbs = repo.targetWeightLbs()
        startingWeightLbs = repo.startingWeightLbs()
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Progress", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        val snap = streak
        if (snap != null) {
            Text(
                "Streak ${snap.current} days (perfect ${snap.perfectCurrent}) · Best ${snap.longest} / ${snap.perfectLongest} · Freezes ${snap.freezesRemaining}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(20.dp))

        Text("Scheduled habits", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CalendarLegendRow("🔥", "Only good")
                CalendarLegendRow("📈", "Good beats bad")
                CalendarLegendRow("📉", "Bad beats good")
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CalendarLegendRow("❌", "Only bad")
                CalendarLegendRow("⚖", "Good = bad")
                CalendarLegendRow("·", "No activity")
            }
        }
        Spacer(Modifier.height(8.dp))
        CalendarLegendRow("💪", "Workout on your schedule (future days only)")
        Spacer(Modifier.height(12.dp))
        if (calendarDays.isEmpty()) {
            Text("No data yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                "Tap a past day to insert or edit habit entries.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FiveWeekCalendar(
                days = calendarDays,
                onPastDateClick = { pastCalendarDay = it },
            )
        }

        Spacer(Modifier.height(28.dp))
        Text("Weight (lbs)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (!hasAnyWeighIn) {
            Text(
                "Complete a scheduled weigh-in on Today to chart weight here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    7 to "7 d",
                    30 to "30 d",
                    90 to "90 d",
                    365 to "1y",
                ).forEach { (d, label) ->
                    TextButton(onClick = { weightWindowDays = d }) {
                        Text(
                            label,
                            color = if (weightWindowDays == d) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (weightPoints.isEmpty()) {
                Text(
                    "No weigh-ins in this range.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                WeightLineChart(
                    points = weightPoints,
                    targetWeightLbs = targetWeightLbs,
                    startingWeightLbs = startingWeightLbs,
                    windowDays = weightWindowDays,
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("Habit Trends", style = MaterialTheme.typography.titleMedium)
        Text(
            "Daily score: good habits minus bad (unscheduled counts + 1 per completed scheduled session). " +
                "Solid line is cumulative momentum; dashed line is a linear trend over that window.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                7 to "7 d",
                30 to "30 d",
                90 to "90 d",
                365 to "1y",
            ).forEach { (d, label) ->
                TextButton(onClick = { windowDays = d }) {
                    Text(
                        label,
                        color = if (windowDays == d) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (habitTrend.isEmpty()) {
            Text(
                "No data in this window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            HabitMomentumChart(
                days = habitTrend,
                windowLabel = if (windowDays == 365) "1y (365 d)" else "$windowDays d",
            )
        }
    }

    CalendarPastDayFlow(
        date = pastCalendarDay,
        onDismiss = { pastCalendarDay = null },
        onDataChanged = { refresh() },
    )
}

@Composable
private fun FiveWeekCalendar(
    days: List<CalendarDayState>,
    onPastDateClick: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val first = days.first().date
    val last = days.last().date
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "${first.format(monthDayFmt)} – ${last.format(monthDayFmt)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                weekDaysSunFirst.forEach { dow ->
                    Text(
                        dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            days.chunked(7).forEach { week ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    week.forEach { cell ->
                        val isPast = cell.date.isBefore(today)
                        DayCell(
                            cell = cell,
                            isToday = cell.date == today,
                            onClick = if (isPast) {
                                { onPastDateClick(cell.date) }
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun CalendarLegendRow(emoji: String, caption: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, style = MaterialTheme.typography.bodyMedium)
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayCell(
    cell: CalendarDayState,
    isToday: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val bg = when {
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val mod = modifier
        .padding(2.dp)
        .then(
            if (isToday) {
                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
            } else {
                Modifier
            },
        )
        .background(color = bg, shape = shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(vertical = 6.dp)
    Column(
        modifier = mod,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            cell.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        val emoji = when (cell.emoji) {
            CalendarDayEmoji.FIRE -> "🔥"
            CalendarDayEmoji.TREND_UP -> "📈"
            CalendarDayEmoji.TREND_DOWN -> "📉"
            CalendarDayEmoji.X_MARK -> "❌"
            CalendarDayEmoji.TIE -> "⚖"
            CalendarDayEmoji.REST -> "·"
        }
        Row(
            modifier = Modifier.height(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (cell.futureExerciseScheduled) {
                Spacer(Modifier.width(2.dp))
                Text("💪", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun WeightLineChart(
    points: List<WeightPoint>,
    targetWeightLbs: Double?,
    startingWeightLbs: Double?,
    windowDays: Int,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val targetColor = MaterialTheme.colorScheme.tertiary
    val startingColor = Color(0xFFC62828)
    val refYs = listOfNotNull(targetWeightLbs, startingWeightLbs)
    val maxY = (points.map { it.value } + refYs).maxOrNull() ?: 0.0
    val minY = (points.map { it.value } + refYs).minOrNull() ?: 0.0
    val span = (maxY - minY).takeIf { it > 1e-6 } ?: 1.0
    val midY = (maxY + minY) / 2.0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier
                        .width(44.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        "%.1f".format(maxY),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "%.1f".format(midY),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "%.1f".format(minY),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 8.dp),
                ) {
                    val w = size.width
                    val h = size.height
                    val n = points.size
                    fun yAt(v: Double) = (h - ((v - minY) / span).toFloat() * h).coerceIn(0f, h)
                    fun drawRefLine(yv: Double?, color: Color) {
                        if (yv != null && yv in minY..maxY) {
                            val yy = yAt(yv)
                            drawLine(
                                color = color,
                                start = Offset(0f, yy),
                                end = Offset(w, yy),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f),
                            )
                        }
                    }
                    if (n < 2) {
                        if (n == 1) {
                            drawRefLine(targetWeightLbs, targetColor)
                            drawRefLine(startingWeightLbs, startingColor)
                            val y = yAt(points[0].value)
                            drawCircle(lineColor, 4.dp.toPx(), Offset(w / 2, y))
                        }
                        return@Canvas
                    }
                    fun xAt(i: Int) = (i.toFloat() / (n - 1)) * w
                    drawRefLine(targetWeightLbs, targetColor)
                    drawRefLine(startingWeightLbs, startingColor)
                    val path = Path()
                    points.forEachIndexed { i, p ->
                        val x = xAt(i)
                        val y = yAt(p.value)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    val strokeW = 3.dp.toPx()
                    drawPath(
                        path,
                        color = lineColor,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round),
                    )
                }
            }
            if (points.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 48.dp),
                    horizontalArrangement = if (points.size == 1) Arrangement.Center else Arrangement.SpaceBetween,
                ) {
                    Text(
                        points.first().date.format(unscheduledAxisDateFmt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (points.size > 2) {
                        Text(
                            points[points.size / 2].date.format(unscheduledAxisDateFmt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (points.size > 1) {
                        Text(
                            points.last().date.format(unscheduledAxisDateFmt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val wLabel = if (windowDays == 365) "1y (365 d)" else "$windowDays d"
                Text(
                    "$wLabel · oldest left → newest right",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 48.dp, top = 2.dp),
                )
            }
            val refParts = buildList {
                startingWeightLbs?.let { add("Start ${"%.1f".format(it)}") }
                targetWeightLbs?.let { add("Target ${"%.1f".format(it)}") }
            }
            Text(
                "Range ${"%.1f".format(minY)}–${"%.1f".format(maxY)} lbs" +
                    if (refParts.isEmpty()) "" else " · " + refParts.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun cumulativeMomentum(nets: List<Double>): List<Double> {
    if (nets.isEmpty()) return emptyList()
    val out = ArrayList<Double>(nets.size)
    var run = 0.0
    for (n in nets) {
        run += n
        out.add(run)
    }
    return out
}

/** Least-squares y = a + b·x for x = 0..n-1. Returns (intercept a, slope b). */
private fun linearRegressionOnSeries(y: List<Double>): Pair<Double, Double> {
    val n = y.size
    if (n == 0) return 0.0 to 0.0
    if (n == 1) return y[0] to 0.0
    val meanX = (n - 1) / 2.0
    val meanY = y.sum() / n
    var num = 0.0
    var den = 0.0
    for (i in y.indices) {
        val dx = i - meanX
        num += dx * (y[i] - meanY)
        den += dx * dx
    }
    if (den < 1e-12) return meanY to 0.0
    val b = num / den
    val a = meanY - b * meanX
    return a to b
}

@Composable
private fun HabitMomentumChart(
    days: List<HabitTrendDay>,
    windowLabel: String,
) {
    val momentumColor = MaterialTheme.colorScheme.primary
    val trendColor = MaterialTheme.colorScheme.tertiary
    val zeroLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val nets = remember(days) { days.map { it.net } }
    val cumulative = remember(days) { cumulativeMomentum(nets) }
    val (regA, regB) = remember(cumulative) { linearRegressionOnSeries(cumulative) }
    val n = days.size
    val regEndY = if (n > 0) regA + regB * (n - 1) else regA
    val maxY = listOfNotNull(
        cumulative.maxOrNull(),
        cumulative.minOrNull(),
        0.0,
        regA,
        regEndY,
    ).maxOrNull() ?: 0.0
    val minY = listOfNotNull(
        cumulative.maxOrNull(),
        cumulative.minOrNull(),
        0.0,
        regA,
        regEndY,
    ).minOrNull() ?: 0.0
    val span = (maxY - minY).takeIf { it > 1e-6 } ?: 1.0
    val midY = (maxY + minY) / 2.0
    val trendSlopeLabel = when {
        n < 2 -> "Trend needs 2+ days"
        kotlin.math.abs(regB) < 1e-6 -> "Trend ~ flat"
        else -> "Trend ~ ${if (regB > 0) "+" else ""}${"%.2f".format(regB)} momentum/day"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier
                        .width(44.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        "%.0f".format(maxY),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "%.0f".format(midY),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "%.0f".format(minY),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 8.dp),
                ) {
                    val w = size.width
                    val h = size.height
                    fun yAt(v: Double) = (h - ((v - minY) / span).toFloat() * h).coerceIn(0f, h)
                    fun xAt(i: Int) = if (n <= 1) w / 2f else (i.toFloat() / (n - 1)) * w
                    if (0.0 in minY..maxY) {
                        val y0 = yAt(0.0)
                        drawLine(
                            color = zeroLineColor,
                            start = Offset(0f, y0),
                            end = Offset(w, y0),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    if (n >= 2) {
                        val yStart = yAt(regA)
                        val yEnd = yAt(regA + regB * (n - 1))
                        drawLine(
                            color = trendColor,
                            start = Offset(0f, yStart),
                            end = Offset(w, yEnd),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                        )
                    }
                    if (n == 1) {
                        drawCircle(momentumColor, 5.dp.toPx(), Offset(w / 2, yAt(cumulative[0])))
                        return@Canvas
                    }
                    val path = Path()
                    cumulative.forEachIndexed { i, v ->
                        val x = xAt(i)
                        val y = yAt(v)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path,
                        color = momentumColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 48.dp),
                horizontalArrangement = if (days.size == 1) Arrangement.Center else Arrangement.SpaceBetween,
            ) {
                Text(
                    days.first().date.format(unscheduledAxisDateFmt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (days.size > 2) {
                    Text(
                        days[days.size / 2].date.format(unscheduledAxisDateFmt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (days.size > 1) {
                    Text(
                        days.last().date.format(unscheduledAxisDateFmt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "$windowLabel · oldest left → newest right",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 48.dp, top = 2.dp),
            )
            Text(
                trendSlopeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("— Cumulative momentum", color = momentumColor, style = MaterialTheme.typography.labelSmall)
                Text("— Linear trend", color = trendColor, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
