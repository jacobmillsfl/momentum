package com.momentum.app.domain

import java.util.Calendar
import java.util.TimeZone

/** ISO weekday: 1 = Monday … 7 = Sunday. */
fun isoDayOfWeek(cal: Calendar): Int {
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    return when (dow) {
        Calendar.SUNDAY -> 7
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        else -> 1
    }
}

fun startOfDayUtcMillis(zone: TimeZone = TimeZone.getDefault()): Long {
    val cal = Calendar.getInstance(zone)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

const val SESSION_GENERATION_WEEKS = 8

data class SessionOccurrence(
    val dayStartMs: Long,
    val rule: RecurrenceDayRule,
)

/**
 * Builds planned session occurrences from today through [weeksAhead] weeks,
 * one row per matching weekday (no days before today).
 */
fun buildOccurrencesFromToday(
    recurrence: List<RecurrenceDayRule>,
    weeksAhead: Int,
    zone: TimeZone = TimeZone.getDefault(),
): List<SessionOccurrence> {
    val ruleByIso = recurrence.associateBy { it.dayOfWeek }
    val startToday = startOfDayUtcMillis(zone)
    val cal = Calendar.getInstance(zone)
    cal.timeInMillis = startToday
    val endCal = Calendar.getInstance(zone)
    endCal.timeInMillis = startToday
    endCal.add(Calendar.DAY_OF_MONTH, weeksAhead * 7)

    val out = ArrayList<SessionOccurrence>()
    while (!cal.after(endCal)) {
        val iso = isoDayOfWeek(cal)
        val rule = ruleByIso[iso]
        if (rule != null) {
            val dayStart = cal.timeInMillis
            if (dayStart >= startToday) {
                out.add(SessionOccurrence(dayStart, rule))
            }
        }
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return out
}
