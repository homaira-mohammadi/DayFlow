package com.dayflow.domain

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

enum class TaskStatus { PENDING, DONE, CANCELLED }

enum class HabitStatus { ACTIVE, PAUSED, ARCHIVED }

/** How a habit is measured: a simple yes/no, a count (pages, glasses), or minutes. */
enum class HabitGoalType { CHECK, QUANTITY, DURATION }

enum class DaySection(val label: String) {
    MORNING("Morning"),
    AFTERNOON("Afternoon"),
    EVENING("Evening"),
    ANYTIME("Anytime");

    companion object {
        fun forTime(minutes: Int?): DaySection = when {
            minutes == null -> ANYTIME
            minutes < 12 * 60 -> MORNING
            minutes < 17 * 60 -> AFTERNOON
            else -> EVENING
        }
    }
}

/** Result of comparing a recorded value with a habit's full target and optional minimum version. */
enum class Achievement {
    NONE, PARTIAL, MINIMUM, FULL;

    fun counts(minimumCounts: Boolean): Boolean = this == FULL || (this == MINIMUM && minimumCounts)
}

/** Weekday bit mask: bit 0 = Monday ... bit 6 = Sunday. */
object Days {
    const val ALL = 0b1111111
    const val WEEKDAYS = 0b0011111
    const val WEEKENDS = 0b1100000

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun contains(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0

    fun toggle(mask: Int, day: DayOfWeek): Int = mask xor bit(day)

    fun orderedWeek(start: DayOfWeek): List<DayOfWeek> = (0L..6L).map { start.plus(it) }

    fun describe(mask: Int, weekStart: DayOfWeek): String = when (mask) {
        ALL -> "Every day"
        WEEKDAYS -> "Weekdays"
        WEEKENDS -> "Weekends"
        0 -> "No days"
        else -> orderedWeek(weekStart)
            .filter { contains(mask, it) }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
}

enum class Mood(val label: String, val emoji: String) {
    HAPPY("Happy", "😊"),
    CALM("Calm", "😌"),
    MOTIVATED("Motivated", "💪"),
    TIRED("Tired", "😴"),
    SAD("Sad", "😔"),
    ANXIOUS("Anxious", "😟"),
}
