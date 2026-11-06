package com.dayflow.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dayflow.MainActivity
import com.dayflow.R

data class NotificationAction(val label: String, val intent: PendingIntent)

object Notifier {
    private const val CHANNEL_ID = "reminders"
    private const val FOCUS_RUNNING = "focus_running"
    private const val FOCUS_DONE = "focus_done"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Habit, task, morning plan and evening review reminders"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(FOCUS_RUNNING, "Focus timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the running focus countdown"
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(FOCUS_DONE, "Focus timer finished", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts you when a focus session or break ends"
                enableVibration(true)
            },
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** A silent, ongoing notification with a live countdown; needs no service because the system draws the chronometer. */
    @SuppressLint("MissingPermission")
    fun showFocusRunning(context: Context, title: String, endAt: Long, actions: List<NotificationAction>) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val builder = NotificationCompat.Builder(context, FOCUS_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Stay with it. You'll get an alert when time is up.")
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(endAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        actions.forEach { builder.addAction(0, it.label, it.intent) }
        manager.notify(FOCUS_TAG, 0, builder.build())
    }

    @SuppressLint("MissingPermission")
    fun showFocusDone(context: Context, title: String, text: String, actions: List<NotificationAction>) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val builder = NotificationCompat.Builder(context, FOCUS_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        actions.forEach { builder.addAction(0, it.label, it.intent) }
        manager.notify(FOCUS_TAG, 0, builder.build())
    }

    const val FOCUS_TAG = "focus"

    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled()
    fun show(
        context: Context,
        tag: String,
        id: Int,
        title: String,
        text: String,
        actions: List<NotificationAction> = emptyList(),
        /** Hides the content on the lock screen, showing only a generic line. */
        sensitive: Boolean = false,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val open = openApp(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        actions.forEach { builder.addAction(0, it.label, it.intent) }
        if (sensitive) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            builder.setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Dayflow")
                    .setContentText("You have a private reminder")
                    .build(),
            )
        }
        manager.notify(tag, id, builder.build())
    }

    fun cancel(context: Context, tag: String, id: Int) {
        NotificationManagerCompat.from(context).cancel(tag, id)
    }
}
