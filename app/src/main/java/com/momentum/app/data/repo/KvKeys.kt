package com.momentum.app.data.repo

object KvKeys {
    const val STREAK_CURRENT = "streak.current"
    const val STREAK_LONGEST = "streak.longest"
    const val STREAK_LAST_ACTIVE = "streak.last_active_date"
    const val STREAK_PERFECT_CURRENT = "streak.perfect.current"
    const val STREAK_PERFECT_LONGEST = "streak.perfect.longest"
    const val STREAK_PERFECT_LAST_ACTIVE = "streak.perfect.last_active_date"
    const val FREEZES_ALLOWED = "freezes.allowed_per_month"
    const val FREEZES_USED = "freezes.used"
    const val FREEZES_MONTH = "freezes.month_key"
    /** ISO date (yyyy-MM-dd) when a freeze was applied for misses; one per day max. */
    const val FREEZE_LAST_APPLIED_DAY = "freezes.last_applied_day"
    /** system | light | dark */
    const val THEME_MODE = "ui.theme_mode"
    /** "true" | "false" */
    const val NOTIFICATIONS_ENABLED = "notifications.enabled"
    /** Local time HH:mm (24h) */
    const val NOTIFICATION_TIME = "notifications.time"
    /** Optional target body weight in lbs for the weight chart; empty if unset */
    const val TARGET_WEIGHT_LBS = "health.target_weight_lbs"
    /** Optional starting weight in lbs; red dashed line on chart */
    const val STARTING_WEIGHT_LBS = "health.starting_weight_lbs"

    /** Max value for [FREEZES_ALLOWED] and streak-freeze UI. */
    const val MAX_STREAK_FREEZES_PER_MONTH = 5
}
