package com.riyadmondol2006.taskfern.widget

import com.riyadmondol2006.taskfern.data.db.RoutineEntity
import com.riyadmondol2006.taskfern.data.db.RoutineStepEntity
import com.riyadmondol2006.taskfern.data.db.RoutineStepWithCheck
import com.riyadmondol2006.taskfern.data.db.TaskEntity
import com.riyadmondol2006.taskfern.data.db.TaskRow
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class WidgetModelsTest {
    // A Thursday.
    private val today = LocalDate.of(2026, 10, 29)

    private fun row(
        id: Long,
        due: LocalDate?,
        time: LocalTime? = null,
        priority: Int = 0,
        completed: Boolean = false,
        recurrence: RecurrenceRule? = null,
        listName: String? = null,
    ) = TaskRow(
        task = TaskEntity(
            id = id, title = "Task $id", dueDate = due, dueTime = time, priority = priority, isCompleted = completed,
            createdAt = 0, updatedAt = 0, recurrence = recurrence,
        ),
        listName = listName, listColor = null, subtaskTotal = 0, subtaskDone = 0, reminderCount = 0,
    )

    // ---- Progress -------------------------------------------------------------------------------------------------

    @Test
    fun progressIsCompletedOverCompletedPlusRemaining() {
        assertEquals(0f, WidgetModels.progress(0, 0), 0f)
        assertEquals(0f, WidgetModels.progress(0, 4), 0f)
        assertEquals(0.25f, WidgetModels.progress(1, 3), 1e-6f)
        assertEquals(1f, WidgetModels.progress(5, 0), 0f)
        assertEquals(0f, WidgetModels.progress(-2, 3), 0f)
    }

    // ---- Today ----------------------------------------------------------------------------------------------------

    @Test
    fun todayShowsOverdueFirstThenTimedThenUntimedByPriority() {
        val rows = listOf(
            row(1, today, priority = 1),
            row(2, today, time = LocalTime.of(14, 0)),
            row(3, today.minusDays(2)),
            row(4, today, priority = 3),
            row(5, today, time = LocalTime.of(9, 30)),
            row(6, today.minusDays(1), time = LocalTime.of(8, 0)),
        )
        val model = WidgetModels.today(rows, completedToday = 2, today = today)

        assertEquals(listOf(3L, 6L, 5L, 2L, 4L, 1L), model.tasks.map { it.id })
        assertEquals(6, model.remaining)
        assertEquals(2, model.overdueCount)
        assertEquals(listOf(true, true, false, false, false, false), model.tasks.map { it.overdue })
        assertEquals(2f / 8f, model.progress, 1e-6f)
        assertFalse(model.allClear)
    }

    @Test
    fun todayLeavesOutCompletedFuturePausedAndUndatedRows() {
        val rows = listOf(
            row(1, today),
            row(2, today, completed = true),
            row(3, today.plusDays(1)),
            row(4, null),
            row(5, today, recurrence = RecurrenceRule.daily().copy(paused = true)),
            row(6, today, recurrence = RecurrenceRule.daily()),
            row(1, today), // a duplicate row must not appear twice
        )
        val model = WidgetModels.today(rows, completedToday = 0, today = today)

        assertEquals(listOf(1L, 6L), model.tasks.map { it.id })
        assertTrue(model.tasks.first { it.id == 6L }.recurring)
    }

    @Test
    fun todayCarriesTheDueDateTheCheckOffWillExpect() {
        val model = WidgetModels.today(listOf(row(9, today.minusDays(3))), 0, today)
        val task = model.tasks.single()
        assertEquals(today.minusDays(3), task.dueDate)
        assertEquals(CompleteTaskRequest(9, today.minusDays(3)), CompleteTaskRequest(task.id, task.dueDate))
    }

    @Test
    fun todayLimitsRowsButCountsEverything() {
        val rows = (1L..60L).map { row(it, today) }
        val model = WidgetModels.today(rows, completedToday = 0, today = today, limit = 50)
        assertEquals(50, model.tasks.size)
        assertEquals(60, model.remaining)
        assertEquals(10, model.hiddenCount)
    }

    @Test
    fun emptyTodayIsAllClear() {
        val model = WidgetModels.today(emptyList(), completedToday = 3, today = today)
        assertTrue(model.allClear)
        assertEquals(1f, model.progress, 0f)
    }

    // ---- Agenda ---------------------------------------------------------------------------------------------------

    @Test
    fun agendaGroupsByDayWithTodayAlwaysFirst() {
        val rows = listOf(
            row(1, today.plusDays(2), time = LocalTime.of(10, 0)),
            row(2, today.plusDays(2)),
            row(3, today.plusDays(1), priority = 2),
            row(4, today.plusDays(6)),
            row(5, today.plusDays(7)), // outside the 7-day window
            row(6, today.minusDays(1)), // overdue is a count, not a row
        )
        val model = WidgetModels.agenda(rows, overdueCount = 4, today = today)

        assertEquals(listOf(today, today.plusDays(1), today.plusDays(2), today.plusDays(6)), model.days.map { it.date })
        assertTrue(model.days.first().tasks.isEmpty())
        assertEquals(listOf(1L, 2L), model.days[2].tasks.map { it.id })
        assertEquals(4, model.upcomingCount)
        assertEquals(4, model.overdueCount)
        assertFalse(model.isEmpty)
    }

    @Test
    fun agendaEntriesHaveUniqueStableIdsAndAPlaceholderForAnEmptyToday() {
        val model = WidgetModels.agenda(listOf(row(1, today.plusDays(1)), row(2, today.plusDays(3))), 0, today)
        val entries = WidgetModels.agendaEntries(model)

        assertEquals(
            listOf(
                AgendaEntry.DayHeader(today, true, 0),
                AgendaEntry.NothingDue(today),
            ),
            entries.take(2),
        )
        assertEquals(listOf(1L, 2L), entries.filterIsInstance<AgendaEntry.Task>().map { it.task.id })
        val ids = entries.map { it.stableId }
        assertEquals(ids.size, ids.toSet().size)
        // Header and placeholder ids never collide with task ids (which are positive).
        assertTrue(entries.filter { it !is AgendaEntry.Task }.all { it.stableId < 0 })
        // Ids depend on the day, not on the position in the list.
        val later = WidgetModels.agendaEntries(WidgetModels.agenda(listOf(row(2, today.plusDays(3))), 0, today))
        assertEquals(
            entries.first { it is AgendaEntry.DayHeader && it.date == today.plusDays(3) }.stableId,
            later.first { it is AgendaEntry.DayHeader && it.date == today.plusDays(3) }.stableId,
        )
    }

    @Test
    fun agendaWithNothingUpcomingIsEmpty() {
        val model = WidgetModels.agenda(emptyList(), overdueCount = 2, today = today)
        assertTrue(model.isEmpty)
        assertEquals(listOf(today), model.days.map { it.date })
    }

    // ---- Shopping & targets ---------------------------------------------------------------------------------------

    @Test
    fun shoppingDetailJoinsQuantityAndUnit() {
        assertEquals("2 l", WidgetModels.shoppingDetail("2", "l"))
        assertEquals("3", WidgetModels.shoppingDetail(" 3 ", ""))
        assertEquals("pack", WidgetModels.shoppingDetail("", "pack"))
        assertEquals("", WidgetModels.shoppingDetail(" ", " "))
    }

    @Test
    fun resolveTargetFallsBackToTheFirstAndFlagsADeletedChoice() {
        assertEquals(TargetResolution(7, false), WidgetModels.resolveTarget(7, listOf(3, 7)))
        assertEquals(TargetResolution(3, true), WidgetModels.resolveTarget(9, listOf(3, 7)))
        assertEquals(TargetResolution(3, false), WidgetModels.resolveTarget(null, listOf(3, 7)))
        assertEquals(TargetResolution(null, true), WidgetModels.resolveTarget(9, emptyList()))
        assertEquals(TargetResolution(null, false), WidgetModels.resolveTarget(null, emptyList()))
    }

    // ---- Routines -------------------------------------------------------------------------------------------------

    private fun mask(vararg days: DayOfWeek) = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }

    private fun routine(weekdays: Int, paused: Boolean = false) =
        RoutineEntity(id = 5, name = "Morning", emoji = "☀️", weekdays = weekdays, paused = paused, createdAt = 0)

    @Test
    fun routineStatusFollowsWeekdaysAndPause() {
        assertEquals(RoutineStatus.Active, WidgetModels.routineStatus(routine(0x7F), today))
        assertEquals(RoutineStatus.Paused, WidgetModels.routineStatus(routine(0x7F, paused = true), today))
        assertEquals(
            RoutineStatus.NotScheduled(LocalDate.of(2026, 11, 2)), // next Monday
            WidgetModels.routineStatus(routine(mask(DayOfWeek.MONDAY)), today),
        )
        // A Thursday-only routine seen on Friday runs again next Thursday.
        assertEquals(
            RoutineStatus.NotScheduled(today.plusDays(7)),
            WidgetModels.routineStatus(routine(mask(DayOfWeek.THURSDAY)), today.plusDays(1)),
        )
        // Seen on its own day, the next run is a week later.
        assertEquals(today.plusDays(7), WidgetModels.nextRunDay(routine(mask(DayOfWeek.THURSDAY)), today))
        assertEquals(RoutineStatus.NotScheduled(null), WidgetModels.routineStatus(routine(0), today))
        assertNull(WidgetModels.nextRunDay(routine(0), today))
    }

    @Test
    fun routineModelCountsCheckedSteps() {
        val steps = listOf(
            RoutineStepWithCheck(RoutineStepEntity(id = 1, routineId = 5, title = "Water"), checked = true),
            RoutineStepWithCheck(RoutineStepEntity(id = 2, routineId = 5, title = "Stretch"), checked = false),
            RoutineStepWithCheck(RoutineStepEntity(id = 3, routineId = 5, title = "Plan"), checked = true),
        )
        val model = WidgetModels.routine(routine(0x7F), steps, today, configuredMissing = false)
        assertEquals(2, model.done)
        assertEquals(3, model.total)
        assertEquals(2f / 3f, model.progress, 1e-6f)
        assertFalse(model.allDone)
        assertEquals(listOf(1L, 2L, 3L), model.steps.map { it.id })

        val empty = WidgetModels.routine(routine(0x7F), emptyList(), today, configuredMissing = true)
        assertEquals(0f, empty.progress, 0f)
        assertFalse(empty.allDone)
        assertTrue(empty.configuredMissing)
    }

    // ---- Quick Add ------------------------------------------------------------------------------------------------

    @Test
    fun quickAddDueParsesStoredOptions() {
        assertEquals(QuickAddDue.TODAY, QuickAddDue.fromKey("today"))
        assertEquals(QuickAddDue.TOMORROW, QuickAddDue.fromKey("tomorrow"))
        assertEquals(QuickAddDue.NONE, QuickAddDue.fromKey("none"))
        assertEquals(QuickAddDue.NONE, QuickAddDue.fromKey(""))
        assertEquals(QuickAddDue.NONE, QuickAddDue.fromKey(null))
        assertNull(QuickAddDue.NONE.extra)
        assertEquals("tomorrow", QuickAddDue.TOMORROW.extra)
    }
}
