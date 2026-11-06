package com.dayflow.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dayflow.DayFlowApp

/** Alarms don't survive reboots or clock changes; re-arm them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as DayFlowApp).container
        container.reminders.refreshAll()
        container.focus.rearm()
    }
}
