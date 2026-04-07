package com.momentum.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.momentum.app.MomentumApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? MomentumApp ?: return
        scope.launch(Dispatchers.IO) {
            DailyReminderScheduler.schedule(context.applicationContext, app.repository)
        }
    }
}
