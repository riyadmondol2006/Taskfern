package com.riyadmondol2006.taskfern.backup

import com.riyadmondol2006.taskfern.data.db.FocusSessionEntity
import com.riyadmondol2006.taskfern.data.db.ItemMemoryEntity
import com.riyadmondol2006.taskfern.data.db.OccurrenceAction
import com.riyadmondol2006.taskfern.data.db.OccurrenceLogEntity
import com.riyadmondol2006.taskfern.data.db.Priority
import com.riyadmondol2006.taskfern.data.db.ReminderEntity
import com.riyadmondol2006.taskfern.data.db.RoutineCheckEntity
import com.riyadmondol2006.taskfern.data.db.RoutineEntity
import com.riyadmondol2006.taskfern.data.db.RoutineStepEntity
import com.riyadmondol2006.taskfern.data.db.ShoppingItemEntity
import com.riyadmondol2006.taskfern.data.db.ShoppingListEntity
import com.riyadmondol2006.taskfern.data.db.SubtaskEntity
import com.riyadmondol2006.taskfern.data.db.TaskEntity
import com.riyadmondol2006.taskfern.data.db.TaskListEntity
import com.riyadmondol2006.taskfern.data.db.TemplateEntity
import com.riyadmondol2006.taskfern.data.db.TemplateKind
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** A small but complete data set touching every table and every kind of reference. */
object BackupFixtures {
    const val T0 = 1_761_700_000_000L

    fun snapshot(): DatabaseSnapshot {
        val work = TaskListEntity(id = 3, name = "Work", color = 2, icon = "work", position = 0, favorite = true, createdAt = T0)
        val home = TaskListEntity(id = 7, name = "Home", position = 1, createdAt = T0 + 1)
        val series = TaskEntity(
            id = 10, listId = 3, title = "Weekly review", notes = "Look back; plan, then rest", priority = Priority.HIGH,
            dueDate = LocalDate.of(2026, 11, 2), dueTime = LocalTime.of(9, 30), tags = TaskEntity.tagsToColumn(listOf("focus", "weekly")),
            createdAt = T0, updatedAt = T0 + 50, position = 4,
            recurrence = RecurrenceRule.weekly(1, DayOfWeek.MONDAY).copy(endDate = LocalDate.of(2027, 1, 31)),
            seriesAnchor = LocalDate.of(2026, 10, 5), occurrenceCount = 3, nextReminderAt = T0 + 999,
        )
        val done = TaskEntity(
            id = 11, listId = 3, title = "Weekly review", isCompleted = true, completedAt = T0 + 100,
            dueDate = LocalDate.of(2026, 10, 26), createdAt = T0 + 100, updatedAt = T0 + 100, sourceTaskId = 10,
        )
        val inbox = TaskEntity(id = 12, title = "Call the bank", createdAt = T0 + 2, updatedAt = T0 + 2, pinned = true, snoozeUntil = T0 + 5_000)
        val trashed = TaskEntity(id = 13, listId = 7, title = "Old idea", createdAt = T0 + 3, updatedAt = T0 + 3, deletedAt = T0 + 4, archived = false)
        return DatabaseSnapshot(
            taskLists = listOf(work, home),
            tasks = listOf(series, done, inbox, trashed),
            subtasks = listOf(
                SubtaskEntity(id = 20, taskId = 10, title = "Inbox zero", done = true, position = 0),
                SubtaskEntity(id = 21, taskId = 10, title = "Pick three priorities", position = 1),
            ),
            reminders = listOf(ReminderEntity(id = 30, taskId = 10, offsetMinutes = 0), ReminderEntity(id = 31, taskId = 10, offsetMinutes = 60)),
            occurrenceLog = listOf(
                OccurrenceLogEntity(id = 40, taskId = 10, occurrenceDate = LocalDate.of(2026, 10, 26), action = OccurrenceAction.COMPLETED, at = T0 + 100),
                OccurrenceLogEntity(id = 41, taskId = 10, occurrenceDate = LocalDate.of(2026, 10, 19), action = OccurrenceAction.SKIPPED, at = T0 + 90),
            ),
            shoppingLists = listOf(ShoppingListEntity(id = 50, name = "Groceries", color = 1, position = 0, createdAt = T0)),
            shoppingItems = listOf(
                ShoppingItemEntity(id = 60, listId = 50, name = "Milk", quantity = "2", unit = "l", category = "Dairy", createdAt = T0, priceCents = 189),
                ShoppingItemEntity(id = 61, listId = 50, name = "Bread", checked = true, checkedAt = T0 + 10, position = 1, createdAt = T0),
            ),
            itemMemory = listOf(ItemMemoryEntity(nameKey = "milk", name = "Milk", category = "Dairy", unit = "l", useCount = 4, lastUsedAt = T0, lastPriceCents = 189)),
            routines = listOf(
                RoutineEntity(
                    id = 70, name = "Morning", emoji = "☀️", weekdays = RecurrenceRule.WEEKDAYS_MASK, timeMinutes = 7 * 60 + 15,
                    position = 0, createdAt = T0, nextReminderAt = T0 + 1234,
                ),
            ),
            routineSteps = listOf(
                RoutineStepEntity(id = 80, routineId = 70, title = "Water", position = 0),
                RoutineStepEntity(id = 81, routineId = 70, title = "Stretch", position = 1),
            ),
            routineChecks = listOf(RoutineCheckEntity(stepId = 80, routineId = 70, date = LocalDate.of(2026, 10, 28), checkedAt = T0 + 20)),
            templates = listOf(TemplateEntity(id = 90, kind = TemplateKind.SHOPPING, name = "Weekly groceries", emoji = "🛒", items = "Milk\nEggs", builtin = true)),
            focusSessions = listOf(
                FocusSessionEntity(id = 100, taskId = 10, startedAt = T0 + 30, plannedMinutes = 25, focusedSeconds = 1500, completed = true),
                FocusSessionEntity(id = 101, taskId = null, startedAt = T0 + 40, plannedMinutes = 45, focusedSeconds = 600),
            ),
        )
    }

    /** What an import yields from [snapshot]: alarm bookkeeping is never carried over. */
    fun DatabaseSnapshot.withoutAlarms() = copy(
        tasks = tasks.map { it.copy(nextReminderAt = null) },
        routines = routines.map { it.copy(nextReminderAt = null) },
    )

    fun file(snapshot: DatabaseSnapshot = snapshot()): BackupFile =
        BackupMapper.toBackup(snapshot, null, T0, "1.0.0-test")
}
