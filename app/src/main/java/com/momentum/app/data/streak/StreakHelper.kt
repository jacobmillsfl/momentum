package com.momentum.app.data.streak

import com.momentum.app.data.local.dao.KvDao
import com.momentum.app.data.local.dao.LogDao
import com.momentum.app.data.local.dao.SessionDao
import com.momentum.app.data.local.entity.KvEntity
import com.momentum.app.data.repo.KvKeys
import com.momentum.app.domain.SessionStatus
import com.momentum.app.domain.millisToLocalDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.TimeZone

/**
 * Streak: consecutive calendar days with any logged habit activity (positive or negative).
 */
class StreakHelper(
    private val sessionDao: SessionDao,
    private val logDao: LogDao,
    private val kvDao: KvDao,
) {
    private val zone: TimeZone get() = TimeZone.getDefault()

    suspend fun ensureFreezeMonthReset() {
        // No-op: streak freezes removed; kept for call-site compatibility during rollout.
    }

    suspend fun onDaySettledForMillis(dayStartMs: Long) {
        onDaySettled(millisToLocalDate(dayStartMs, zone))
    }

    suspend fun onDaySettled(date: LocalDate) {
        val today = LocalDate.now()
        if (date.isAfter(today)) return
        recomputeStreakMetrics()
    }

    suspend fun recomputeStreakMetrics() {
        val activityDays = collectActivityDays()
        val today = LocalDate.now()
        val current = computeCurrentStreak(activityDays, today)
        val longest = computeLongestStreak(activityDays)
        val lastActive = findLastActiveDayInCurrentStreak(activityDays, today)
        kvDao.upsert(KvEntity(KvKeys.STREAK_CURRENT, current.toString()))
        kvDao.upsert(KvEntity(KvKeys.STREAK_LONGEST, longest.toString()))
        kvDao.upsert(
            KvEntity(
                KvKeys.STREAK_LAST_ACTIVE,
                lastActive?.format(DateTimeFormatter.ISO_LOCAL_DATE).orEmpty(),
            ),
        )
    }

    private suspend fun collectActivityDays(): Set<LocalDate> {
        val days = mutableSetOf<LocalDate>()
        for (log in logDao.listAllForBackup()) {
            days.add(millisToLocalDate(log.loggedAt, zone))
        }
        for (session in sessionDao.listAllForBackup()) {
            if (session.status == SessionStatus.COMPLETED.name ||
                session.status == SessionStatus.MISSED.name
            ) {
                days.add(millisToLocalDate(session.scheduledAt, zone))
            }
        }
        return days
    }

    private fun computeLongestStreak(activityDays: Set<LocalDate>): Int {
        if (activityDays.isEmpty()) return 0
        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (date in activityDays.sorted()) {
            run = if (previous != null && date == previous.plusDays(1)) run + 1 else 1
            if (run > longest) longest = run
            previous = date
        }
        return longest
    }

    private fun computeCurrentStreak(activityDays: Set<LocalDate>, today: LocalDate): Int {
        if (activityDays.isEmpty()) return 0
        var cursor = if (activityDays.contains(today)) today else today.minusDays(1)
        if (!activityDays.contains(cursor)) return 0
        var count = 0
        while (activityDays.contains(cursor)) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    private fun findLastActiveDayInCurrentStreak(activityDays: Set<LocalDate>, today: LocalDate): LocalDate? {
        if (activityDays.isEmpty()) return null
        var cursor = if (activityDays.contains(today)) today else today.minusDays(1)
        if (!activityDays.contains(cursor)) return null
        while (activityDays.contains(cursor)) {
            val previous = cursor.minusDays(1)
            if (!activityDays.contains(previous)) return cursor
            cursor = previous
        }
        return cursor
    }
}
