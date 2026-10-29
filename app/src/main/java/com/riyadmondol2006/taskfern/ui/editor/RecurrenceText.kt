package com.riyadmondol2006.taskfern.ui.editor

import android.content.Context
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.domain.recurrence.Frequency
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceRule
import com.riyadmondol2006.taskfern.ui.common.Formatters
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Human description of a repeat rule, e.g. "Every 2 weeks on Mon, Thu · after completion · 3 times". */
object RecurrenceText {
    fun describe(context: Context, rule: RecurrenceRule, anchor: LocalDate?): String {
        val r = context.resources
        val base = when (rule.frequency) {
            Frequency.DAILY -> if (rule.interval == 1) context.getString(R.string.repeat_every_day)
            else r.getQuantityString(R.plurals.repeat_every_n_days, rule.interval, rule.interval)
            Frequency.WEEKLY -> {
                val days = rule.weekdaySet
                when {
                    rule.interval == 1 && rule.weekdays == RecurrenceRule.WEEKDAYS_MASK -> context.getString(R.string.repeat_weekdays)
                    rule.interval == 1 && days.size == 7 -> context.getString(R.string.repeat_every_day)
                    else -> {
                        val every = if (rule.interval == 1) context.getString(R.string.repeat_every_week)
                        else r.getQuantityString(R.plurals.repeat_every_n_weeks, rule.interval, rule.interval)
                        val names = (days.ifEmpty { setOfNotNull(anchor?.dayOfWeek) })
                            .sortedBy { it.value }
                            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
                        if (names.isEmpty()) every else context.getString(R.string.repeat_on_days, every, names)
                    }
                }
            }
            Frequency.MONTHLY -> {
                val every = if (rule.interval == 1) context.getString(R.string.repeat_every_month)
                else r.getQuantityString(R.plurals.repeat_every_n_months, rule.interval, rule.interval)
                if (anchor != null && !rule.fromCompletion) context.getString(R.string.repeat_on_day_of_month, every, anchor.dayOfMonth) else every
            }
            Frequency.YEARLY -> if (rule.interval == 1) context.getString(R.string.repeat_every_year)
            else r.getQuantityString(R.plurals.repeat_every_n_years, rule.interval, rule.interval)
        }
        val parts = mutableListOf(base)
        if (rule.fromCompletion) parts += context.getString(R.string.repeat_after_completion_short)
        rule.endDate?.let { parts += context.getString(R.string.repeat_until, Formatters.shortDate(it)) }
        rule.maxOccurrences?.let { parts += r.getQuantityString(R.plurals.repeat_times, it, it) }
        if (rule.paused) parts += context.getString(R.string.paused)
        return parts.joinToString(" · ")
    }
}
