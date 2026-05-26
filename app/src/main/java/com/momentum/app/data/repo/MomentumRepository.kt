package com.momentum.app.data.repo

import androidx.room.withTransaction
import com.momentum.app.data.backup.MOMENTUM_BACKUP_SCHEMA_VERSION
import com.momentum.app.data.backup.MomentumBackupV1
import com.momentum.app.data.backup.toBackupRow
import com.momentum.app.data.backup.toEntity
import com.momentum.app.data.local.MomentumDatabase
import com.momentum.app.data.local.entity.HabitEntity
import com.momentum.app.data.local.entity.KvEntity
import com.momentum.app.data.local.entity.MigrationStateEntity
import com.momentum.app.data.local.entity.LogEntity
import com.momentum.app.data.local.entity.ProjectEntity
import com.momentum.app.data.local.entity.SessionEntity
import com.momentum.app.data.local.entity.TaskEntity
import com.momentum.app.data.streak.ScheduledSessionScores
import com.momentum.app.data.streak.StreakHelper
import com.momentum.app.data.streak.accumulateScheduledSessionScores
import com.momentum.app.data.streak.computeGoodBadForDay
import com.momentum.app.data.streak.hasAnyHabitActivityForDay
import com.momentum.app.domain.HabitGoalDomain
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
import kotlinx.serialization.decodeFromString
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
    /** Ignored for scheduled workouts (always [HabitGoalDomain.BODY]) and weigh-ins (always [HabitGoalDomain.MIND]). */
    val goalDomain: HabitGoalDomain,
)

data class SessionWithHabit(
    val session: SessionEntity,
    val habitTitle: String,
    val habitTrackingMode: String?,
    val habitUnit: String?,
    /** [com.momentum.app.domain.HabitValence.name] */
    val habitValence: String,
    /** [com.momentum.app.domain.HabitGoalDomain.name] or null */
    val habitGoalDomain: String?,
)

data class UnscheduledRow(
    val habit: HabitEntity,
    val todayDisplay: String,
)

/** Positive vs negative habit completion sums for one Mind/Body side (current calendar day). */
data class DomainCompletionTotals(
    val positive: Double,
    val negative: Double,
)

data class TodayMindBodyCompletionTotals(
    val mind: DomainCompletionTotals,
    val body: DomainCompletionTotals,
)

class MomentumRepository(
    private val database: MomentumDatabase,
) {
    private val habitDao = database.habitDao()
    private val migrationDao = database.migrationDao()
    private val sessionDao = database.sessionDao()
    private val logDao = database.logDao()
    private val kvDao = database.kvDao()
    private val taskDao = database.taskDao()
    private val projectDao = database.projectDao()
    private val json = Json { ignoreUnknownKeys = true }
    private val backupJson = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
    private val streakHelper = StreakHelper(sessionDao, logDao, kvDao)
    private val zone: TimeZone get() = TimeZone.getDefault()

    suspend fun seedKvDefaults() {
        val defaults = listOf(
            KvKeys.STREAK_CURRENT to "0",
            KvKeys.STREAK_LONGEST to "0",
            KvKeys.STREAK_LAST_ACTIVE to "",
            KvKeys.THEME_MODE to "system",
            KvKeys.NOTIFICATIONS_ENABLED to "false",
            KvKeys.NOTIFICATION_TIME to "09:00",
            KvKeys.NOTIFICATION_EVENING_TIME to "20:00",
            KvKeys.TARGET_WEIGHT_LBS to "",
            KvKeys.STARTING_WEIGHT_LBS to "",
        )
        for ((k, v) in defaults) {
            if (kvDao.get(k) == null) {
                kvDao.upsert(KvEntity(k, v))
            }
        }
        runStreakActivityMigrationIfNeeded()
    }

    private suspend fun runStreakActivityMigrationIfNeeded() {
        if (migrationDao.getById(MigrationIds.STREAK_ACTIVITY_V2)?.completed == true) return
        streakHelper.recomputeStreakMetrics()
        migrationDao.upsert(
            MigrationStateEntity(
                MigrationIds.STREAK_ACTIVITY_V2,
                completed = true,
                completedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun hasAnyHabitActivityToday(): Boolean =
        hasAnyHabitActivityForDay(LocalDate.now(), sessionDao, logDao, zone)

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
        val resolvedGoalDomain = resolveGoalDomainForNewHabit(input)
        val habit = HabitEntity(
            id = id,
            title = input.title.trim(),
            valence = input.valence.name,
            isScheduled = input.isScheduled,
            trackingMode = input.trackingMode?.name,
            unit = if (input.trackingMode == TrackingMode.WEIGHT) "lbs" else null,
            recurrenceJson = recurrenceJson,
            notes = input.notes?.trim()?.takeIf { it.isNotEmpty() },
            archivedAt = null,
            createdAt = now,
            goalDomain = resolvedGoalDomain.name,
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

    private fun resolveGoalDomainForNewHabit(input: NewHabitInput): HabitGoalDomain =
        if (input.isScheduled) {
            if (input.trackingMode == TrackingMode.WEIGHT) HabitGoalDomain.MIND else HabitGoalDomain.BODY
        } else {
            input.goalDomain
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
            SessionWithHabit(s, h.title, h.trackingMode, h.unit, h.valence, h.goalDomain)
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
            SessionWithHabit(s, h.title, h.trackingMode, h.unit, h.valence, h.goalDomain)
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

    /**
     * Today's completions split by Mind vs Body for the Today gauge: unscheduled log totals plus
     * one unit per completed scheduled session (weigh-in or workout) per habit.
     */
    suspend fun todayMindBodyCompletionTotals(): TodayMindBodyCompletionTotals {
        val date = LocalDate.now()
        val (start, end) = dayRange()
        val dayStart = localDateToStartMillis(date, zone)
        var mindPos = 0.0
        var mindNeg = 0.0
        var bodyPos = 0.0
        var bodyNeg = 0.0
        val habits = habitDao.listActive()
        for (h in habits) {
            val domain = h.goalDomain?.let { runCatching { HabitGoalDomain.valueOf(it) }.getOrNull() }
                ?: continue
            val isPositive = h.valence == HabitValence.POSITIVE.name
            if (!h.isScheduled) {
                val sum = unscheduledDayTotal(h.id, start, end)
                if (sum <= 0.0) continue
                when (domain) {
                    HabitGoalDomain.MIND -> if (isPositive) mindPos += sum else mindNeg += sum
                    HabitGoalDomain.BODY -> if (isPositive) bodyPos += sum else bodyNeg += sum
                }
            } else {
                val s = sessionDao.findByHabitAndDay(h.id, dayStart) ?: continue
                if (s.status != SessionStatus.COMPLETED.name) continue
                val contribution = 1.0
                when (domain) {
                    HabitGoalDomain.MIND ->
                        if (isPositive) mindPos += contribution else mindNeg += contribution
                    HabitGoalDomain.BODY ->
                        if (isPositive) bodyPos += contribution else bodyNeg += contribution
                }
            }
        }
        return TodayMindBodyCompletionTotals(
            mind = DomainCompletionTotals(mindPos, mindNeg),
            body = DomainCompletionTotals(bodyPos, bodyNeg),
        )
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

    /** Permanently removes the habit and all logs/sessions (cascade). */
    suspend fun deleteHabitPermanently(habitId: String) {
        habitDao.deleteById(habitId)
    }

    suspend fun updateHabitMetadata(
        habitId: String,
        title: String,
        notes: String?,
        goalDomain: HabitGoalDomain?,
    ) {
        val h = habitDao.getById(habitId) ?: throw IllegalArgumentException("Habit not found")
        val trimmed = title.trim()
        if (trimmed.isEmpty()) throw IllegalStateException("Name is required")
        val resolvedGoal = when {
            h.isScheduled && h.trackingMode == TrackingMode.WEIGHT.name -> HabitGoalDomain.MIND
            h.isScheduled -> HabitGoalDomain.BODY
            else -> goalDomain ?: throw IllegalStateException("Choose Mind or Body")
        }
        habitDao.update(
            h.copy(
                title = trimmed,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                goalDomain = resolvedGoal.name,
            ),
        )
    }

    suspend fun updateScheduledHabitWeeklyPlan(habitId: String, rules: List<RecurrenceDayRule>) {
        val h = habitDao.getById(habitId) ?: return
        if (!h.isScheduled || rules.isEmpty()) return
        val sorted = rules.sortedBy { it.dayOfWeek }
        val recurrenceJson = json.encodeToString(ListSerializer(RecurrenceDayRule.serializer()), sorted)
        val todayStart = startOfDayUtcMillis(zone)
        database.withTransaction {
            habitDao.update(h.copy(recurrenceJson = recurrenceJson))
            sessionDao.deleteFuturePlannedSessions(habitId, todayStart, SessionStatus.PLANNED.name)
            val occ = buildOccurrencesFromToday(sorted, SESSION_GENERATION_WEEKS, zone)
            for (o in occ) {
                if (sessionDao.findByHabitAndDay(habitId, o.dayStartMs) != null) continue
                val categoriesJson = json.encodeToString(
                    ListSerializer(serializer<String>()),
                    o.rule.categories,
                )
                sessionDao.insert(
                    SessionEntity(
                        id = UUID.randomUUID().toString(),
                        habitId = habitId,
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

    /**
     * Merges two habits into a new one with [newTitle]; reassigns all logs/sessions.
     * Habits must match valence, scheduled mode, tracking mode, and Mind/Body focus.
     */
    suspend fun mergeHabitsIntoNew(habitIdA: String, habitIdB: String, newTitle: String): String {
        val a = habitDao.getById(habitIdA) ?: throw IllegalArgumentException("Habit not found")
        val b = habitDao.getById(habitIdB) ?: throw IllegalArgumentException("Other habit not found")
        if (a.id == b.id) throw IllegalStateException("Pick two different habits")
        if (a.valence != b.valence) throw IllegalStateException("Both habits must be the same type (positive or negative)")
        if (a.isScheduled != b.isScheduled) throw IllegalStateException("Cannot merge scheduled with unscheduled")
        if (a.trackingMode != b.trackingMode) throw IllegalStateException("Habit kinds must match (e.g. both count-based)")
        if (a.goalDomain == null || b.goalDomain == null || a.goalDomain != b.goalDomain) {
            throw IllegalStateException("Both habits must have the same Mind/Body focus")
        }
        val t = newTitle.trim()
        if (t.isEmpty()) throw IllegalStateException("New name is required")

        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val mergedNotes = listOfNotNull(a.notes, b.notes)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n---\n")
            .takeIf { it.isNotEmpty() }
        val mergedRecurrenceJson = if (a.isScheduled) {
            val ra = parseRecurrenceRules(a.recurrenceJson)
            val rb = parseRecurrenceRules(b.recurrenceJson)
            val byDay = LinkedHashMap<Int, RecurrenceDayRule>()
            for (r in ra + rb) {
                byDay[r.dayOfWeek] = r
            }
            json.encodeToString(
                ListSerializer(RecurrenceDayRule.serializer()),
                byDay.values.sortedBy { it.dayOfWeek },
            )
        } else {
            null
        }
        database.withTransaction {
            val newHabit = HabitEntity(
                id = newId,
                title = t,
                valence = a.valence,
                isScheduled = a.isScheduled,
                trackingMode = a.trackingMode,
                unit = a.unit,
                recurrenceJson = mergedRecurrenceJson,
                notes = mergedNotes,
                archivedAt = null,
                createdAt = now,
                goalDomain = a.goalDomain,
            )
            habitDao.insert(newHabit)
            logDao.reassignHabitId(a.id, newId)
            logDao.reassignHabitId(b.id, newId)
            if (a.isScheduled) {
                // Move every session to the new habit first so nothing still references the old rows;
                // then collapse duplicate times (same calendar instant) into one session.
                sessionDao.reassignHabitId(a.id, newId)
                sessionDao.reassignHabitId(b.id, newId)
                dedupeSessionsSharingScheduledInstant(sessionDao.sessionsForHabitOrdered(newId))
            }
            habitDao.deleteById(a.id)
            habitDao.deleteById(b.id)
        }
        return newId
    }

    /**
     * Sessions already share one [SessionEntity.habitId]. Merge rows that share the same [SessionEntity.scheduledAt]
     * by keeping one session and moving tasks off the others before deleting duplicates.
     */
    private suspend fun dedupeSessionsSharingScheduledInstant(sessions: List<SessionEntity>) {
        if (sessions.isEmpty()) return
        val groups = sessions.groupBy { it.scheduledAt }
        for (group in groups.values) {
            val keeper = group.first()
            for (extra in group.drop(1)) {
                taskDao.reassignSessionId(extra.id, keeper.id)
                sessionDao.deleteById(extra.id)
            }
        }
    }

    private fun parseRecurrenceRules(recurrenceJson: String?): List<RecurrenceDayRule> {
        if (recurrenceJson.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(RecurrenceDayRule.serializer()), recurrenceJson)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun streakSnapshot(): StreakSnapshot {
        val current = kvDao.get(KvKeys.STREAK_CURRENT)?.toIntOrNull() ?: 0
        val longest = kvDao.get(KvKeys.STREAK_LONGEST)?.toIntOrNull() ?: 0
        return StreakSnapshot(
            current = current,
            longest = longest,
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

    /** Scheduled weigh-in habits (WEIGHT tracking), active. */
    suspend fun listScheduledWeighInHabits(): List<HabitEntity> =
        habitDao.listScheduledActive().filter { it.trackingMode == TrackingMode.WEIGHT.name }

    /** True if the user already has a scheduled workout-style habit (at most one is allowed). */
    suspend fun hasScheduledWorkoutHabit(): Boolean = listScheduledExerciseHabits().isNotEmpty()

    /** True if the user already has a scheduled weigh-in habit (at most one is allowed). */
    suspend fun hasScheduledWeighInHabit(): Boolean = listScheduledWeighInHabits().isNotEmpty()

    /**
     * Exercise habits that have no session row on [date] yet (ie: off-day or not generated).
     * Use [ensureSessionForHabitOnDate] to add a session so it appears on Today / Progress.
     * Empty if the user has no scheduled workout habit.
     */
    suspend fun scheduledExerciseHabitsMissingSessionOnDate(date: LocalDate): List<HabitEntity> {
        val habits = listScheduledExerciseHabits()
        return habits.filter { findSessionForHabitOnDate(it.id, date) == null }
    }

    /**
     * Weigh-in habits that have no session row on [date] yet.
     * Empty if the user has no scheduled weigh-in habit.
     */
    suspend fun scheduledWeighInHabitsMissingSessionOnDate(date: LocalDate): List<HabitEntity> {
        val habits = listScheduledWeighInHabits()
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
        val dueProjects = projectDao.listIncompleteDueInEpochDayRange(start.toEpochDay(), end.toEpochDay())
        val dueByEpochDay = dueProjects.groupBy { it.dueEpochDay!! }
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
            val habitEmoji = when {
                snap.good == 0.0 && snap.bad == 0.0 -> CalendarDayEmoji.REST
                snap.good > 0.0 && snap.bad == 0.0 -> CalendarDayEmoji.FIRE
                snap.bad > 0.0 && snap.good == 0.0 -> CalendarDayEmoji.X_MARK
                snap.good > snap.bad -> CalendarDayEmoji.TREND_UP
                snap.bad > snap.good -> CalendarDayEmoji.TREND_DOWN
                else -> CalendarDayEmoji.TIE
            }
            val projectDue = !d.isBefore(today) && dueByEpochDay[d.toEpochDay()]?.isNotEmpty() == true
            val emoji = if (projectDue) CalendarDayEmoji.PROJECT_DUE else habitEmoji
            val futureExercise =
                d.isAfter(today) && recurrenceHasExerciseOnWeekday(d)
            out.add(CalendarDayState(d, emoji, futureExerciseScheduled = futureExercise))
            d = d.plusDays(1)
        }
        return out
    }

    suspend fun projectsIncompleteDueOn(date: LocalDate): List<ProjectEntity> =
        projectDao.listIncompleteDueOnEpochDay(date.toEpochDay())

    suspend fun listProjectsActive(): List<ProjectEntity> = projectDao.listActiveOrdered()

    suspend fun listProjectsCompleted(): List<ProjectEntity> = projectDao.listCompletedOrdered()

    suspend fun projectById(id: String): ProjectEntity? = projectDao.getById(id)

    suspend fun createProject(title: String, description: String?, dueDate: LocalDate?): String {
        val t = title.trim()
        if (t.isEmpty()) throw IllegalStateException("Title is required")
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val dueEpoch = dueDate?.toEpochDay()
        projectDao.insert(
            ProjectEntity(
                id = id,
                title = t,
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                createdAtMs = now,
                dueEpochDay = dueEpoch,
                completedAtMs = null,
            ),
        )
        return id
    }

    suspend fun updateProject(
        id: String,
        title: String,
        description: String?,
        dueDate: LocalDate?,
        completed: Boolean,
    ) {
        val p = projectDao.getById(id) ?: throw IllegalArgumentException("Project not found")
        val t = title.trim()
        if (t.isEmpty()) throw IllegalStateException("Title is required")
        val dueEpoch = dueDate?.toEpochDay()
        val completedAt = when {
            completed -> p.completedAtMs ?: System.currentTimeMillis()
            else -> null
        }
        projectDao.update(
            p.copy(
                title = t,
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                dueEpochDay = dueEpoch,
                completedAtMs = completedAt,
            ),
        )
    }

    suspend fun deleteProject(id: String) {
        projectDao.deleteById(id)
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
     * Per-day good vs bad for Habit Trends: unscheduled log totals plus scheduled sessions scored
     * like the Progress calendar (complete / miss / past planned), so momentum matches streak logic.
     *
     * @param goalDomain `null` = combined trend using every active habit that has a goal domain set.
     *  Otherwise only habits in that domain contribute.
     */
    suspend fun habitTrendDaily(windowDays: Int, goalDomain: HabitGoalDomain? = null): List<HabitTrendDay> {
        val window = windowDays.coerceAtLeast(1)
        val end = LocalDate.now()
        val start = end.minusDays(window.toLong() - 1)
        val activeHabits = habitDao.listActive()
        fun habitInTrendFilter(h: HabitEntity): Boolean {
            val gd = h.goalDomain ?: return false
            return goalDomain == null || gd == goalDomain.name
        }
        val unscheduledHabits = activeHabits.filter { !it.isScheduled && habitInTrendFilter(it) }
        val scheduledHabitIdsInFilter =
            activeHabits.filter { it.isScheduled && habitInTrendFilter(it) }.map { it.id }.toSet()
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
            val sessions = sessionDao.sessionsBetween(ds, de).filter { it.habitId in scheduledHabitIdsInFilter }
            val schedAcc = ScheduledSessionScores()
            accumulateScheduledSessionScores(
                sessions,
                habitDao,
                day,
                end,
                calendarMode = true,
                schedAcc,
            )
            val goodS = schedAcc.good
            val badS = schedAcc.bad
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

    private suspend fun autoAssignInferrableGoalDomains() {
        for (h in habitDao.listActive()) {
            if (h.goalDomain != null) continue
            val inferred: HabitGoalDomain? = when {
                h.isScheduled && h.trackingMode == TrackingMode.WEIGHT.name -> HabitGoalDomain.MIND
                h.isScheduled -> HabitGoalDomain.BODY
                else -> null
            }
            if (inferred != null) {
                habitDao.update(h.copy(goalDomain = inferred.name))
            }
        }
    }

    /**
     * When true, the app should block normal navigation until [completeGoalDomainMigration] runs.
     * Infers Body/Mind for scheduled exercise vs weigh-in habits automatically.
     */
    suspend fun shouldBlockForGoalDomainMigration(): Boolean {
        if (migrationDao.getById(MigrationIds.HABIT_GOAL_DOMAIN_V1)?.completed == true) return false
        autoAssignInferrableGoalDomains()
        val pending = habitDao.listActive().filter { it.goalDomain == null }
        if (pending.isEmpty()) {
            migrationDao.upsert(
                MigrationStateEntity(MigrationIds.HABIT_GOAL_DOMAIN_V1, true, System.currentTimeMillis()),
            )
            return false
        }
        return true
    }

    suspend fun listHabitsNeedingGoalDomainClassification(): List<HabitEntity> {
        autoAssignInferrableGoalDomains()
        return habitDao.listActive().filter { it.goalDomain == null }
    }

    suspend fun completeGoalDomainMigration(assignments: Map<String, HabitGoalDomain>) {
        database.withTransaction {
            for ((id, domain) in assignments) {
                val h = habitDao.getById(id) ?: continue
                habitDao.update(h.copy(goalDomain = domain.name))
            }
            migrationDao.upsert(
                MigrationStateEntity(MigrationIds.HABIT_GOAL_DOMAIN_V1, true, System.currentTimeMillis()),
            )
        }
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
        val projects = projectDao.listAllForBackup().map { it.toBackupRow() }
        val payload = MomentumBackupV1(
            schemaVersion = MOMENTUM_BACKUP_SCHEMA_VERSION,
            exportedAtEpochMs = System.currentTimeMillis(),
            habits = habits,
            sessions = sessions,
            tasks = tasks,
            logs = logs,
            kv = kv,
            projects = projects,
        )
        return backupJson.encodeToString(MomentumBackupV1.serializer(), payload)
    }

    private fun parseBackupOrThrow(jsonText: String): MomentumBackupV1 {
        val data = try {
            backupJson.decodeFromString(MomentumBackupV1.serializer(), jsonText.trim())
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not read backup file.", e)
        }
        if (data.schemaVersion !in 1..MOMENTUM_BACKUP_SCHEMA_VERSION) {
            throw IllegalArgumentException(
                "This backup (version ${data.schemaVersion}) cannot be loaded. App supports versions 1–$MOMENTUM_BACKUP_SCHEMA_VERSION.",
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
            migrationDao.deleteAll()
            habitDao.deleteAll()
            projectDao.deleteAll()
            data.habits.forEach { habitDao.insert(it.toEntity()) }
            data.sessions.forEach { sessionDao.insert(it.toEntity()) }
            data.tasks.forEach { taskDao.insert(it.toEntity()) }
            data.logs.forEach { logDao.insert(it.toEntity()) }
            data.kv.forEach { kvDao.upsert(it.toEntity()) }
            data.projects.forEach { projectDao.insert(it.toEntity()) }
        }
    }

    /**
     * Removes all habits, sessions, tasks, logs, and settings, then restores the same default
     * key/value rows as a fresh install ([seedKvDefaults]). Caller should refresh UI and
     * reschedule notifications.
     */
    suspend fun resetAllLocalData() {
        database.withTransaction {
            taskDao.deleteAll()
            logDao.deleteAll()
            sessionDao.deleteAll()
            kvDao.deleteAll()
            migrationDao.deleteAll()
            habitDao.deleteAll()
            projectDao.deleteAll()
        }
        seedKvDefaults()
    }
}
