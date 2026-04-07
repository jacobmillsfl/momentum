package com.momentum.app.data.repo

import androidx.room.withTransaction
import com.momentum.app.data.backup.MOMENTUM_BACKUP_SCHEMA_VERSION
import com.momentum.app.data.backup.MomentumBackupV1
import com.momentum.app.data.backup.toBackupRow
import com.momentum.app.data.backup.toEntity
import com.momentum.app.data.local.MomentumDatabase
import com.momentum.app.data.local.entity.HabitEntity
import com.momentum.app.data.local.entity.KvEntity
import com.momentum.app.data.local.entity.LogEntity
import com.momentum.app.data.local.entity.SessionEntity
import com.momentum.app.data.local.entity.TaskEntity
import com.momentum.app.data.streak.StreakHelper
import com.momentum.app.data.streak.computeGoodBadForDay
import com.momentum.app.domain.HabitValence
import com.momentum.app.domain.RecurrenceDayRule
import com.momentum.app.domain.SESSION_GENERATION_WEEKS
import com.momentum.app.domain.SessionStatus
import com.momentum.app.domain.TrackingMode
import com.momentum.app.domain.buildOccurrencesFromToday
import com.momentum.app.domain.endOfDayMillis
import com.momentum.app.domain.localDateToStartMillis
import com.momentum.app.domain.millisToLocalDate
import com.momentum.app.domain.startOfDayMillis
import com.momentum.app.domain.startOfDayUtcMillis
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

data class NewHabitInput(
    val title: String,
    val valence: HabitValence,
    val notes: String?,
    val isScheduled: Boolean,
    val trackingMode: TrackingMode?,
    val unit: String?,
    val recurrence: List<RecurrenceDayRule>?,
)

data class SessionWithHabit(
    val session: SessionEntity,
    val habitTitle: String,
    val habitTrackingMode: String?,
    val habitUnit: String?,
)

data class UnscheduledRow(
    val habit: HabitEntity,
    val todayDisplay: String,
)

class MomentumRepository(
    private val database: MomentumDatabase,
) {
    private val habitDao = database.habitDao()
    private val sessionDao = database.sessionDao()
    private val logDao = database.logDao()
    private val kvDao = database.kvDao()
    private val taskDao = database.taskDao()
    private val json = Json { ignoreUnknownKeys = true }
    private val backupJson = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
    private val streakHelper = StreakHelper(sessionDao, habitDao, logDao, kvDao)
    private val zone: TimeZone get() = TimeZone.getDefault()

    suspend fun seedKvDefaults() {
        streakHelper.ensureFreezeMonthReset()
        val monthKey = java.time.YearMonth.now().toString()
        val defaults = listOf(
            KvKeys.STREAK_CURRENT to "0",
            KvKeys.STREAK_LONGEST to "0",
            KvKeys.STREAK_LAST_ACTIVE to "",
            KvKeys.FREEZES_ALLOWED to "3",
            KvKeys.FREEZES_USED to "0",
            KvKeys.FREEZES_MONTH to monthKey,
            KvKeys.FREEZE_LAST_APPLIED_DAY to "",
            KvKeys.THEME_MODE to "system",
            KvKeys.NOTIFICATIONS_ENABLED to "false",
            KvKeys.NOTIFICATION_TIME to "09:00",
            KvKeys.STREAK_PERFECT_CURRENT to "0",
            KvKeys.STREAK_PERFECT_LONGEST to "0",
            KvKeys.STREAK_PERFECT_LAST_ACTIVE to "",
            KvKeys.TARGET_WEIGHT_LBS to "",
            KvKeys.STARTING_WEIGHT_LBS to "",
        )
        for ((k, v) in defaults) {
            if (kvDao.get(k) == null) {
                kvDao.upsert(KvEntity(k, v))
            }
        }
    }

    suspend fun getKv(key: String): String? = kvDao.get(key)

    suspend fun setKv(key: String, value: String) {
        kvDao.upsert(KvEntity(key, value))
    }

    suspend fun listActiveHabits(): List<HabitEntity> = habitDao.listActive()

    suspend fun habitById(habitId: String): HabitEntity? = habitDao.getById(habitId)

    /** Sessions for [habitId] in the last [days] calendar days (including today). */
    suspend fun sessionsForHabitWindow(habitId: String, days: Int): List<SessionEntity> {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong() - 1)
        val startMs = localDateToStartMillis(startDate, zone)
        val endMs = endOfDayMillis(localDateToStartMillis(endDate, zone), zone)
        return sessionDao.sessionsForHabitInRange(habitId, startMs, endMs)
    }

    /** Logs for [habitId] in the last [days] calendar days (including today). */
    suspend fun logsForHabitWindow(habitId: String, days: Int): List<LogEntity> {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong() - 1)
        val startMs = localDateToStartMillis(startDate, zone)
        val endMs = endOfDayMillis(localDateToStartMillis(endDate, zone), zone)
        return logDao.logsForHabitInRange(habitId, startMs, endMs)
    }

    /** Non-null message if this scheduled habit would duplicate an existing one. */
    suspend fun duplicateScheduledHabitMessage(input: NewHabitInput): String? {
        if (!input.isScheduled || input.recurrence.isNullOrEmpty()) return null
        val active = habitDao.listActive()
        return when (input.trackingMode) {
            TrackingMode.WEIGHT -> {
                if (active.any { it.isScheduled && it.trackingMode == TrackingMode.WEIGHT.name }) {
                    "You already have a scheduled weigh-in habit. Archive it before adding another."
                } else {
                    null
                }
            }
            else -> {
                if (active.any { it.isScheduled && it.trackingMode != TrackingMode.WEIGHT.name }) {
                    "You already have a scheduled workout habit. Archive it before adding another."
                } else {
                    null
                }
            }
        }
    }

    suspend fun createHabit(input: NewHabitInput): String {
        duplicateScheduledHabitMessage(input)?.let { throw IllegalStateException(it) }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val recurrenceJson = if (input.isScheduled && !input.recurrence.isNullOrEmpty()) {
            json.encodeToString(ListSerializer(RecurrenceDayRule.serializer()), input.recurrence)
        } else {
            null
        }
        val habit = HabitEntity(
            id = id,
            title = input.title.trim(),
            valence = input.valence.name,
            isScheduled = input.isScheduled,
            trackingMode = input.trackingMode?.name,
            unit = when (input.trackingMode) {
                TrackingMode.WEIGHT -> "lbs"
                else -> input.unit?.trim()?.takeIf { it.isNotEmpty() }
            },
            recurrenceJson = recurrenceJson,
            notes = input.notes?.trim()?.takeIf { it.isNotEmpty() },
            archivedAt = null,
            createdAt = now,
        )
        database.withTransaction {
            habitDao.insert(habit)
            if (input.isScheduled && !input.recurrence.isNullOrEmpty()) {
                val occ = buildOccurrencesFromToday(input.recurrence, SESSION_GENERATION_WEEKS)
                for (o in occ) {
                    val exists = sessionDao.findByHabitAndDay(id, o.dayStartMs)
                    if (exists != null) continue
                    val categoriesJson = json.encodeToString(
                        ListSerializer(serializer<String>()),
                        o.rule.categories,
                    )
                    sessionDao.insert(
                        SessionEntity(
                            id = UUID.randomUUID().toString(),
                            habitId = id,
                            scheduledAt = o.dayStartMs,
                            completedAt = null,
                            status = SessionStatus.PLANNED.name,
                            categoriesJson = categoriesJson,
                            notes = o.rule.defaultNotes?.trim()?.takeIf { it.isNotEmpty() },
                            completionValue = null,
                        ),
                    )
                }
            }
        }
        return id
    }

    private suspend fun dayRange(): Pair<Long, Long> {
        val start = startOfDayUtcMillis(zone)
        val cal = Calendar.getInstance(zone)
        cal.timeInMillis = start
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return start to cal.timeInMillis
    }

    private fun dayRangeForDate(date: LocalDate): Pair<Long, Long> {
        val start = localDateToStartMillis(date, zone)
        val end = endOfDayMillis(start, zone)
        return start to end
    }

    /** Noon local time on [date], for backfilled log rows. */
    private fun logTimestampForDay(date: LocalDate): Long =
        localDateToStartMillis(date, zone) + 3_600_000L

    suspend fun sessionsForDate(date: LocalDate): List<SessionWithHabit> {
        val (start, end) = dayRangeForDate(date)
        val sessions = sessionDao.sessionsBetween(start, end)
        return sessions.mapNotNull { s ->
            val h = habitDao.getById(s.habitId) ?: return@mapNotNull null
            SessionWithHabit(s, h.title, h.trackingMode, h.unit)
        }
    }

    private suspend fun unscheduledDayTotal(habitId: String, start: Long, end: Long): Double {
        val logs = logDao.logsForHabitInRange(habitId, start, end)
        return logs.sumOf { log ->
            log.numericValue ?: if (log.booleanValue == 1) 1.0 else 0.0
        }
    }

    suspend fun unscheduledForDate(date: LocalDate): List<UnscheduledRow> {
        val habits = habitDao.listActive().filter { !it.isScheduled }
        val (start, end) = dayRangeForDate(date)
        return habits.map { h ->
            val sum = unscheduledDayTotal(h.id, start, end)
            UnscheduledRow(h, sum.toInt().toString())
        }
    }

    suspend fun findSessionForHabitOnDate(habitId: String, date: LocalDate): SessionEntity? {
        val start = localDateToStartMillis(date, zone)
        return sessionDao.findByHabitAndDay(habitId, start)
    }

    /**
     * Ensures a [SessionEntity] exists for this scheduled habit on [date] (e.g. calendar backfill).
     * Creates a PLANNED session using the recurrence rule for that weekday, or empty categories if none match.
     */
    suspend fun ensureSessionForHabitOnDate(habitId: String, date: LocalDate): SessionEntity? {
        val dayStart = localDateToStartMillis(date, zone)
        sessionDao.findByHabitAndDay(habitId, dayStart)?.let { return it }
        val h = habitDao.getById(habitId) ?: return null
        if (!h.isScheduled) return null
        val recurrenceJson = h.recurrenceJson ?: return null
        val rules = try {
            json.decodeFromString(ListSerializer(RecurrenceDayRule.serializer()), recurrenceJson)
        } catch (_: Exception) {
            emptyList()
        }
        val isoDow = date.dayOfWeek.value
        val rule = rules.find { it.dayOfWeek == isoDow } ?: rules.firstOrNull()
        val categoriesJson = json.encodeToString(
            ListSerializer(serializer<String>()),
            rule?.categories ?: emptyList(),
        )
        val notes = rule?.defaultNotes?.trim()?.takeIf { it.isNotEmpty() }
        val entity = SessionEntity(
            id = UUID.randomUUID().toString(),
            habitId = habitId,
            scheduledAt = dayStart,
            completedAt = null,
            status = SessionStatus.PLANNED.name,
            categoriesJson = categoriesJson,
            notes = notes,
            completionValue = null,
        )
        database.withTransaction {
            if (sessionDao.findByHabitAndDay(habitId, dayStart) != null) return@withTransaction
            sessionDao.insert(entity)
        }
        return sessionDao.findByHabitAndDay(habitId, dayStart)
    }

    suspend fun sessionsForToday(): List<SessionWithHabit> {
        val (start, end) = dayRange()
        val sessions = sessionDao.sessionsBetween(start, end)
        return sessions.mapNotNull { s ->
            val h = habitDao.getById(s.habitId) ?: return@mapNotNull null
            SessionWithHabit(s, h.title, h.trackingMode, h.unit)
        }
    }

    suspend fun unscheduledForToday(): List<UnscheduledRow> {
        val habits = habitDao.listActive().filter { !it.isScheduled }
        val (start, end) = dayRange()
        return habits.map { h ->
            val sum = unscheduledDayTotal(h.id, start, end)
            UnscheduledRow(h, sum.toInt().toString())
        }
    }

    suspend fun markSession(sessionId: String, status: SessionStatus, completionValue: Double? = null) {
        val s = sessionDao.getById(sessionId) ?: return
        val h = habitDao.getById(s.habitId) ?: return

        if (s.status == SessionStatus.COMPLETED.name) {
            if (h.trackingMode == TrackingMode.WEIGHT.name &&
                status == SessionStatus.COMPLETED &&
                completionValue != null &&
                completionValue > 0.0
            ) {
                val updated = s.copy(
                    completionValue = completionValue,
                    completedAt = System.currentTimeMillis(),
                )
                sessionDao.update(updated)
                val dayStart = startOfDayMillis(s.scheduledAt, zone)
                streakHelper.onDaySettledForMillis(dayStart)
            }
            return
        }

        if (status == SessionStatus.COMPLETED && h.trackingMode == TrackingMode.WEIGHT.name) {
            if (completionValue == null || completionValue <= 0.0) return
        }
        val updated = when (status) {
            SessionStatus.COMPLETED -> s.copy(
                status = status.name,
                completedAt = System.currentTimeMillis(),
                completionValue = if (h.trackingMode == TrackingMode.WEIGHT.name) completionValue else null,
            )
            SessionStatus.MISSED -> s.copy(status = status.name, completedAt = null, completionValue = null)
            SessionStatus.PLANNED -> s.copy(status = status.name, completedAt = null, completionValue = null)
        }
        sessionDao.update(updated)
        val dayStart = startOfDayMillis(s.scheduledAt, zone)
        streakHelper.onDaySettledForMillis(dayStart)
    }

    /**
     * Clears a completed or legacy missed session back to planned: status, weight, completion time,
     * and all task checkboxes. Implied “missed” is represented by leaving a session open (planned).
     */
    suspend fun resetSession(sessionId: String) {
        val s = sessionDao.getById(sessionId) ?: return
        if (s.status != SessionStatus.COMPLETED.name && s.status != SessionStatus.MISSED.name) return
        for (t in taskDao.tasksForSession(sessionId)) {
            if (t.completed) {
                taskDao.update(t.copy(completed = false))
            }
        }
        sessionDao.update(
            s.copy(
                status = SessionStatus.PLANNED.name,
                completedAt = null,
                completionValue = null,
            ),
        )
        streakHelper.onDaySettledForMillis(startOfDayMillis(s.scheduledAt, zone))
    }

    suspend fun updateSessionNotes(sessionId: String, notes: String?) {
        val s = sessionDao.getById(sessionId) ?: return
        sessionDao.update(s.copy(notes = notes?.trim()?.takeIf { it.isNotEmpty() }))
    }

    suspend fun tasksForSession(sessionId: String) = taskDao.tasksForSession(sessionId)

    suspend fun addTask(sessionId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val existing = taskDao.tasksForSession(sessionId)
        val order = (existing.maxOfOrNull { it.sortOrder } ?: 0) + 1
        taskDao.insert(
            TaskEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                title = trimmed,
                completed = false,
                sortOrder = order,
                notes = null,
            ),
        )
    }

    suspend fun setTaskCompleted(taskId: String, completed: Boolean) {
        val t = taskDao.getById(taskId) ?: return
        taskDao.update(t.copy(completed = completed))
    }

    suspend fun archiveHabit(habitId: String) {
        val h = habitDao.getById(habitId) ?: return
        habitDao.update(h.copy(archivedAt = System.currentTimeMillis()))
    }

    suspend fun streakSnapshot(): StreakSnapshot {
        streakHelper.ensureFreezeMonthReset()
        val current = kvDao.get(KvKeys.STREAK_CURRENT)?.toIntOrNull() ?: 0
        val longest = kvDao.get(KvKeys.STREAK_LONGEST)?.toIntOrNull() ?: 0
        val perfectCurrent = kvDao.get(KvKeys.STREAK_PERFECT_CURRENT)?.toIntOrNull() ?: 0
        val perfectLongest = kvDao.get(KvKeys.STREAK_PERFECT_LONGEST)?.toIntOrNull() ?: 0
        val allowed = (kvDao.get(KvKeys.FREEZES_ALLOWED)?.toIntOrNull() ?: 3)
            .coerceIn(0, KvKeys.MAX_STREAK_FREEZES_PER_MONTH)
        val used = kvDao.get(KvKeys.FREEZES_USED)?.toIntOrNull() ?: 0
        return StreakSnapshot(
            current = current,
            longest = longest,
            perfectCurrent = perfectCurrent,
            perfectLongest = perfectLongest,
            freezesRemaining = (allowed - used).coerceAtLeast(0),
        )
    }

    /** Optional target weight in lbs for the Progress chart; null if unset or invalid. */
    suspend fun targetWeightLbs(): Double? =
        kvDao.get(KvKeys.TARGET_WEIGHT_LBS)?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    /** Optional starting weight in lbs; null if unset or invalid. */
    suspend fun startingWeightLbs(): Double? =
        kvDao.get(KvKeys.STARTING_WEIGHT_LBS)?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    /** Logged sessions and unscheduled activity for a calendar day (overview). */
    suspend fun dayTrackingSummary(date: LocalDate): DayTrackingSummary {
        val sessions = sessionsForDate(date)
        val scheduled = sessions.map { row ->
            val tasks = taskDao.tasksForSession(row.session.id)
            val taskLines =
                if (row.habitTrackingMode == TrackingMode.WEIGHT.name) {
                    emptyList()
                } else {
                    tasks.map { t ->
                        "${t.title} ${if (t.completed) "✓" else "○"}"
                    }
                }
            val wl = if (row.habitTrackingMode == TrackingMode.WEIGHT.name &&
                row.session.status == SessionStatus.COMPLETED.name
            ) {
                row.session.completionValue
            } else {
                null
            }
            ScheduledSessionDaySummary(
                habitTitle = row.habitTitle,
                trackingMode = row.habitTrackingMode,
                status = row.session.status,
                weightLbs = wl,
                taskLines = taskLines,
            )
        }
        val unrows = unscheduledForDate(date)
        val unscheduled = unrows.mapNotNull { u ->
            val n = u.todayDisplay.toIntOrNull() ?: 0
            if (n <= 0) return@mapNotNull null
            val vLabel = when (u.habit.valence) {
                HabitValence.POSITIVE.name -> "Good"
                else -> "Bad"
            }
            UnscheduledHabitDaySummary(
                habitTitle = u.habit.title,
                valenceLabel = vLabel,
                summaryText = "Count $n",
            )
        }
        return DayTrackingSummary(scheduled = scheduled, unscheduled = unscheduled)
    }

    /** Sunday-based week: weekOffset 0 = current week. */
    suspend fun weekTilesForHabit(habitId: String, weekOffset: Int): List<HabitWeekDay> {
        val today = LocalDate.now()
        val sunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            .minusWeeks(weekOffset.toLong())
        val habit = habitDao.getById(habitId) ?: return emptyList()
        if (!habit.isScheduled) return emptyList()
        return (0 until 7).map { d ->
            val date = sunday.plusDays(d.toLong())
            val start = localDateToStartMillis(date, zone)
            val end = endOfDayMillis(start, zone)
            val sessions = sessionDao.sessionsForHabitInRange(habitId, start, end)
            val tile = when {
                sessions.isEmpty() -> SessionDayTile.NOT_SCHEDULED
                else -> {
                    val s = sessions.first()
                    when (s.status) {
                        SessionStatus.COMPLETED.name -> SessionDayTile.COMPLETED
                        SessionStatus.MISSED.name -> SessionDayTile.MISSED
                        SessionStatus.PLANNED.name -> SessionDayTile.PLANNED
                        else -> SessionDayTile.PLANNED
                    }
                }
            }
            HabitWeekDay(date, tile)
        }
    }

    suspend fun listScheduledHabits(): List<HabitEntity> = habitDao.listScheduledActive()

    /** Scheduled exercise habits (workouts / task sessions), not weigh-ins. */
    suspend fun listScheduledExerciseHabits(): List<HabitEntity> =
        habitDao.listScheduledActive().filter { it.trackingMode != TrackingMode.WEIGHT.name }

    /**
     * Exercise habits that have no session row on [date] yet (ie: off-day or not generated).
     * Use [ensureSessionForHabitOnDate] to add a session so it appears on Today / Progress.
     */
    suspend fun scheduledExerciseHabitsMissingSessionOnDate(date: LocalDate): List<HabitEntity> {
        val habits = listScheduledExerciseHabits()
        return habits.filter { findSessionForHabitOnDate(it.id, date) == null }
    }

    /** True if any exercise habit’s recurrence includes this calendar weekday (ISO: Mon=1 … Sun=7). */
    private suspend fun recurrenceHasExerciseOnWeekday(date: LocalDate): Boolean {
        val dow = date.dayOfWeek.value
        for (h in listScheduledExerciseHabits()) {
            val recurrenceJson = h.recurrenceJson ?: continue
            val rules = try {
                json.decodeFromString(ListSerializer(RecurrenceDayRule.serializer()), recurrenceJson)
            } catch (_: Exception) {
                emptyList()
            }
            if (rules.any { it.dayOfWeek == dow }) return true
        }
        return false
    }

    /**
     * Five-week window: 2 weeks before the current week through 2 weeks after (Sun–Sat grid, 35 days).
     * Emoji reflects good vs bad habit activity (scheduled + unscheduled).
     */
    suspend fun scheduledCalendarFiveWeeks(): List<CalendarDayState> {
        val today = LocalDate.now()
        val sundayOfThisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val start = sundayOfThisWeek.minusWeeks(2)
        val end = start.plusDays(34)
        val out = ArrayList<CalendarDayState>()
        var d = start
        while (!d.isAfter(end)) {
            val snap = computeGoodBadForDay(
                date = d,
                today = today,
                calendarMode = true,
                sessionDao = sessionDao,
                habitDao = habitDao,
                logDao = logDao,
                zone = zone,
            )
            val emoji = when {
                snap.good == 0.0 && snap.bad == 0.0 -> CalendarDayEmoji.REST
                snap.good > 0.0 && snap.bad == 0.0 -> CalendarDayEmoji.FIRE
                snap.bad > 0.0 && snap.good == 0.0 -> CalendarDayEmoji.X_MARK
                snap.good > snap.bad -> CalendarDayEmoji.TREND_UP
                snap.bad > snap.good -> CalendarDayEmoji.TREND_DOWN
                else -> CalendarDayEmoji.TIE
            }
            val futureExercise =
                d.isAfter(today) && recurrenceHasExerciseOnWeekday(d)
            out.add(CalendarDayState(d, emoji, futureExerciseScheduled = futureExercise))
            d = d.plusDays(1)
        }
        return out
    }

    /** Completed weigh-in sessions (scheduled WEIGHT habits), oldest first. */
    suspend fun weightHistory(): List<WeightPoint> {
        val sessions = sessionDao.sessionsWithCompletionValue()
        val out = ArrayList<WeightPoint>()
        for (s in sessions) {
            val h = habitDao.getById(s.habitId) ?: continue
            if (h.trackingMode != TrackingMode.WEIGHT.name) continue
            val v = s.completionValue ?: continue
            out.add(WeightPoint(millisToLocalDate(s.scheduledAt, zone), v, "lbs"))
        }
        return out.sortedWith(compareBy<WeightPoint> { it.date }.thenBy { it.value })
    }

    /** Weigh-ins in the last [days] calendar days (including today), oldest first. */
    suspend fun weightHistoryForWindow(days: Int): List<WeightPoint> {
        val all = weightHistory()
        val end = LocalDate.now()
        val start = end.minusDays(days.toLong() - 1)
        return all.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
    }

    /**
     * Per-day good vs bad for Habit Trends: unscheduled log totals plus one point per completed
     * scheduled session (positive valence → good, negative → bad). Weigh-in complete counts as 1 good.
     */
    suspend fun habitTrendDaily(windowDays: Int): List<HabitTrendDay> {
        val end = LocalDate.now()
        val start = end.minusDays(windowDays.toLong() - 1)
        val unscheduledHabits = habitDao.listActive().filter { !it.isScheduled }
        val activeHabitIds = habitDao.listActive().map { it.id }.toSet()
        val out = ArrayList<HabitTrendDay>()
        var day = start
        while (!day.isAfter(end)) {
            val ds = localDateToStartMillis(day, zone)
            val de = endOfDayMillis(ds, zone)
            var goodU = 0.0
            var badU = 0.0
            for (h in unscheduledHabits) {
                val logs = logDao.logsForHabitInRange(h.id, ds, de)
                val sum = logs.sumOf { log ->
                    log.numericValue ?: if (log.booleanValue == 1) 1.0 else 0.0
                }
                if (h.valence == HabitValence.POSITIVE.name) goodU += sum else badU += sum
            }
            var goodS = 0.0
            var badS = 0.0
            for (s in sessionDao.sessionsBetween(ds, de)) {
                if (s.status != SessionStatus.COMPLETED.name) continue
                val h = habitDao.getById(s.habitId) ?: continue
                if (h.id !in activeHabitIds) continue
                if (h.valence == HabitValence.POSITIVE.name) goodS += 1.0 else badS += 1.0
            }
            val good = goodU + goodS
            val bad = badU + badS
            out.add(
                HabitTrendDay(
                    date = day,
                    goodUnscheduled = goodU,
                    goodScheduled = goodS,
                    good = good,
                    badUnscheduled = badU,
                    badScheduled = badS,
                    bad = bad,
                    net = good - bad,
                ),
            )
            day = day.plusDays(1)
        }
        return out
    }

    suspend fun logsForHabitDay(habitId: String): List<LogEntity> =
        logsForHabitOnDate(habitId, LocalDate.now())

    suspend fun logsForHabitOnDate(habitId: String, date: LocalDate): List<LogEntity> {
        val (s, e) = dayRangeForDate(date)
        return logDao.logsForHabitDay(habitId, s, e)
    }

    suspend fun updateLogNotes(logId: String, notes: String?) {
        val log = logDao.getById(logId) ?: return
        logDao.update(
            log.copy(notes = notes?.trim()?.takeIf { it.isNotEmpty() }),
        )
    }

    suspend fun addCountLog(habitId: String) {
        val h = habitDao.getById(habitId) ?: return
        logDao.insert(
            LogEntity(
                id = UUID.randomUUID().toString(),
                habitId = habitId,
                loggedAt = System.currentTimeMillis(),
                valence = h.valence,
                booleanValue = null,
                numericValue = 1.0,
                unit = h.unit,
                notes = null,
            ),
        )
        streakHelper.onDaySettled(LocalDate.now())
    }

    suspend fun toggleBooleanHabit(habitId: String, turnOn: Boolean) {
        val h = habitDao.getById(habitId) ?: return
        val (start, end) = dayRange()
        if (!turnOn && logDao.countBooleanTrueForDay(habitId, start, end) > 0) {
            return
        }
        if (turnOn) {
            logDao.deleteLogsForHabitDay(habitId, start, end)
            logDao.insert(
                LogEntity(
                    id = UUID.randomUUID().toString(),
                    habitId = habitId,
                    loggedAt = System.currentTimeMillis(),
                    valence = h.valence,
                    booleanValue = 1,
                    numericValue = null,
                    unit = null,
                    notes = null,
                ),
            )
        } else {
            logDao.deleteLogsForHabitDay(habitId, start, end)
        }
        streakHelper.onDaySettled(LocalDate.now())
    }

    suspend fun decrementCount(habitId: String) {
        val (start, end) = dayRange()
        val lid = logDao.latestLogIdForDay(habitId, start, end) ?: return
        logDao.deleteLogById(lid)
        streakHelper.onDaySettled(LocalDate.now())
    }

    suspend fun quickLogForHabit(habitId: String) {
        val h = habitDao.getById(habitId) ?: return
        when (h.trackingMode) {
            TrackingMode.WEIGHT.name -> return
            else -> addCountLog(habitId)
        }
    }

    suspend fun addCountLogOnDay(habitId: String, date: LocalDate) {
        val h = habitDao.getById(habitId) ?: return
        logDao.insert(
            LogEntity(
                id = UUID.randomUUID().toString(),
                habitId = habitId,
                loggedAt = logTimestampForDay(date),
                valence = h.valence,
                booleanValue = null,
                numericValue = 1.0,
                unit = h.unit,
                notes = null,
            ),
        )
        streakHelper.onDaySettled(date)
    }

    suspend fun toggleBooleanHabitOnDay(habitId: String, date: LocalDate, turnOn: Boolean) {
        val h = habitDao.getById(habitId) ?: return
        val (start, end) = dayRangeForDate(date)
        if (!turnOn && logDao.countBooleanTrueForDay(habitId, start, end) > 0) {
            return
        }
        if (turnOn) {
            logDao.deleteLogsForHabitDay(habitId, start, end)
            logDao.insert(
                LogEntity(
                    id = UUID.randomUUID().toString(),
                    habitId = habitId,
                    loggedAt = logTimestampForDay(date),
                    valence = h.valence,
                    booleanValue = 1,
                    numericValue = null,
                    unit = null,
                    notes = null,
                ),
            )
        } else {
            logDao.deleteLogsForHabitDay(habitId, start, end)
        }
        streakHelper.onDaySettled(date)
    }

    suspend fun quickLogForHabitOnDay(habitId: String, date: LocalDate) {
        val h = habitDao.getById(habitId) ?: return
        when (h.trackingMode) {
            TrackingMode.WEIGHT.name -> return
            else -> addCountLogOnDay(habitId, date)
        }
    }

    suspend fun decrementCountOnDay(habitId: String, date: LocalDate) {
        val (start, end) = dayRangeForDate(date)
        val lid = logDao.latestLogIdForDay(habitId, start, end) ?: return
        logDao.deleteLogById(lid)
        streakHelper.onDaySettled(date)
    }

    /** JSON backup for transfer or restore (habits, sessions, tasks, logs, settings KV). */
    suspend fun exportBackupJson(): String {
        val habits = habitDao.listAllForBackup().map { it.toBackupRow() }
        val sessions = sessionDao.listAllForBackup().map { it.toBackupRow() }
        val tasks = taskDao.listAllForBackup().map { it.toBackupRow() }
        val logs = logDao.listAllForBackup().map { it.toBackupRow() }
        val kv = kvDao.listAllForBackup().map { it.toBackupRow() }
        val payload = MomentumBackupV1(
            schemaVersion = MOMENTUM_BACKUP_SCHEMA_VERSION,
            exportedAtEpochMs = System.currentTimeMillis(),
            habits = habits,
            sessions = sessions,
            tasks = tasks,
            logs = logs,
            kv = kv,
        )
        return backupJson.encodeToString(MomentumBackupV1.serializer(), payload)
    }

    private fun parseBackupOrThrow(jsonText: String): MomentumBackupV1 {
        val data = try {
            backupJson.decodeFromString(MomentumBackupV1.serializer(), jsonText.trim())
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not read backup file.", e)
        }
        if (data.schemaVersion != MOMENTUM_BACKUP_SCHEMA_VERSION) {
            throw IllegalArgumentException(
                "This backup (version ${data.schemaVersion}) cannot be loaded. App supports version $MOMENTUM_BACKUP_SCHEMA_VERSION.",
            )
        }
        return data
    }

    /** Parse and validate a backup without writing (for import confirmation UI). */
    fun peekBackupJson(jsonText: String): MomentumBackupV1 = parseBackupOrThrow(jsonText)

    /**
     * Replaces all local data with the backup. Caller should refresh UI and reschedule notifications.
     * @throws IllegalArgumentException if JSON is invalid or schema version is unsupported.
     */
    suspend fun importBackupJson(jsonText: String) {
        val data = parseBackupOrThrow(jsonText)
        database.withTransaction {
            taskDao.deleteAll()
            logDao.deleteAll()
            sessionDao.deleteAll()
            kvDao.deleteAll()
            habitDao.deleteAll()
            data.habits.forEach { habitDao.insert(it.toEntity()) }
            data.sessions.forEach { sessionDao.insert(it.toEntity()) }
            data.tasks.forEach { taskDao.insert(it.toEntity()) }
            data.logs.forEach { logDao.insert(it.toEntity()) }
            data.kv.forEach { kvDao.upsert(it.toEntity()) }
        }
    }
}
