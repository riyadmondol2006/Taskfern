package com.riyadmondol2006.taskfern.ui.tasks

import com.riyadmondol2006.taskfern.data.db.Priority
import com.riyadmondol2006.taskfern.data.db.TaskRow
import com.riyadmondol2006.taskfern.data.prefs.TaskSort
import com.riyadmondol2006.taskfern.ui.navigation.SmartViewKey
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Pure filtering, sorting and grouping for task lists. Every smart view is a filter over the same open-task rows, so
 * all views agree with each other and update together.
 */
object TaskFilters {

    private fun TaskRow.isPaused() = task.recurrence?.paused == true

    /** Last day of the current week given the user's week start. */
    fun endOfWeek(today: LocalDate, weekStartsMonday: Boolean): LocalDate {
        val last = if (weekStartsMonday) DayOfWeek.SUNDAY else DayOfWeek.SATURDAY
        return today.with(TemporalAdjusters.nextOrSame(last))
    }

    /** Applies a smart view (or a list / tag) to open tasks. */
    fun filter(
        view: String,
        rows: List<TaskRow>,
        today: LocalDate,
        weekStartsMonday: Boolean = true,
        listId: Long = 0,
        recentSinceMillis: Long = 0,
    ): List<TaskRow> = when {
        view == SmartViewKey.LIST -> rows.filter { it.task.listId == listId }
        view == SmartViewKey.INBOX -> rows.filter { it.task.listId == null }
        view == SmartViewKey.TODAY -> rows.filter { !it.isPaused() && it.task.dueDate?.let { d -> !d.isAfter(today) } == true }
        view == SmartViewKey.TOMORROW -> rows.filter { !it.isPaused() && it.task.dueDate == today.plusDays(1) }
        view == SmartViewKey.THIS_WEEK -> {
            val end = endOfWeek(today, weekStartsMonday)
            rows.filter { !it.isPaused() && it.task.dueDate?.let { d -> !d.isBefore(today) && !d.isAfter(end) } == true }
        }
        view == SmartViewKey.UPCOMING -> rows.filter { !it.isPaused() && it.task.dueDate?.isAfter(today) == true }
        view == SmartViewKey.OVERDUE -> rows.filter { !it.isPaused() && it.task.dueDate?.isBefore(today) == true }
        view == SmartViewKey.IMPORTANT -> rows.filter { it.task.priority == Priority.HIGH }
        view == SmartViewKey.NO_DATE -> rows.filter { it.task.dueDate == null }
        view == SmartViewKey.RECENT -> rows.filter { it.task.createdAt >= recentSinceMillis }.sortedByDescending { it.task.createdAt }
        view.startsWith(SmartViewKey.TAG_PREFIX) -> {
            val tag = view.removePrefix(SmartViewKey.TAG_PREFIX)
            rows.filter { tag in it.task.tagList }
        }
        else -> rows
    }

    /** Extra, user-chosen filters on top of a view. */
    data class Refinement(
        val priorities: Set<Int> = emptySet(),
        val tags: Set<String> = emptySet(),
        val onlyWithReminder: Boolean = false,
        val onlyRecurring: Boolean = false,
    ) {
        val isActive: Boolean get() = priorities.isNotEmpty() || tags.isNotEmpty() || onlyWithReminder || onlyRecurring
    }

    fun refine(rows: List<TaskRow>, r: Refinement): List<TaskRow> {
        if (!r.isActive) return rows
        return rows.filter { row ->
            (r.priorities.isEmpty() || row.task.priority in r.priorities) &&
                (r.tags.isEmpty() || row.task.tagList.any { it in r.tags }) &&
                (!r.onlyWithReminder || row.reminderCount > 0) &&
                (!r.onlyRecurring || row.task.isRecurring)
        }
    }

    private val smartComparator: Comparator<TaskRow> =
        compareByDescending<TaskRow> { it.task.pinned }
            .thenBy { it.task.dueDate == null }
            .thenBy { it.task.dueDate }
            .thenBy { it.task.dueTime == null }
            .thenBy { it.task.dueTime }
            .thenByDescending { it.task.priority }
            .thenBy { it.task.position }
            .thenBy { it.task.id }

    fun sort(rows: List<TaskRow>, sort: TaskSort): List<TaskRow> = when (sort) {
        TaskSort.SMART -> rows.sortedWith(smartComparator)
        TaskSort.DUE_DATE -> rows.sortedWith(
            compareBy<TaskRow> { it.task.dueDate == null }.thenBy { it.task.dueDate }
                .thenBy { it.task.dueTime == null }.thenBy { it.task.dueTime }.thenBy { it.task.id },
        )
        TaskSort.PRIORITY -> rows.sortedWith(
            compareByDescending<TaskRow> { it.task.priority }.thenBy { it.task.dueDate == null }.thenBy { it.task.dueDate }.thenBy { it.task.id },
        )
        TaskSort.CREATED -> rows.sortedWith(compareByDescending<TaskRow> { it.task.createdAt }.thenByDescending { it.task.id })
        TaskSort.TITLE -> rows.sortedWith(compareBy<TaskRow, String>(String.CASE_INSENSITIVE_ORDER) { it.task.title }.thenBy { it.task.id })
        TaskSort.MANUAL -> rows.sortedWith(compareBy<TaskRow> { it.task.position }.thenBy { it.task.id })
    }

    /** Date buckets used by the Upcoming and This-week style lists. */
    enum class Bucket { OVERDUE, TODAY, TOMORROW, THIS_WEEK, LATER, NO_DATE }

    fun bucketOf(date: LocalDate?, today: LocalDate, weekStartsMonday: Boolean): Bucket = when {
        date == null -> Bucket.NO_DATE
        date.isBefore(today) -> Bucket.OVERDUE
        date == today -> Bucket.TODAY
        date == today.plusDays(1) -> Bucket.TOMORROW
        !date.isAfter(endOfWeek(today, weekStartsMonday)) -> Bucket.THIS_WEEK
        else -> Bucket.LATER
    }

    /** Groups already-sorted rows by date bucket, keeping order inside each group. Pinned rows stay in their bucket. */
    fun groupByBucket(rows: List<TaskRow>, today: LocalDate, weekStartsMonday: Boolean): List<Pair<Bucket, List<TaskRow>>> =
        rows.groupBy { bucketOf(it.task.dueDate, today, weekStartsMonday) }
            .toSortedMap(compareBy { it.ordinal })
            .map { it.key to it.value }

    /** Groups dated rows per calendar day (for Upcoming). */
    fun groupByDay(rows: List<TaskRow>): List<Pair<LocalDate?, List<TaskRow>>> =
        rows.groupBy { it.task.dueDate }.toList().sortedWith(compareBy({ it.first == null }, { it.first }))
}
