package com.dayflow

import android.content.Context
import com.dayflow.data.db.DayFlowDatabase
import com.dayflow.data.prefs.SettingsRepository
import com.dayflow.data.repo.BackupRepository
import com.dayflow.data.repo.CycleRepository
import com.dayflow.data.repo.GoalRepository
import com.dayflow.data.repo.HabitRepository
import com.dayflow.data.repo.PhotoStore
import com.dayflow.data.repo.PlanRepository
import com.dayflow.data.repo.PrayerRepository
import com.dayflow.data.repo.ReflectionRepository
import com.dayflow.data.repo.TaskRepository
import com.dayflow.focus.FocusController
import com.dayflow.security.AppLock
import com.dayflow.notifications.ReminderScheduler

/** Hand-written dependency container; small enough that a DI framework isn't needed yet. */
class AppContainer(context: Context) {
    val db = DayFlowDatabase.build(context)
    val settings = SettingsRepository(context)
    val reminders = ReminderScheduler(context, db, settings)
    val habits = HabitRepository(db, reminders)
    val tasks = TaskRepository(db, reminders)
    val plans = PlanRepository(db)
    val photos = PhotoStore(context)
    val backup = BackupRepository(db, photos)
    val reflections = ReflectionRepository(db, photos)
    val cycle = CycleRepository(db)
    val prayers = PrayerRepository(db)
    val goals = GoalRepository(db)
    val appLock = AppLock()
    val focus = FocusController(context, db, habits, reminders)
}
