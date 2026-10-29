package com.riyadmondol2006.taskfern.widget

import com.riyadmondol2006.taskfern.data.db.RoutineEntity
import com.riyadmondol2006.taskfern.data.db.RoutineStepWithCheck
import com.riyadmondol2006.taskfern.data.db.ShoppingItemEntity
import com.riyadmondol2006.taskfern.data.db.TaskRow
import com.riyadmondol2006.taskfern.data.repo.RoutineRepository
import java.time.LocalDate
import java.time.LocalTime

/*
 * Plain data the widgets render, built from database rows by pure functions so the rules (what is shown, in which
 * order, how progress is counted) are unit-tested without a launcher.
 */

/** A task as a widget row. [dueDate] doubles as the "expected due" a check-off tap carries. */
data class WidgetTask(
    val id: Long,
    val title: String,
    val dueDate: LocalDate,
    val dueTime: LocalTime?,
    val priority: Int,
    val listName: String?,
    val overdue: Boolean,
    val recurring: Boolean,
)

data class TodayModel(
    val today: LocalDate,
    /** Overdue first, then today's tasks; at most [WidgetModels.TODAY_LIMIT]. */
    val tasks: List<WidgetTask>,
    /** All open tasks due today or earlier, including any beyond the row limit. */
    val remaining: Int,
    val overdueCount: Int,
    val completedToday: Int,
) {
    val progress: Float get() = WidgetModels.progress(completedToday, remaining)
    val allClear: Boolean get() = remaining == 0
    val hiddenCount: Int get() = remaining - tasks.size
}

data class AgendaDay(val date: LocalDate, val tasks: List<WidgetTask>)

data class AgendaModel(
    val today: LocalDate,
    val overdueCount: Int,
    /** Today first (always present, possibly empty), then only the following days that have tasks. */
    val days: List<AgendaDay>,
    val upcomingCount: Int,
) {
    val isEmpty: Boolean get() = upcomingCount == 0
}

/** One row of the agenda's flat list: a day header, a task, or the "nothing due" line under an empty today. */
sealed interface AgendaEntry {
    /** Stable lazy-list id: task ids are positive, headers and placeholders use a reserved negative range. */
    val stableId: Long

    data class DayHeader(val date: LocalDate, val isToday: Boolean, val count: Int) : AgendaEntry {
        override val stableId: Long get() = AGENDA_ID_BASE - date.toEpochDay() * 2
    }

    data class Task(val task: WidgetTask) : AgendaEntry {
        override val stableId: Long get() = task.id
    }

    data class NothingDue(val date: LocalDate) : AgendaEntry {
        override val stableId: Long get() = AGENDA_ID_BASE - date.toEpochDay() * 2 - 1
    }

    companion object {
        private const val AGENDA_ID_BASE = -1_000L
    }
}

/** Due-date preset of a Quick Add widget, stored in [com.riyadmondol2006.taskfern.data.db.WidgetConfigEntity.option]. */
enum class QuickAddDue(val key: String) {
    NONE("none"), TODAY("today"), TOMORROW("tomorrow");

    /** Value of `QuickAddActivity.EXTRA_DUE`, or null for no date. */
    val extra: String? get() = if (this == NONE) null else key

    companion object {
        fun fromKey(key: String?): QuickAddDue = entries.firstOrNull { it.key == key } ?: NONE
    }
}

data class QuickAddModel(
    /** Valid task list id, or null for the Inbox. */
    val listId: Long?,
    val listName: String?,
    val due: QuickAddDue,
)

data class WidgetShoppingItem(val id: Long, val name: String, val detail: String)

sealed interface ShoppingModel {
    data object NoLists : ShoppingModel

    data class Showing(
        val listId: Long,
        val listName: String,
        val remaining: Int,
        val items: List<WidgetShoppingItem>,
        /** The list this widget was set up for was deleted; this is the fallback. */
        val configuredMissing: Boolean,
    ) : ShoppingModel {
        val hiddenCount: Int get() = remaining - items.size
    }
}

data class WidgetStep(val id: Long, val title: String, val checked: Boolean)

sealed interface RoutineStatus {
    data object Active : RoutineStatus
    data object Paused : RoutineStatus
    /** Not on today's weekdays; [nextDay] is the next day it runs, null when no weekday is selected. */
    data class NotScheduled(val nextDay: LocalDate?) : RoutineStatus
}

sealed interface RoutineModel {
    data object NoRoutines : RoutineModel

    data class Showing(
        val routineId: Long,
        val name: String,
        val emoji: String,
        val date: LocalDate,
        val status: RoutineStatus,
        val steps: List<WidgetStep>,
        val configuredMissing: Boolean,
    ) : RoutineModel {
        val done: Int get() = steps.count { it.checked }
        val total: Int get() = steps.size
        val progress: Float get() = if (total == 0) 0f else done.toFloat() / total
        val allDone: Boolean get() = total > 0 && done == total
    }
}

/** Which configured target (list / routine) a widget shows. */
data class TargetResolution(val id: Long?, val configuredMissing: Boolean)

object WidgetModels {
    const val TODAY_LIMIT = 50
    const val AGENDA_DAYS = 7
    const val AGENDA_LIMIT = 60
    const val SHOPPING_LIMIT = 40

    /** Share of the day's work done: completed ÷ (completed + remaining), 0 when there was nothing to do. */
    fun progress(completed: Int, remaining: Int): Float {
        val total = completed.coerceAtLeast(0) + remaining.coerceAtLeast(0)
        return if (total == 0) 0f else completed.coerceAtLeast(0).toFloat() / total
    }

    /** Due date, then timed before untimed (by time), then higher priority, keeping the database order otherwise. */
    private val taskOrder = compareBy<WidgetTask> { it.dueDate }
        .thenBy { it.dueTime == null }
        .thenBy { it.dueTime }
        .thenByDescending { it.priority }

    fun task(row: TaskRow, today: LocalDate): WidgetTask? {
        val t = row.task
        val due = t.dueDate ?: return null
        return WidgetTask(
            id = t.id,
            title = t.title,
            dueDate = due,
            dueTime = t.dueTime,
            priority = t.priority.coerceIn(0, 3),
            listName = row.listName,
            overdue = due.isBefore(today),
            recurring = t.recurrence != null,
        )
    }

    /** Tasks a widget may show: open, live, dated and not a paused series. */
    private fun isShowable(row: TaskRow): Boolean {
        val t = row.task
        return !t.isCompleted && t.deletedAt == null && !t.archived && t.dueDate != null && t.recurrence?.paused != true
    }

    fun today(rows: List<TaskRow>, completedToday: Int, today: LocalDate, limit: Int = TODAY_LIMIT): TodayModel {
        val tasks = rows.asSequence()
            .filter(::isShowable)
            .mapNotNull { task(it, today) }
            .filter { !it.dueDate.isAfter(today) }
            .distinctBy { it.id }
            .sortedWith(taskOrder)
            .toList()
        return TodayModel(
            today = today,
            tasks = tasks.take(limit),
            remaining = tasks.size,
            overdueCount = tasks.count { it.overdue },
            completedToday = completedToday.coerceAtLeast(0),
        )
    }

    fun agenda(
        rows: List<TaskRow>,
        overdueCount: Int,
        today: LocalDate,
        days: Int = AGENDA_DAYS,
        limit: Int = AGENDA_LIMIT,
    ): AgendaModel {
        val last = today.plusDays((days - 1).toLong())
        val tasks = rows.asSequence()
            .filter(::isShowable)
            .mapNotNull { task(it, today) }
            .filter { !it.dueDate.isBefore(today) && !it.dueDate.isAfter(last) }
            .distinctBy { it.id }
            .sortedWith(taskOrder)
            .toList()
        val byDay = tasks.take(limit).groupBy { it.dueDate }
        val grouped = buildList {
            add(AgendaDay(today, byDay[today].orEmpty()))
            byDay.keys.filter { it != today }.sorted().forEach { add(AgendaDay(it, byDay.getValue(it))) }
        }
        return AgendaModel(today, overdueCount.coerceAtLeast(0), grouped, tasks.size)
    }

    fun agendaEntries(model: AgendaModel): List<AgendaEntry> = buildList {
        for (day in model.days) {
            add(AgendaEntry.DayHeader(day.date, day.date == model.today, day.tasks.size))
            if (day.tasks.isEmpty()) add(AgendaEntry.NothingDue(day.date))
            day.tasks.forEach { add(AgendaEntry.Task(it)) }
        }
    }

    /** "2 l", "500 g", "3", or "" — the quantity and unit as typed. */
    fun shoppingDetail(quantity: String, unit: String): String =
        listOf(quantity.trim(), unit.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    fun shoppingItem(item: ShoppingItemEntity) = WidgetShoppingItem(item.id, item.name.trim(), shoppingDetail(item.quantity, item.unit))

    /**
     * The configured target when it still exists; otherwise the first available one, flagging that the configured one
     * is gone. A widget that was never configured (null) quietly shows the first.
     */
    fun resolveTarget(configured: Long?, available: List<Long>): TargetResolution = when {
        configured != null && configured in available -> TargetResolution(configured, false)
        configured != null -> TargetResolution(available.firstOrNull(), true)
        else -> TargetResolution(available.firstOrNull(), false)
    }

    /** The next day after [today] (within a week) that a routine with this weekday mask runs on. */
    fun nextRunDay(routine: RoutineEntity, today: LocalDate): LocalDate? =
        (1L..7L).map { today.plusDays(it) }.firstOrNull { RoutineRepository.runsOn(routine, it) }

    fun routineStatus(routine: RoutineEntity, today: LocalDate): RoutineStatus = when {
        routine.paused -> RoutineStatus.Paused
        RoutineRepository.runsOn(routine, today) -> RoutineStatus.Active
        else -> RoutineStatus.NotScheduled(nextRunDay(routine, today))
    }

    fun routine(
        routine: RoutineEntity,
        steps: List<RoutineStepWithCheck>,
        today: LocalDate,
        configuredMissing: Boolean,
    ) = RoutineModel.Showing(
        routineId = routine.id,
        name = routine.name,
        emoji = routine.emoji,
        date = today,
        status = routineStatus(routine, today),
        steps = steps.map { WidgetStep(it.step.id, it.step.title, it.checked) },
        configuredMissing = configuredMissing,
    )
}
