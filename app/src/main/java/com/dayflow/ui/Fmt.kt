package com.dayflow.ui

import com.dayflow.domain.HabitGoalType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object Fmt {
    private val header = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
    private val short = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    private val month = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    fun time(minutes: Int, use24h: Boolean): String =
        LocalTime.of(minutes / 60, minutes % 60)
            .format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", Locale.getDefault()))

    fun dayHeader(date: LocalDate): String = date.format(header).uppercase(Locale.getDefault())

    fun shortDate(date: LocalDate): String = date.format(short)

    fun month(date: LocalDate): String = date.format(month)

    fun relative(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> shortDate(date)
    }

    fun number(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.getDefault(), "%.1f", v)

    fun amount(v: Double, type: HabitGoalType, unit: String): String = when (type) {
        HabitGoalType.CHECK -> if (v > 0) "Done" else "Not done"
        HabitGoalType.DURATION -> "${number(v)} min"
        HabitGoalType.QUANTITY -> if (unit.isBlank()) number(v) else "${number(v)} $unit"
    }

    /** "37.5%" style: one decimal only when it matters. */
    fun percent(fraction: Float): String {
        val p = (fraction * 1000).roundToInt() / 10.0
        return "${number(p)}%"
    }

    fun greeting(name: String, now: LocalTime = LocalTime.now()): String {
        val part = when (now.hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
        return if (name.isBlank()) part else "$part, $name"
    }
}
