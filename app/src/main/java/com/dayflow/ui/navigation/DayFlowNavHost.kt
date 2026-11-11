package com.dayflow.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dayflow.DayFlowApp
import com.dayflow.ui.components.LockGate
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dayflow.ui.calendar.CalendarScreen
import com.dayflow.ui.cycle.CycleCalendarScreen
import com.dayflow.ui.cycle.CycleScreen
import com.dayflow.ui.focus.FocusHistoryScreen
import com.dayflow.ui.focus.FocusScreen
import com.dayflow.ui.goals.GoalDetailScreen
import com.dayflow.ui.goals.GoalEditorScreen
import com.dayflow.ui.goals.GoalsScreen
import com.dayflow.ui.habits.HabitDetailScreen
import com.dayflow.ui.habits.HabitEditorScreen
import com.dayflow.ui.habits.HabitsScreen
import com.dayflow.ui.prayer.PrayerHistoryScreen
import com.dayflow.ui.progress.ProgressScreen
import com.dayflow.ui.sleep.SleepHistoryScreen
import com.dayflow.ui.reflection.ReflectionEditorScreen
import com.dayflow.ui.reflection.ReflectionHistoryScreen
import com.dayflow.ui.settings.SettingsScreen
import com.dayflow.ui.tasks.TaskEditorScreen
import com.dayflow.ui.today.TodayScreen
import java.time.LocalDate

private enum class TopLevel(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Filled.WbSunny),
    CALENDAR("calendar", "Calendar", Icons.Filled.CalendarMonth),
    FOCUS("focus", "Focus", Icons.Filled.Timer),
    HABITS("habits", "Habits", Icons.Filled.Loop),
    PROGRESS("progress", "Progress", Icons.Filled.Insights),
    CYCLE("cycle", "Cycle", Icons.Filled.WaterDrop),
}

private object Routes {
    const val HABIT_DETAIL = "habit/{id}"
    const val HABIT_EDIT = "habit-edit?id={id}&goal={goal}"
    const val TASK_EDIT = "task-edit?id={id}&date={date}&goal={goal}"
    const val GOALS = "goals"
    const val GOAL_DETAIL = "goal/{id}"
    const val GOAL_EDIT = "goal-edit?id={id}"
    const val REFLECTION_EDIT = "reflection?date={date}"
    const val REFLECTIONS = "reflections"
    const val SETTINGS = "settings"
    const val FOCUS_HISTORY = "focus-history"
    const val CYCLE_CALENDAR = "cycle-calendar"
    const val PRAYER_HISTORY = "prayer-history"
    const val SLEEP_HISTORY = "sleep-history"

    fun habit(id: Long) = "habit/$id"
    /** [goal] links a new habit to that goal. */
    fun editHabit(id: Long = -1, goal: Long = -1) = "habit-edit?id=$id&goal=$goal"
    fun goal(id: Long) = "goal/$id"
    fun editGoal(id: Long = -1) = "goal-edit?id=$id"
    fun reflection(date: LocalDate) = "reflection?date=${date.toEpochDay()}"
    /** [goal] links a new task to that goal. */
    fun editTask(id: Long = -1, date: LocalDate? = null, goal: Long = -1) =
        "task-edit?id=$id&date=${date?.toEpochDay() ?: -1}&goal=$goal"
}

private fun NavHostController.navigateToTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun DayFlowNavHost(cycleEnabled: Boolean, lockPrivate: Boolean, nav: NavHostController = rememberNavController()) {
    val appLock = (LocalContext.current.applicationContext as DayFlowApp).container.appLock
    val unlocked by appLock.unlocked.collectAsStateWithLifecycle()
    // With "Journal & Cycle" locking, private details stay hidden on shared screens until unlocked.
    val hidePrivate = lockPrivate && !unlocked
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    // The Cycle tab only exists for people who turned cycle tracking on.
    val tabs = TopLevel.entries.filter { it != TopLevel.CYCLE || cycleEnabled }
    val showBar = tabs.any { it.route == route }
    // Turning tracking off while a Cycle page is open leaves it, so no cycle data stays on screen.
    LaunchedEffect(cycleEnabled, route) {
        if (!cycleEnabled && (route == TopLevel.CYCLE.route || route == Routes.CYCLE_CALENDAR)) {
            nav.navigate(TopLevel.TODAY.route) {
                popUpTo(nav.graph.findStartDestination().id) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { dest ->
                    NavigationBarItem(
                        selected = route == dest.route,
                        onClick = { nav.navigateToTab(dest.route) },
                        icon = { Icon(dest.icon, null) },
                        // Six tabs leave little room, so use a smaller label that never gets cut off.
                        label = {
                            Text(
                                dest.label,
                                maxLines = 1,
                                softWrap = false,
                                style = if (tabs.size > 5) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                            )
                        },
                        alwaysShowLabel = tabs.size <= 5,
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = TopLevel.TODAY.route, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable(TopLevel.TODAY.route) {
                TodayScreen(
                    onAddTask = { nav.navigate(Routes.editTask(date = it)) },
                    onAddHabit = { nav.navigate(Routes.editHabit()) },
                    onOpenTask = { nav.navigate(Routes.editTask(it)) },
                    onOpenReflection = { nav.navigate(Routes.reflection(it)) },
                    onOpenReflections = { nav.navigate(Routes.REFLECTIONS) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenFocus = { nav.navigateToTab(TopLevel.FOCUS.route) },
                    onOpenCycle = { nav.navigateToTab(TopLevel.CYCLE.route) },
                    onOpenPrayerHistory = { nav.navigate(Routes.PRAYER_HISTORY) },
                    onOpenSleepHistory = { nav.navigate(Routes.SLEEP_HISTORY) },
                    hidePrivate = hidePrivate,
                )
            }
            composable(TopLevel.CYCLE.route) {
                LockGate(lockPrivate, "Unlock Cycle") { CycleScreen(onOpenCalendar = { nav.navigate(Routes.CYCLE_CALENDAR) }) }
            }
            composable(Routes.CYCLE_CALENDAR) { LockGate(lockPrivate, "Unlock Cycle") { CycleCalendarScreen(onBack = { nav.popBackStack() }) } }
            composable(TopLevel.CALENDAR.route) {
                CalendarScreen(
                    onAddTask = { nav.navigate(Routes.editTask(date = it)) },
                    onOpenTask = { nav.navigate(Routes.editTask(it)) },
                    onOpenFocus = { nav.navigateToTab(TopLevel.FOCUS.route) },
                )
            }
            composable(TopLevel.FOCUS.route) { FocusScreen(onOpenHistory = { nav.navigate(Routes.FOCUS_HISTORY) }) }
            composable(Routes.SLEEP_HISTORY) { SleepHistoryScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.PRAYER_HISTORY) { PrayerHistoryScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.FOCUS_HISTORY) { FocusHistoryScreen(onBack = { nav.popBackStack() }) }
            composable(TopLevel.HABITS.route) {
                HabitsScreen(
                    onAddHabit = { nav.navigate(Routes.editHabit()) },
                    onOpenHabit = { nav.navigate(Routes.habit(it)) },
                    onEditHabit = { nav.navigate(Routes.editHabit(it)) },
                )
            }
            composable(TopLevel.PROGRESS.route) {
                ProgressScreen(
                    onOpenGoals = { nav.navigate(Routes.GOALS) },
                    onOpenGoal = { nav.navigate(Routes.goal(it)) },
                    onAddGoal = { nav.navigate(Routes.editGoal()) },
                )
            }
            composable(Routes.GOALS) {
                GoalsScreen(
                    onBack = { nav.popBackStack() },
                    onAddGoal = { nav.navigate(Routes.editGoal()) },
                    onOpenGoal = { nav.navigate(Routes.goal(it)) },
                    onEditGoal = { nav.navigate(Routes.editGoal(it)) },
                )
            }
            composable(Routes.GOAL_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                GoalDetailScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.editGoal(it)) },
                    onNewTask = { nav.navigate(Routes.editTask(goal = it)) },
                    onNewHabit = { nav.navigate(Routes.editHabit(goal = it)) },
                    onOpenTask = { nav.navigate(Routes.editTask(it)) },
                    onOpenHabit = { nav.navigate(Routes.habit(it)) },
                )
            }
            composable(
                Routes.GOAL_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
            ) {
                GoalEditorScreen(
                    onClose = { nav.popBackStack() },
                    onSaved = { id, isNew ->
                        nav.popBackStack()
                        // A new goal opens straight away, ready for its milestones, tasks and habits.
                        if (isNew) nav.navigate(Routes.goal(id))
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { nav.popBackStack() }, onOpenReflections = { nav.navigate(Routes.REFLECTIONS) })
            }

            composable(Routes.HABIT_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                HabitDetailScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.editHabit(it)) })
            }
            composable(
                Routes.HABIT_EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("goal") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) {
                HabitEditorScreen(onClose = { nav.popBackStack() })
            }
            composable(
                Routes.TASK_EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("date") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("goal") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) {
                TaskEditorScreen(onClose = { nav.popBackStack() })
            }
            composable(
                Routes.REFLECTION_EDIT,
                arguments = listOf(navArgument("date") { type = NavType.LongType; defaultValue = -1L }),
            ) {
                LockGate(lockPrivate, "Unlock journal") { ReflectionEditorScreen(onClose = { nav.popBackStack() }) }
            }
            composable(Routes.REFLECTIONS) {
                LockGate(lockPrivate, "Unlock journal") {
                    ReflectionHistoryScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.reflection(it)) })
                }
            }
        }
    }
}
