package com.momentum.app.data.streak

import com.momentum.app.data.local.dao.HabitDao
import com.momentum.app.data.local.dao.KvDao
import com.momentum.app.data.local.dao.LogDao
import com.momentum.app.data.local.dao.SessionDao
import com.momentum.app.data.local.entity.KvEntity
import com.momentum.app.data.repo.GoodBadSnapshot
import com.momentum.app.data.repo.KvKeys
import com.momentum.app.domain.millisToLocalDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.TimeZone

/**
 * Normal streak: consecutive qualifying days with more good than bad (freezes apply to positive misses).
 * Perfect streak: consecutive days with only good activity (bad == 0, good > 0). Freezes never apply.
 */
class StreakHelper(
    private val sessionDao: SessionDao,
    private val habitDao: HabitDao,
    private val logDao: LogDao,
    private val kvDao: KvDao,
) {
    private val zone: TimeZone get() = TimeZone.getDefault()

    suspend fun ensureFreezeMonthReset() {
        val ymNow = YearMonth.now().toString()
        val stored = kvDao.get(KvKeys.FREEZES_MONTH) ?: return
        if (stored != ymNow) {
            kvDao.upsert(KvEntity(KvKeys.FREEZES_MONTH, ymNow))
            kvDao.upsert(KvEntity(KvKeys.FREEZES_USED, "0"))
            kvDao.upsert(KvEntity(KvKeys.FREEZE_LAST_APPLIED_DAY, ""))
        }
    }

    suspend fun onDaySettledForMillis(dayStartMs: Long) {
        onDaySettled(millisToLocalDate(dayStartMs, zone))
    }

    suspend fun onDaySettled(date: LocalDate) {
        ensureFreezeMonthReset()
        val today = LocalDate.now()
        if (date.isAfter(today)) return

        val snap = computeGoodBadForDay(
            date = date,
            today = today,
            calendarMode = false,
            sessionDao = sessionDao,
            habitDao = habitDao,
            logDao = logDao,
            zone = zone,
        )
        if (snap.hasPlannedScheduledSessions) return

        if (snap.good == 0.0 && snap.bad == 0.0) return

        if (snap.bad == 0.0 && snap.good > 0.0) {
            applyPerfectSuccess(date)
        } else {
            resetPerfectStreak()
        }

        val normalSuccess = snap.good > snap.bad
        if (normalSuccess) {
            applyNormalSuccess(date)
        } else {
            val dayStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val lastApplied = kvDao.get(KvKeys.FREEZE_LAST_APPLIED_DAY).orEmpty()
            if (snap.anyPositiveMissedScheduled && lastApplied != dayStr) {
                val allowed = (kvDao.get(KvKeys.FREEZES_ALLOWED)?.toIntOrNull() ?: 3)
                    .coerceIn(0, KvKeys.MAX_STREAK_FREEZES_PER_MONTH)
                val used = kvDao.get(KvKeys.FREEZES_USED)?.toIntOrNull() ?: 0
                if (used < allowed) {
                    kvDao.upsert(KvEntity(KvKeys.FREEZES_USED, (used + 1).toString()))
                    kvDao.upsert(KvEntity(KvKeys.FREEZE_LAST_APPLIED_DAY, dayStr))
                } else {
                    resetNormalStreak()
                }
            } else {
                resetNormalStreak()
            }
        }
    }

    private suspend fun resetNormalStreak() {
        kvDao.upsert(KvEntity(KvKeys.STREAK_CURRENT, "0"))
        kvDao.upsert(KvEntity(KvKeys.STREAK_LAST_ACTIVE, ""))
    }

    private suspend fun resetPerfectStreak() {
        kvDao.upsert(KvEntity(KvKeys.STREAK_PERFECT_CURRENT, "0"))
        kvDao.upsert(KvEntity(KvKeys.STREAK_PERFECT_LAST_ACTIVE, ""))
    }

    private suspend fun applyNormalSuccess(day: LocalDate) {
        val lastStr = kvDao.get(KvKeys.STREAK_LAST_ACTIVE)?.takeIf { it.isNotBlank() }
        val current = kvDao.get(KvKeys.STREAK_CURRENT)?.toIntOrNull() ?: 0
        var longest = kvDao.get(KvKeys.STREAK_LONGEST)?.toIntOrNull() ?: 0

        if (lastStr != null) {
            val last = LocalDate.parse(lastStr)
            if (day == last) return
        }

        val newStreak = if (lastStr == null) {
            1
        } else {
            val last = LocalDate.parse(lastStr)
            if (canContinueNormalFrom(last, day)) {
                current + 1
            } else {
                1
            }
        }
        if (newStreak > longest) longest = newStreak
        kvDao.upsert(KvEntity(KvKeys.STREAK_CURRENT, newStreak.toString()))
        kvDao.upsert(KvEntity(KvKeys.STREAK_LONGEST, longest.toString()))
        kvDao.upsert(KvEntity(KvKeys.STREAK_LAST_ACTIVE, day.toString()))
    }

    private suspend fun applyPerfectSuccess(day: LocalDate) {
        val lastStr = kvDao.get(KvKeys.STREAK_PERFECT_LAST_ACTIVE)?.takeIf { it.isNotBlank() }
        val current = kvDao.get(KvKeys.STREAK_PERFECT_CURRENT)?.toIntOrNull() ?: 0
        var longest = kvDao.get(KvKeys.STREAK_PERFECT_LONGEST)?.toIntOrNull() ?: 0

        if (lastStr != null) {
            val last = LocalDate.parse(lastStr)
            if (day == last) return
        }

        val newStreak = if (lastStr == null) {
            1
        } else {
            val last = LocalDate.parse(lastStr)
            if (canContinuePerfectFrom(last, day)) {
                current + 1
            } else {
                1
            }
        }
        if (newStreak > longest) longest = newStreak
        kvDao.upsert(KvEntity(KvKeys.STREAK_PERFECT_CURRENT, newStreak.toString()))
        kvDao.upsert(KvEntity(KvKeys.STREAK_PERFECT_LONGEST, longest.toString()))
        kvDao.upsert(KvEntity(KvKeys.STREAK_PERFECT_LAST_ACTIVE, day.toString()))
    }

    private fun failureDay(s: GoodBadSnapshot): Boolean =
        s.good <= s.bad && s.good + s.bad > 0

    private suspend fun canContinueNormalFrom(lastSuccess: LocalDate, day: LocalDate): Boolean {
        if (day <= lastSuccess) return false
        if (day == lastSuccess.plusDays(1)) return true
        var cursor = lastSuccess.plusDays(1)
        while (cursor.isBefore(day)) {
            val snap = computeGoodBadForDay(
                date = cursor,
                today = LocalDate.now(),
                calendarMode = false,
                sessionDao = sessionDao,
                habitDao = habitDao,
                logDao = logDao,
                zone = zone,
            )
            if (snap.hasPlannedScheduledSessions) return false
            if (failureDay(snap)) return false
            cursor = cursor.plusDays(1)
        }
        return true
    }

    /** Gap days must be neutral (no scored activity) between two perfect-only-good days. */
    private suspend fun canContinuePerfectFrom(lastSuccess: LocalDate, day: LocalDate): Boolean {
        if (day <= lastSuccess) return false
        if (day == lastSuccess.plusDays(1)) return true
        var cursor = lastSuccess.plusDays(1)
        while (cursor.isBefore(day)) {
            val snap = computeGoodBadForDay(
                date = cursor,
                today = LocalDate.now(),
                calendarMode = false,
                sessionDao = sessionDao,
                habitDao = habitDao,
                logDao = logDao,
                zone = zone,
            )
            if (snap.hasPlannedScheduledSessions) return false
            if (snap.good != 0.0 || snap.bad != 0.0) return false
            cursor = cursor.plusDays(1)
        }
        return true
    }
}
