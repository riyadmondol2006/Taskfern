package com.riyadmondol2006.taskfern.domain.recurrence

import java.time.DayOfWeek
import java.time.LocalDate

/** How often a recurring task repeats. */
enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * A repeat rule. Stored inline on a task (Room `@Embedded`), so a task either has a rule or not.
 *
 * Dates are calendar dates without a zone: a task due "every Monday at 9:00" stays on Mondays at 9:00 local time when
 * the device changes time zone or daylight saving time, which is what people expect from a personal planner.
 *
 * @property interval repeat every N days/weeks/months/years (N ≥ 1).
 * @property weekdays for [Frequency.WEEKLY]: a bit mask of [DayOfWeek]s (see [weekdayMask]). 0 means "the weekday of the
 *   series start".
 * @property fromCompletion when true the next date is counted from the day the task was completed instead of from its
 *   due date ("water the plants 3 days after I last did it").
 * @property endDate the series ends after this date (inclusive).
 * @property maxOccurrences the series ends after this many completed occurrences.
 * @property paused a paused series keeps its settings but is hidden from Today/Overdue and does not remind.
 */
data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val weekdays: Int = 0,
    val fromCompletion: Boolean = false,
    val endDate: LocalDate? = null,
    val maxOccurrences: Int? = null,
    val paused: Boolean = false,
) {
    init {
        require(interval in 1..999) { "interval must be between 1 and 999" }
        require(weekdays in 0..0x7F) { "weekdays must be a 7-bit mask" }
        require(maxOccurrences == null || maxOccurrences >= 1) { "maxOccurrences must be positive" }
    }

    val weekdaySet: Set<DayOfWeek> get() = weekdaysFromMask(weekdays)

    companion object {
        val WEEKDAYS_MASK: Int = weekdayMask(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        val WEEKEND_MASK: Int = weekdayMask(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        fun daily(interval: Int = 1) = RecurrenceRule(Frequency.DAILY, interval)
        fun weekdays() = RecurrenceRule(Frequency.WEEKLY, 1, WEEKDAYS_MASK)
        fun weekly(interval: Int = 1, vararg days: DayOfWeek) = RecurrenceRule(Frequency.WEEKLY, interval, weekdayMask(*days))
        fun monthly(interval: Int = 1) = RecurrenceRule(Frequency.MONTHLY, interval)
        fun yearly(interval: Int = 1) = RecurrenceRule(Frequency.YEARLY, interval)

        fun weekdayMask(vararg days: DayOfWeek): Int = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
        fun weekdaysFromMask(mask: Int): Set<DayOfWeek> =
            DayOfWeek.entries.filterTo(LinkedHashSet()) { mask and (1 shl (it.value - 1)) != 0 }
    }
}
