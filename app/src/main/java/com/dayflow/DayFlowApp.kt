package com.dayflow

import android.app.Application
import com.dayflow.notifications.Notifier

class DayFlowApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
        container.reminders.refreshAll()
        container.focus.rearm()
    }
}
