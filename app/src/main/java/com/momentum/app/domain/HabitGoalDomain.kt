package com.momentum.app.domain

/**
 * High-level area a habit supports: mental/emotional/social ([MIND]) vs physical ([BODY]).
 * Scheduled workouts are always [BODY]; scheduled weigh-ins are always [MIND].
 */
enum class HabitGoalDomain {
    MIND,
    BODY,
}
