package com.momentum.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object MomentumNotificationChannels {
    const val DAILY_REMINDER_ID = "momentum_daily_reminder"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            DAILY_REMINDER_ID,
            "Daily reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Reminder to log habits and sessions"
        }
        nm.createNotificationChannel(channel)
    }
}
