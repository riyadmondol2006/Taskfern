package com.riyadmondol2006.taskfern.domain

import com.riyadmondol2006.taskfern.data.db.OccurrenceAction
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceEngine
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceRule
import java.time.LocalDate

/** How long a repeating task has been kept up. */
data class Streak(val current: Int, val best: Int) {
    companion object {
        val NONE = Streak(0, 0)
    }
}

/** One entry of a series' history: an occurrence that was completed or skipped. */
data class OccurrenceRecord(val date: LocalDate, val action: String)

/**
 * Streaks for repeating tasks ("habits"), from the series' occurrence log. Pure: no clock, no Android.
 *
 * A streak counts completed occurrences in a row. It is broken by a skipped occurrence, by a scheduled occurrence that
 * was never completed (the task was finished late, so the missed dates were never logged), and by the task currently
 * sitting overdue. Finishing an occurrence late still counts for that occurrence. For "repeat after completion" rules
 * any gap is allowed: there is no fixed schedule to miss. Changing a schedule restarts the series anchor, so earlier
 * occurrences no longer line up and the streak starts again — which matches what the user changed.
 */
object TaskStreaks {

    fun compute(
        rule: RecurrenceRule,
        anchor: LocalDate,
        log: List<OccurrenceRecord>,
        currentDue: LocalDate?,
        today: LocalDate,
    ): Streak {
        if (log.isEmpty()) return Streak.NONE
        val entries = log.sortedBy { it.date }
        var run = 0
        var best = 0
        var previous: LocalDate? = null
        for (entry in entries) {
            if (entry.action != OccurrenceAction.COMPLETED) {
                run = 0
                previous = entry.date
                continue
            }
            val follows = previous == null || rule.fromCompletion ||
                RecurrenceEngine.occurrenceAfter(rule.copy(endDate = null), anchor, previous) == entry.date
            run = if (follows) run + 1 else 1
            best = maxOf(best, run)
            previous = entry.date
        }
        // An overdue occurrence breaks the current streak (it is still kept as the best).
        val brokenNow = currentDue != null && currentDue.isBefore(today)
        // So does a scheduled occurrence between the last logged one and the current due date that was never logged.
        val gapBeforeCurrent = !rule.fromCompletion && previous != null && currentDue != null &&
            RecurrenceEngine.occurrenceAfter(rule.copy(endDate = null), anchor, previous)?.let { it.isBefore(currentDue) } == true
        return Streak(if (brokenNow || gapBeforeCurrent) 0 else run, best)
    }
}
