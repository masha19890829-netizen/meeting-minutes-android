package com.meetingminutes.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.meetingminutes.app.worker.InsightsWorker
import java.util.concurrent.TimeUnit

class MeetingMinutesApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        val dailyInsights = PeriodicWorkRequestBuilder<InsightsWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily-meeting-insights",
            ExistingPeriodicWorkPolicy.UPDATE,
            dailyInsights
        )
    }
}

