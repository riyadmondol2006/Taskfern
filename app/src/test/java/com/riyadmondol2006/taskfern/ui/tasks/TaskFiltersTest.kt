package com.riyadmondol2006.taskfern.ui.tasks

import com.riyadmondol2006.taskfern.data.db.TaskEntity
import com.riyadmondol2006.taskfern.data.db.TaskRow
import com.riyadmondol2006.taskfern.data.prefs.TaskSort
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceRule
import com.riyadmondol2006.taskfern.ui.navigation.SmartViewKey
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TaskFiltersTest {
    private val today = LocalDate.of(2026, 10, 29) // Thursday

    private fun row(
        id: Long,
        due: LocalDate? = null,
        time: LocalTime? = null,
        priority: Int = 0,
        listId: Long? = null,
        tags: List<String> = emptyList(),
        pinned: Boolean = false,
        created: Long = id,
        position: Long = id,
        paused: Boolean = false,
        reminders: Int = 0,
    ) = TaskRow(
        TaskEntity(
            id = id, title = "Task $id", dueDate = due, dueTime = time, priority = priority, listId = listId,
            tags = TaskEntity.tagsToColumn(tags), pinned = pinned, createdAt = created, updatedAt = created, position = position,
            recurrence = if (paused) RecurrenceRule.daily().copy(paused = true) else null,
        ),
        listName = null, listColor = null, subtaskTotal = 0, subtaskDone = 0, reminderCount = reminders,
    )

    private val rows = listOf(
        row(1, due = today.minusDays(2)),
        row(2, due = today),
        row(3, due = today.plusDays(1)),
        row(4, due = today.plusDays(3)), // Sunday, still this week (Mon start)
        row(5, due = today.plusDays(5)),
        row(6, priority = 3, listId = 7, tags = listOf("home")),
        row(7, due = today, paused = true),
    )

    private fun ids(view: String, monday: Boolean = true) = TaskFilters.filter(view, rows, today, monday, listId = 7).map { it.task.id }

    @Test fun `today includes overdue and excludes paused series`() = assertEquals(listOf(1L, 2L), ids(SmartViewKey.TODAY))
    @Test fun tomorrow() = assertEquals(listOf(3L), ids(SmartViewKey.TOMORROW))
    @Test fun overdue() = assertEquals(listOf(1L), ids(SmartViewKey.OVERDUE))
    @Test fun upcoming() = assertEquals(listOf(3L, 4L, 5L), ids(SmartViewKey.UPCOMING))
    @Test fun `this week respects week start`() {
        assertEquals(listOf(2L, 3L, 4L), ids(SmartViewKey.THIS_WEEK, monday = true))
        assertEquals(listOf(2L, 3L), ids(SmartViewKey.THIS_WEEK, monday = false)) // week ends Saturday
    }
    @Test fun `no date and important`() {
        assertEquals(listOf(6L), ids(SmartViewKey.NO_DATE))
        assertEquals(listOf(6L), ids(SmartViewKey.IMPORTANT))
    }
    @Test fun `list inbox and tag`() {
        assertEquals(listOf(6L), ids(SmartViewKey.LIST))
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 7L), ids(SmartViewKey.INBOX))
        assertEquals(listOf(6L), ids(SmartViewKey.TAG_PREFIX + "home"))
    }

    @Test fun `smart sort puts pinned first then date then time then priority`() {
        val input = listOf(
            row(1, due = today, time = LocalTime.of(15, 0)),
            row(2, due = today, time = LocalTime.of(9, 0)),
            row(3, due = today, priority = 3),
            row(4, pinned = true),
            row(5, due = today.minusDays(1)),
            row(6),
        )
        assertEquals(listOf(4L, 5L, 2L, 1L, 3L, 6L), TaskFilters.sort(input, TaskSort.SMART).map { it.task.id })
    }

    @Test fun `priority and title and manual sort`() {
        val input = listOf(row(1, priority = 1, position = 3), row(2, priority = 3, position = 1), row(3, priority = 2, position = 2))
        assertEquals(listOf(2L, 3L, 1L), TaskFilters.sort(input, TaskSort.PRIORITY).map { it.task.id })
        assertEquals(listOf(2L, 3L, 1L), TaskFilters.sort(input, TaskSort.MANUAL).map { it.task.id })
        assertEquals(listOf(3L, 2L, 1L), TaskFilters.sort(input, TaskSort.CREATED).map { it.task.id })
    }

    @Test fun `refinement filters by priority tag and reminder`() {
        val input = listOf(row(1, priority = 3), row(2, priority = 1, tags = listOf("work")), row(3, reminders = 1))
        assertEquals(listOf(1L), TaskFilters.refine(input, TaskFilters.Refinement(priorities = setOf(3))).map { it.task.id })
        assertEquals(listOf(2L), TaskFilters.refine(input, TaskFilters.Refinement(tags = setOf("work"))).map { it.task.id })
        assertEquals(listOf(3L), TaskFilters.refine(input, TaskFilters.Refinement(onlyWithReminder = true)).map { it.task.id })
    }

    @Test fun `buckets group in date order`() {
        val groups = TaskFilters.groupByBucket(TaskFilters.sort(rows, TaskSort.SMART), today, true).map { it.first }
        assertEquals(
            listOf(TaskFilters.Bucket.OVERDUE, TaskFilters.Bucket.TODAY, TaskFilters.Bucket.TOMORROW, TaskFilters.Bucket.THIS_WEEK, TaskFilters.Bucket.LATER, TaskFilters.Bucket.NO_DATE),
            groups,
        )
    }

    @Test fun `handles thousands of tasks quickly`() {
        val many = (1L..5000L).map { row(it, due = today.plusDays(it % 60 - 20), priority = (it % 4).toInt()) }
        val start = System.nanoTime()
        repeat(20) {
            TaskFilters.sort(TaskFilters.filter(SmartViewKey.UPCOMING, many, today), TaskSort.SMART)
        }
        val ms = (System.nanoTime() - start) / 1_000_000
        assert(ms < 2_000) { "Filtering and sorting 5000 tasks 20 times took $ms ms" }
    }
}
