package com.momentum.app.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.momentum.app.MainActivity
import com.momentum.app.MomentumApp
import com.momentum.app.R as AppR
import com.momentum.app.data.repo.KvKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_MORNING
        val app = context.applicationContext as? MomentumApp ?: return
        val pending = goAsync()
        app.appScope.launch(Dispatchers.IO) {
            try {
                val repo = app.repository
                if (repo.getKv(KvKeys.NOTIFICATIONS_ENABLED) != "true") return@launch
                if (repo.hasAnyHabitActivityToday()) return@launch

                MomentumNotificationChannels.ensure(context)
                val launch = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val contentPi = PendingIntent.getActivity(
                    context,
                    0,
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                val (titleRes, textRes, textArgs) = when (kind) {
                    KIND_EVENING -> {
                        val streak = repo.getKv(KvKeys.STREAK_CURRENT)?.toIntOrNull() ?: 0
                        if (streak > 0) {
                            Triple(
                                AppR.string.notification_evening_title,
                                AppR.string.notification_evening_streak_text,
                                arrayOf(streak),
                            )
                        } else {
                            Triple(
                                AppR.string.notification_evening_title,
                                AppR.string.notification_evening_text,
                                emptyArray(),
                            )
                        }
                    }
                    else -> Triple(
                        AppR.string.notification_morning_title,
                        AppR.string.notification_morning_text,
                        emptyArray(),
                    )
                }

                val notificationId = if (kind == KIND_EVENING) {
                    NOTIFICATION_ID_EVENING
                } else {
                    NOTIFICATION_ID_MORNING
                }

                val notification = NotificationCompat.Builder(
                    context,
                    MomentumNotificationChannels.DAILY_REMINDER_ID,
                )
                    .setSmallIcon(AppR.mipmap.ic_launcher)
                    .setContentTitle(context.getString(titleRes))
                    .setContentText(
                        if (textArgs.isEmpty()) {
                            context.getString(textRes)
                        } else {
                            context.getString(textRes, *textArgs)
                        },
                    )
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(contentPi)
                    .setAutoCancel(true)
                    .build()
                try {
                    NotificationManagerCompat.from(context).notify(notificationId, notification)
                } catch (_: SecurityException) {
                    // POST_NOTIFICATIONS denied
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.momentum.app.action.DAILY_REMINDER"
        const val EXTRA_KIND = "kind"
        const val KIND_MORNING = "morning"
        const val KIND_EVENING = "evening"
        private const val NOTIFICATION_ID_MORNING = 1001
        private const val NOTIFICATION_ID_EVENING = 1002
    }
}
