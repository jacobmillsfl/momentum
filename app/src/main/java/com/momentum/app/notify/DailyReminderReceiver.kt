package com.momentum.app.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.momentum.app.MainActivity
import com.momentum.app.R as AppR

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
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
        val notification = NotificationCompat.Builder(context, MomentumNotificationChannels.DAILY_REMINDER_ID)
            .setSmallIcon(AppR.mipmap.ic_launcher)
            .setContentTitle(context.getString(AppR.string.notification_daily_title))
            .setContentText(context.getString(AppR.string.notification_daily_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied
        }
    }

    companion object {
        const val ACTION = "com.momentum.app.action.DAILY_REMINDER"
        private const val NOTIFICATION_ID = 1001
    }
}
