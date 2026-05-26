package com.momentum.app.data.streak

import com.momentum.app.data.local.dao.HabitDao
import com.momentum.app.data.local.dao.LogDao
import com.momentum.app.data.local.dao.SessionDao
import com.momentum.app.data.local.entity.SessionEntity
import com.momentum.app.data.repo.GoodBadSnapshot
import com.momentum.app.domain.HabitValence
import com.momentum.app.domain.SessionStatus
import com.momentum.app.domain.TrackingMode
import com.momentum.app.domain.endOfDayMillis
import com.momentum.app.domain.localDateToStartMillis
import java.time.LocalDate
import java.util.TimeZone

/** Good/bad tallies from scheduled sessions (active scheduled habits). */
internal data class ScheduledSessionScores(
    var good: Double = 0.0,
    var bad: Double = 0.0,
    var hasPlannedScheduledSessions: Boolean = false,
)

/**
 * Tallies scheduled sessions for [date] using the same rules as [computeGoodBadForDay]
 * (completed, missed, and planned-in-calendar-mode).
 */
internal suspend fun accumulateScheduledSessionScores(
    sessions: List<SessionEntity>,
    habitDao: HabitDao,
    date: LocalDate,
    today: LocalDate,
    calendarMode: Boolean,
    into: ScheduledSessionScores,
) {
    for (s in sessions) {
        val h = habitDao.getById(s.habitId) ?: continue
        when (s.status) {
            SessionStatus.COMPLETED.name -> {
                if (h.valence == HabitValence.POSITIVE.name) into.good += 1.0 else into.bad += 1.0
            }
            SessionStatus.MISSED.name -> {
                if (h.valence == HabitValence.POSITIVE.name) {
                    into.bad += 1.0
                } else {
                    into.good += 1.0
                }
            }
            SessionStatus.PLANNED.name -> {
                when {
                    date.isAfter(today) -> { /* future */ }
                    !calendarMode && !date.isAfter(today) -> {
                        into.hasPlannedScheduledSessions = true
                    }
                    calendarMode && date == today -> { /* still open */ }
                    calendarMode && !date.isAfter(today) -> {
                        if (h.valence == HabitValence.POSITIVE.name) {
                            into.bad += 1.0
                        } else {
                            into.good += 1.0
                        }
                    }
                }
            }
        }
    }
}

/** True when the user logged any habit activity on [date] (positive or negative). */
suspend fun hasAnyHabitActivityForDay(
    date: LocalDate,
    sessionDao: SessionDao,
    logDao: LogDao,
    zone: TimeZone,
): Boolean {
    val start = localDateToStartMillis(date, zone)
    val end = endOfDayMillis(start, zone)
    if (logDao.countLogsBetween(start, end) > 0) return true
    return sessionDao.sessionsBetween(start, end).any { session ->
        session.status == SessionStatus.COMPLETED.name ||
            session.status == SessionStatus.MISSED.name
    }
}

/**
 * Combines scheduled sessions and unscheduled logs into good vs bad scores for a calendar day.
 *
 * @param calendarMode true for Progress calendar emoji: today's PLANNED sessions contribute 0;
 *   past PLANNED is treated as missed. false for streak settlement: any PLANNED on/before today
 *   sets [GoodBadSnapshot.hasPlannedScheduledSessions] and skips numeric tally for streak use.
 */
suspend fun computeGoodBadForDay(
    date: LocalDate,
    today: LocalDate,
    calendarMode: Boolean,
    sessionDao: SessionDao,
    habitDao: HabitDao,
    logDao: LogDao,
    zone: TimeZone,
): GoodBadSnapshot {
    val start = localDateToStartMillis(date, zone)
    val end = endOfDayMillis(start, zone)
    var good = 0.0
    var bad = 0.0
    var hasPlannedScheduledSessions = false

    val activeScheduledIds = habitDao.listScheduledActive().map { it.id }.toSet()
    val sessions = sessionDao.sessionsBetween(start, end).filter { it.habitId in activeScheduledIds }
    val sched = ScheduledSessionScores(good, bad, hasPlannedScheduledSessions)
    accumulateScheduledSessionScores(
        sessions,
        habitDao,
        date,
        today,
        calendarMode,
        sched,
    )
    good = sched.good
    bad = sched.bad
    hasPlannedScheduledSessions = sched.hasPlannedScheduledSessions

    val unscheduled = habitDao.listActive().filter { !it.isScheduled }
    for (h in unscheduled) {
        val logs = logDao.logsForHabitInRange(h.id, start, end)
        val sum = logs.sumOf { log ->
            log.numericValue ?: if (log.booleanValue == 1) 1.0 else 0.0
        }
        if (h.trackingMode == TrackingMode.WEIGHT.name) continue
        if (h.valence == HabitValence.POSITIVE.name) good += sum else bad += sum
    }

    return GoodBadSnapshot(
        good = good,
        bad = bad,
        hasPlannedScheduledSessions = hasPlannedScheduledSessions,
    )
}
