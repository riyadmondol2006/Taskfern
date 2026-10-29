package com.riyadmondol2006.taskfern.domain.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Pure date arithmetic for repeating tasks and routines. No Android, no clock: callers pass "today".
 *
 * Model: a recurring task is a single row whose due date rolls forward when an occurrence is completed or skipped, so
 * future copies never pile up and a double tap can never create duplicates. The [anchor] is the date the series
 * started; it keeps "monthly on the 31st" on the 31st (or the last day of shorter months) instead of drifting to the
 * 28th after February, and it decides which weeks count for "every 2 weeks".
 */
object RecurrenceEngine {

    /**
     * Returns the first occurrence strictly after [after], or null when the rule cannot produce one (series ended by
     * [RecurrenceRule.endDate]). Does not look at completion counts or "today".
     */
    fun occurrenceAfter(rule: RecurrenceRule, anchor: LocalDate, after: LocalDate): LocalDate? {
        val next = when (rule.frequency) {
            Frequency.DAILY -> dailyAfter(rule.interval, anchor, after)
            Frequency.WEEKLY -> weeklyAfter(rule, anchor, after)
            Frequency.MONTHLY -> monthlyAfter(rule.interval, anchor, after)
            Frequency.YEARLY -> yearlyAfter(rule.interval, anchor, after)
        }
        return next.takeUnless { rule.endDate != null && it.isAfter(rule.endDate) }
    }

    /**
     * The due date that follows completing (or skipping) the occurrence due on [currentDue].
     *
     * - "From completion" rules count [RecurrenceRule.interval] units from [completedOn].
     * - Otherwise the next scheduled occurrence after [currentDue] is used; occurrences that were missed while the task
     *   sat overdue are skipped so the new due date is after [today] (one completion never leaves the task overdue).
     * - Returns null when the series is over: past [RecurrenceRule.endDate] or [RecurrenceRule.maxOccurrences] reached,
     *   counting the occurrence being completed now ([completedCount] is the number done *before* this one).
     */
    fun nextDueDate(
        rule: RecurrenceRule,
        anchor: LocalDate,
        currentDue: LocalDate,
        completedOn: LocalDate,
        today: LocalDate,
        completedCount: Int,
        countThisOccurrence: Boolean = true,
    ): LocalDate? {
        val doneAfterThis = completedCount + if (countThisOccurrence) 1 else 0
        if (rule.maxOccurrences != null && doneAfterThis >= rule.maxOccurrences) return null

        if (rule.fromCompletion) {
            val next = addUnits(rule.frequency, rule.interval, completedOn)
            return next.takeUnless { rule.endDate != null && it.isAfter(rule.endDate) }
        }

        var next = occurrenceAfter(rule, anchor, currentDue) ?: return null
        // Catch up past missed occurrences. Bounded so a corrupt rule can never spin forever.
        var guard = 0
        while (!next.isAfter(today) && guard++ < 10_000) {
            next = occurrenceAfter(rule, anchor, next) ?: return null
        }
        return next
    }

    /**
     * Brings a series back to life after a pause, or after its schedule was edited: the first valid occurrence on or
     * after [today] (or the current due date if that is still in the future and valid).
     */
    fun realign(rule: RecurrenceRule, anchor: LocalDate, currentDue: LocalDate?, today: LocalDate): LocalDate? {
        val start = if (currentDue != null && !currentDue.isBefore(today)) currentDue else today
        if (matches(rule, anchor, start)) return start.takeUnless { rule.endDate != null && it.isAfter(rule.endDate) }
        return occurrenceAfter(rule, anchor, start)
    }

    /** True when [date] is an occurrence of the rule (ignoring end conditions). */
    fun matches(rule: RecurrenceRule, anchor: LocalDate, date: LocalDate): Boolean {
        if (date.isBefore(anchor)) return false
        if (rule.fromCompletion) return true // any date can be the next one
        return occurrenceAfter(rule.copy(endDate = null), anchor, date.minusDays(1)) == date
    }

    /** Occurrences in [from]..[to] (inclusive), at most [limit]. Used to project a series onto the calendar. */
    fun occurrencesBetween(
        rule: RecurrenceRule,
        anchor: LocalDate,
        firstDue: LocalDate,
        from: LocalDate,
        to: LocalDate,
        limit: Int = 366,
    ): List<LocalDate> {
        if (rule.paused || to.isBefore(from)) return emptyList()
        val result = ArrayList<LocalDate>()
        var current: LocalDate? = firstDue
        while (current != null && !current.isAfter(to) && result.size < limit) {
            if (!current.isBefore(from)) result += current
            current = if (rule.fromCompletion) {
                // Cannot know when it will be completed; assume on time.
                addUnits(rule.frequency, rule.interval, current).takeUnless { rule.endDate != null && it.isAfter(rule.endDate) }
            } else {
                occurrenceAfter(rule, anchor, current)
            }
        }
        return result
    }

    fun addUnits(frequency: Frequency, interval: Int, date: LocalDate): LocalDate = when (frequency) {
        Frequency.DAILY -> date.plusDays(interval.toLong())
        Frequency.WEEKLY -> date.plusWeeks(interval.toLong())
        Frequency.MONTHLY -> date.plusMonths(interval.toLong()) // java.time clamps Jan 31 + 1 month to Feb 28/29
        Frequency.YEARLY -> date.plusYears(interval.toLong()) // and Feb 29 + 1 year to Feb 28
    }

    private fun dailyAfter(interval: Int, anchor: LocalDate, after: LocalDate): LocalDate {
        if (after.isBefore(anchor)) return anchor
        val days = ChronoUnit.DAYS.between(anchor, after)
        val steps = days / interval + 1
        return anchor.plusDays(steps * interval)
    }

    private fun weeklyAfter(rule: RecurrenceRule, anchor: LocalDate, after: LocalDate): LocalDate {
        val days = rule.weekdaySet.ifEmpty { setOf(anchor.dayOfWeek) }
        val anchorWeek = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var candidate = if (after.isBefore(anchor)) anchor.minusDays(1) else after
        // At most interval*7 + 7 days to the next valid day; scan day by day (cheap, obviously correct).
        repeat(rule.interval * 7 + 8) {
            candidate = candidate.plusDays(1)
            if (candidate.isBefore(anchor)) return@repeat
            val week = candidate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weeksFromAnchor = ChronoUnit.WEEKS.between(anchorWeek, week)
            if (weeksFromAnchor % rule.interval == 0L && candidate.dayOfWeek in days) return candidate
        }
        error("no weekly occurrence found") // unreachable for a valid rule
    }

    private fun monthlyAfter(interval: Int, anchor: LocalDate, after: LocalDate): LocalDate {
        if (after.isBefore(anchor)) return anchor
        val anchorDay = anchor.dayOfMonth
        // Months between the anchor month and the month of `after`, rounded down to a multiple of the interval.
        val monthsFromAnchor = ChronoUnit.MONTHS.between(anchor.withDayOfMonth(1), after.withDayOfMonth(1))
        var k = (monthsFromAnchor / interval) * interval
        while (true) {
            val month = anchor.withDayOfMonth(1).plusMonths(k)
            val candidate = month.withDayOfMonth(minOf(anchorDay, month.lengthOfMonth()))
            if (candidate.isAfter(after)) return candidate
            k += interval
        }
    }

    private fun yearlyAfter(interval: Int, anchor: LocalDate, after: LocalDate): LocalDate {
        if (after.isBefore(anchor)) return anchor
        var k = ((after.year - anchor.year) / interval).toLong() * interval
        while (true) {
            val year = anchor.year + k
            val month = anchor.month
            val first = LocalDate.of(year.toInt(), month, 1)
            val candidate = first.withDayOfMonth(minOf(anchor.dayOfMonth, first.lengthOfMonth()))
            if (candidate.isAfter(after)) return candidate
            k += interval
        }
    }
}
