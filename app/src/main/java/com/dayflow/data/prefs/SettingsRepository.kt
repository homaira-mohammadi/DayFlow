package com.dayflow.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import com.dayflow.domain.CycleConfig
import com.dayflow.domain.Gender
import com.dayflow.security.AppLockMode
import java.time.DayOfWeek

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val name: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Int = 0,
    val dynamicColor: Boolean = false,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val use24h: Boolean = false,
    /** Whether reaching a habit's minimum version counts as done for streaks and completion. */
    val minimumCounts: Boolean = true,
    val notifications: Boolean = true,
    val morningPlanMinutes: Int? = 8 * 60,
    val eveningReviewMinutes: Int? = 21 * 60,
    /** Optional; null means the user chose not to say, and nothing is stored. */
    val gender: Gender? = null,
    /** Shows the Cycle section. Controlled only by the user, never by the gender choice. */
    val cycleTracking: Boolean = false,
    /** Used for predictions until enough cycles are logged. */
    val cycleLength: Int = 28,
    val periodLength: Int = 5,
    /** How many days before a period count as PMS days. */
    val pmsDays: Int = 5,
    val showFertility: Boolean = true,
    val cycleReminders: Boolean = true,
    /** Off by default: cycle information stays inside the Cycle section unless the user opts in. */
    val cycleHints: Boolean = false,
    /** The first-run welcome screen has been completed or skipped. */
    val onboarded: Boolean = false,
    val appLock: AppLockMode = AppLockMode.OFF,
    /** Shows the five daily prayers card on Today. */
    val prayerTracking: Boolean = true,
    /** The first-run app tour has been seen or skipped. */
    val tourSeen: Boolean = false,
) {
    val cycleConfig: CycleConfig get() = CycleConfig(cycleLength, periodLength, pmsDays)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val name = stringPreferencesKey("name")
        val theme = stringPreferencesKey("theme")
        val accent = intPreferencesKey("accent")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val weekStart = intPreferencesKey("week_start")
        val use24h = booleanPreferencesKey("use_24h")
        val minimumCounts = booleanPreferencesKey("minimum_counts")
        val notifications = booleanPreferencesKey("notifications")
        val morning = intPreferencesKey("morning_plan")
        val evening = intPreferencesKey("evening_review")
        val gender = stringPreferencesKey("gender")
        val cycleTracking = booleanPreferencesKey("cycle_tracking")
        val cycleLength = intPreferencesKey("cycle_length")
        val periodLength = intPreferencesKey("period_length")
        val pmsDays = intPreferencesKey("pms_days")
        val showFertility = booleanPreferencesKey("show_fertility")
        val cycleReminders = booleanPreferencesKey("cycle_reminders")
        val cycleHints = booleanPreferencesKey("cycle_hints")
        val onboarded = booleanPreferencesKey("onboarded")
        val appLock = stringPreferencesKey("app_lock")
        val prayerTracking = booleanPreferencesKey("prayer_tracking")
        val tourSeen = booleanPreferencesKey("tour_seen")
    }

    val settings: Flow<UserSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map(::read)

    suspend fun current(): UserSettings = settings.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        store.edit { p ->
            val s = transform(read(p))
            val d = UserSettings()
            // Only values that differ from the defaults are written, so nothing personal is stored until chosen.
            p.putOrRemove(Keys.name, s.name.ifBlank { null }, null)
            p.putOrRemove(Keys.theme, s.themeMode.name, d.themeMode.name)
            p.putOrRemove(Keys.accent, s.accent, d.accent)
            p.putOrRemove(Keys.dynamic, s.dynamicColor, d.dynamicColor)
            p.putOrRemove(Keys.weekStart, s.weekStart.value, d.weekStart.value)
            p.putOrRemove(Keys.use24h, s.use24h, d.use24h)
            p.putOrRemove(Keys.minimumCounts, s.minimumCounts, d.minimumCounts)
            p.putOrRemove(Keys.notifications, s.notifications, d.notifications)
            p.putOrRemove(Keys.morning, s.morningPlanMinutes ?: -1, d.morningPlanMinutes ?: -1)
            p.putOrRemove(Keys.evening, s.eveningReviewMinutes ?: -1, d.eveningReviewMinutes ?: -1)
            p.putOrRemove(Keys.gender, s.gender?.name, null)
            p.putOrRemove(Keys.cycleTracking, s.cycleTracking, d.cycleTracking)
            p.putOrRemove(Keys.cycleLength, s.cycleLength, d.cycleLength)
            p.putOrRemove(Keys.periodLength, s.periodLength, d.periodLength)
            p.putOrRemove(Keys.pmsDays, s.pmsDays, d.pmsDays)
            p.putOrRemove(Keys.showFertility, s.showFertility, d.showFertility)
            p.putOrRemove(Keys.cycleReminders, s.cycleReminders, d.cycleReminders)
            p.putOrRemove(Keys.cycleHints, s.cycleHints, d.cycleHints)
            p.putOrRemove(Keys.onboarded, s.onboarded, d.onboarded)
            p.putOrRemove(Keys.appLock, s.appLock.name, d.appLock.name)
            p.putOrRemove(Keys.prayerTracking, s.prayerTracking, d.prayerTracking)
            p.putOrRemove(Keys.tourSeen, s.tourSeen, d.tourSeen)
        }
    }

    private fun <T> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?, default: T?) {
        if (value == null || value == default) remove(key) else this[key] = value
    }

    private fun read(p: Preferences): UserSettings {
        val d = UserSettings()
        return UserSettings(
            name = p[Keys.name] ?: d.name,
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            accent = p[Keys.accent] ?: d.accent,
            dynamicColor = p[Keys.dynamic] ?: d.dynamicColor,
            weekStart = p[Keys.weekStart]?.let(DayOfWeek::of) ?: d.weekStart,
            use24h = p[Keys.use24h] ?: d.use24h,
            minimumCounts = p[Keys.minimumCounts] ?: d.minimumCounts,
            notifications = p[Keys.notifications] ?: d.notifications,
            morningPlanMinutes = p[Keys.morning].let { if (it == null) d.morningPlanMinutes else it.takeIf { m -> m >= 0 } },
            eveningReviewMinutes = p[Keys.evening].let { if (it == null) d.eveningReviewMinutes else it.takeIf { m -> m >= 0 } },
            gender = p[Keys.gender]?.let { runCatching { Gender.valueOf(it) }.getOrNull() },
            cycleTracking = p[Keys.cycleTracking] ?: d.cycleTracking,
            cycleLength = p[Keys.cycleLength] ?: d.cycleLength,
            periodLength = p[Keys.periodLength] ?: d.periodLength,
            pmsDays = p[Keys.pmsDays] ?: d.pmsDays,
            showFertility = p[Keys.showFertility] ?: d.showFertility,
            cycleReminders = p[Keys.cycleReminders] ?: d.cycleReminders,
            cycleHints = p[Keys.cycleHints] ?: d.cycleHints,
            onboarded = p[Keys.onboarded] ?: d.onboarded,
            appLock = p[Keys.appLock]?.let { runCatching { AppLockMode.valueOf(it) }.getOrNull() } ?: d.appLock,
            prayerTracking = p[Keys.prayerTracking] ?: d.prayerTracking,
            tourSeen = p[Keys.tourSeen] ?: d.tourSeen,
        )
    }
}
