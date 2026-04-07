package com.momentum.app.data.repo

import java.time.LocalDate

/** One cell in the 5-week Progress calendar (Sun–Sat rows). */
enum class CalendarDayEmoji {
    /** No good/bad activity (or future day with nothing logged yet). */
    REST,
    /** Only positive / good activity (bad score is zero, good > 0). */
    FIRE,
    /** More good than bad. */
    TREND_UP,
    /** More bad than good. */
    TREND_DOWN,
    /** Only bad activity (good is zero, bad > 0). */
    X_MARK,
    /** Good and bad both non-zero and equal. */
    TIE,
}

data class CalendarDayState(
    val date: LocalDate,
    val emoji: CalendarDayEmoji,
    /**
     * True for days after today when recurrence includes a scheduled exercise (non–weigh-in) session.
     * Shown as 💪 on the calendar; past and today omit this marker.
     */
    val futureExerciseScheduled: Boolean = false,
)

/** Result of combining scheduled + unscheduled habit activity for one day. */
data class GoodBadSnapshot(
    val good: Double,
    val bad: Double,
    val hasPlannedScheduledSessions: Boolean,
    val anyPositiveMissedScheduled: Boolean,
)

data class WeightPoint(
    val date: LocalDate,
    val value: Double,
    val unit: String?,
)

data class StreakSnapshot(
    /** Consecutive days with more good than bad (trending up). */
    val current: Int,
    val longest: Int,
    /** Consecutive days with only good activity (no bad score). */
    val perfectCurrent: Int,
    val perfectLongest: Int,
    val freezesRemaining: Int,
)

enum class SessionDayTile {
    COMPLETED,
    MISSED,
    NOT_SCHEDULED,
    PLANNED,
}

data class HabitWeekDay(
    val date: LocalDate,
    val tile: SessionDayTile,
)

/**
 * Daily habit scoring for Habit Trends: unscheduled log sums plus +1 per completed scheduled session
 * (by habit valence). [net] = good − bad.
 */
data class HabitTrendDay(
    val date: LocalDate,
    /** Unscheduled positive-habit counts (same as former positive line). */
    val goodUnscheduled: Double,
    /** Completed scheduled sessions for positive-valence habits (1 each). */
    val goodScheduled: Double,
    val good: Double,
    val badUnscheduled: Double,
    val badScheduled: Double,
    val bad: Double,
    val net: Double,
)

/** One scheduled session row for [dayTrackingSummary]. */
data class ScheduledSessionDaySummary(
    val habitTitle: String,
    val trackingMode: String?,
    val status: String,
    /** Weigh-in value when completed, else null. */
    val weightLbs: Double?,
    /** Workout session to-dos with completion markers. */
    val taskLines: List<String>,
)

/** One unscheduled habit row for [dayTrackingSummary]. */
data class UnscheduledHabitDaySummary(
    val habitTitle: String,
    val valenceLabel: String,
    val summaryText: String,
)

/** Overview of what was logged on a calendar day (for Insert/Edit dialog). */
data class DayTrackingSummary(
    val scheduled: List<ScheduledSessionDaySummary>,
    val unscheduled: List<UnscheduledHabitDaySummary>,
)
