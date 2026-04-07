package com.momentum.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.momentum.app.data.repo.KvKeys
import com.momentum.app.data.repo.MomentumRepository
import java.util.Calendar

/**
 * Schedules a daily inexact alarm at the user-selected local time when notifications are enabled.
 */
object DailyReminderScheduler {
    private const val REQUEST_CODE = 0x4D05

    suspend fun schedule(context: Context, repo: MomentumRepository) {
        MomentumNotificationChannels.ensure(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            action = DailyReminderReceiver.ACTION
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
        alarmManager.cancel(pending)

        if (repo.getKv(KvKeys.NOTIFICATIONS_ENABLED) != "true") return

        val timeStr = repo.getKv(KvKeys.NOTIFICATION_TIME) ?: "09:00"
        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pending,
        )
    }
}
