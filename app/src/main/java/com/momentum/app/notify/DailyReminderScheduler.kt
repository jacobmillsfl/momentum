package com.momentum.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.momentum.app.data.repo.KvKeys
import com.momentum.app.data.repo.MomentumRepository
import java.util.Calendar

/**
 * Schedules morning and evening inexact daily alarms when notifications are enabled.
 */
object DailyReminderScheduler {
    private const val REQUEST_CODE_MORNING = 0x4D05
    private const val REQUEST_CODE_EVENING = 0x4D06

    suspend fun schedule(context: Context, repo: MomentumRepository) {
        MomentumNotificationChannels.ensure(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelSlot(context, alarmManager, DailyReminderReceiver.KIND_MORNING, REQUEST_CODE_MORNING)
        cancelSlot(context, alarmManager, DailyReminderReceiver.KIND_EVENING, REQUEST_CODE_EVENING)

        if (repo.getKv(KvKeys.NOTIFICATIONS_ENABLED) != "true") return

        val morning = parseTime(repo.getKv(KvKeys.NOTIFICATION_TIME) ?: "09:00")
        val evening = parseTime(repo.getKv(KvKeys.NOTIFICATION_EVENING_TIME) ?: "20:00")
        scheduleSlot(
            context,
            alarmManager,
            DailyReminderReceiver.KIND_MORNING,
            REQUEST_CODE_MORNING,
            morning.first,
            morning.second,
        )
        scheduleSlot(
            context,
            alarmManager,
            DailyReminderReceiver.KIND_EVENING,
            REQUEST_CODE_EVENING,
            evening.first,
            evening.second,
        )
    }

    private fun parseTime(timeStr: String): Pair<Int, Int> {
        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return hour to minute
    }

    private fun cancelSlot(
        context: Context,
        alarmManager: AlarmManager,
        kind: String,
        requestCode: Int,
    ) {
        val pending = pendingIntent(context, kind, requestCode)
        alarmManager.cancel(pending)
    }

    private fun scheduleSlot(
        context: Context,
        alarmManager: AlarmManager,
        kind: String,
        requestCode: Int,
        hour: Int,
        minute: Int,
    ) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val pending = pendingIntent(context, kind, requestCode)
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pending,
        )
    }

    private fun pendingIntent(context: Context, kind: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            action = DailyReminderReceiver.ACTION
            putExtra(DailyReminderReceiver.EXTRA_KIND, kind)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }
}
