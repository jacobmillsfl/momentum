package com.momentum.app

import android.app.Application
import androidx.room.Room
import com.momentum.app.data.local.MomentumDatabase
import com.momentum.app.data.repo.MomentumRepository
import com.momentum.app.notify.DailyReminderScheduler
import com.momentum.app.notify.MomentumNotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MomentumApp : Application() {
    lateinit var database: MomentumDatabase
        private set
    lateinit var repository: MomentumRepository
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        MomentumNotificationChannels.ensure(this)
        database = Room.databaseBuilder(
            applicationContext,
            MomentumDatabase::class.java,
            "momentum.db",
        ).fallbackToDestructiveMigration()
            .build()
        repository = MomentumRepository(database)
        appScope.launch(Dispatchers.IO) {
            repository.seedKvDefaults()
            DailyReminderScheduler.schedule(this@MomentumApp, repository)
        }
    }

    fun rescheduleNotificationsFromSettings() {
        appScope.launch(Dispatchers.IO) {
            DailyReminderScheduler.schedule(this@MomentumApp, repository)
        }
    }
}
