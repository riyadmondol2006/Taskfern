package com.riyadmondol2006.taskfern.domain

import com.riyadmondol2006.taskfern.data.db.DueScope
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Plans: tasks meant to be done some time within a week, month or year rather than on one day. A plan is stored as its
 * period's last day (so ordering, "overdue" and reminders keep working) plus its [DueScope].
 */
object PlanPeriods {

    /** Which plan a label refers to, relative to today. */
    enum class Relative { THIS, NEXT, OTHER }

    data class Label(val scope: Int, val relative: Relative, val start: LocalDate, val end: LocalDate)

    /** Last day of the period of [scope] that contains [date]. */
    fun endOf(scope: Int, date: LocalDate, weekStartsMonday: Boolean = true): LocalDate = when (scope) {
        DueScope.WEEK -> date.with(TemporalAdjusters.nextOrSame(if (weekStartsMonday) DayOfWeek.SUNDAY else DayOfWeek.SATURDAY))
        DueScope.MONTH -> date.with(TemporalAdjusters.lastDayOfMonth())
        DueScope.YEAR -> date.with(TemporalAdjusters.lastDayOfYear())
        else -> date
    }

    /** First day of the period that ends on [end]. */
    fun startOf(scope: Int, end: LocalDate): LocalDate = when (scope) {
        DueScope.WEEK -> end.minusDays(6)
        DueScope.MONTH -> end.withDayOfMonth(1)
        DueScope.YEAR -> end.withDayOfYear(1)
        else -> end
    }

    /** End of this period ([offset] = 0) or of the next one ([offset] = 1) of [scope], seen from [today]. */
    fun end(scope: Int, today: LocalDate, offset: Int, weekStartsMonday: Boolean = true): LocalDate {
        val reference = when (scope) {
            DueScope.WEEK -> today.plusWeeks(offset.toLong())
            DueScope.MONTH -> today.plusMonths(offset.toLong())
            DueScope.YEAR -> today.plusYears(offset.toLong())
            else -> today.plusDays(offset.toLong())
        }
        return endOf(scope, reference, weekStartsMonday)
    }

    /** True while [today] lies within the planned period (not before it starts, not after it ends). */
    fun isCurrent(scope: Int, end: LocalDate, today: LocalDate): Boolean =
        !today.isBefore(startOf(scope, end)) && !today.isAfter(end)

    fun label(scope: Int, end: LocalDate, today: LocalDate): Label {
        val start = startOf(scope, end)
        val nextStart = when (scope) {
            DueScope.WEEK -> today.plusWeeks(1)
            DueScope.MONTH -> today.plusMonths(1)
            else -> today.plusYears(1)
        }
        val relative = when {
            isCurrent(scope, end, today) -> Relative.THIS
            isCurrent(scope, end, nextStart) -> Relative.NEXT
            else -> Relative.OTHER
        }
        return Label(scope, relative, start, end)
    }
}
