package com.riyadmondol2006.taskfern.ui.common

import android.content.Context
import android.text.format.DateFormat
import com.riyadmondol2006.taskfern.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The locale of the current configuration, observed by Compose so text updates when the language changes. */
@androidx.compose.runtime.Composable
fun currentLocale(): Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]

/** Locale-aware, human date and time labels ("Today", "Tomorrow 9:00", "Fri 14 Nov"). */
object Formatters {

    fun time(context: Context, time: LocalTime): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return time.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    fun minutesOfDay(context: Context, minutes: Int): String = time(context, LocalTime.of(minutes / 60 % 24, minutes % 60))

    /** "Today", "Tomorrow", "Yesterday", weekday within the next 6 days, else a short date. */
    fun relativeDay(context: Context, date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> context.getString(R.string.today)
            days == 1L -> context.getString(R.string.tomorrow)
            days == -1L -> context.getString(R.string.yesterday)
            days in 2..6 -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            else -> shortDate(date, today)
        }
    }

    fun shortDate(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val skeleton = if (date.year == today.year) "EEE d MMM" else "d MMM yyyy"
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
        return date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    fun longDate(date: LocalDate): String {
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEE d MMMM")
        return date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    fun monthYear(date: LocalDate): String {
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMM yyyy")
        return date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    fun mediumDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    fun dueLabel(context: Context, date: LocalDate, time: LocalTime?, today: LocalDate = LocalDate.now()): String {
        val day = relativeDay(context, date, today)
        return if (time == null) day else "$day ${time(context, time)}"
    }

    /** "10 min before", "1 hour before", "1 day before", "At due time". */
    fun reminderOffset(context: Context, minutes: Int): String = when {
        minutes == 0 -> context.getString(R.string.reminder_at_due)
        minutes % (24 * 60) == 0 -> context.resources.getQuantityString(R.plurals.reminder_days_before, minutes / 1440, minutes / 1440)
        minutes % 60 == 0 -> context.resources.getQuantityString(R.plurals.reminder_hours_before, minutes / 60, minutes / 60)
        else -> context.resources.getQuantityString(R.plurals.reminder_minutes_before, minutes, minutes)
    }

    fun money(cents: Long, symbol: String): String {
        val whole = cents / 100
        val frac = (kotlin.math.abs(cents) % 100).toString().padStart(2, '0')
        return if (symbol.isEmpty()) "$whole.$frac" else "$symbol$whole.$frac"
    }
}
