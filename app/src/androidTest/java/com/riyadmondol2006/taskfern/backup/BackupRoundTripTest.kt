package com.riyadmondol2006.taskfern.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.riyadmondol2006.taskfern.data.db.TaskfernDatabase
import com.riyadmondol2006.taskfern.data.db.TemplateEntity
import com.riyadmondol2006.taskfern.data.db.TemplateKind
import com.riyadmondol2006.taskfern.data.db.WidgetConfigEntity
import com.riyadmondol2006.taskfern.data.db.WidgetKind
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Fills a real (in-memory) database, exports it to JSON, and imports it into other databases with both Replace and
 * Merge, comparing every table afterwards.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val opened = ArrayList<TaskfernDatabase>()

    private fun newDb() = TaskfernDatabase.build(context, inMemory = true).also { opened += it }

    @Before
    fun setUp() = opened.clear()

    @After
    fun tearDown() = opened.forEach { it.close() }

    private suspend fun fill(db: TaskfernDatabase) {
        val t = db.taskDao()
        val work = t.insertList(TaskListEntity(name = "Work", color = 2, icon = "work", position = 0, favorite = true, createdAt = T0))
        val home = t.insertList(TaskListEntity(name = "Home", position = 1, createdAt = T0 + 1))
        val series = t.insert(
            TaskEntity(
                listId = work, title = "Weekly review", notes = "Plan; then rest, ok?", priority = Priority.HIGH,
                dueDate = LocalDate.of(2026, 11, 2), dueTime = LocalTime.of(9, 30),
                tags = TaskEntity.tagsToColumn(listOf("focus", "weekly")), createdAt = T0, updatedAt = T0 + 50, position = 4,
                recurrence = RecurrenceRule.weekly(1, DayOfWeek.MONDAY).copy(endDate = LocalDate.of(2027, 1, 31), maxOccurrences = 20),
                seriesAnchor = LocalDate.of(2026, 10, 5), occurrenceCount = 3, nextReminderAt = T0 + 999,
            ),
        )
        t.insert(
            TaskEntity(
                listId = work, title = "Weekly review", isCompleted = true, completedAt = T0 + 100,
                dueDate = LocalDate.of(2026, 10, 26), createdAt = T0 + 100, updatedAt = T0 + 100, sourceTaskId = series,
            ),
        )
        t.insert(TaskEntity(title = "Call the bank ☎️", createdAt = T0 + 2, updatedAt = T0 + 2, pinned = true, snoozeUntil = T0 + 5_000))
        t.insert(TaskEntity(listId = home, title = "Old idea", createdAt = T0 + 3, updatedAt = T0 + 4, deletedAt = T0 + 4))
        t.insert(TaskEntity(listId = home, title = "Put away", createdAt = T0 + 5, updatedAt = T0 + 5, archived = true))
        t.insertSubtasks(
            listOf(
                SubtaskEntity(taskId = series, title = "Inbox zero", done = true, position = 0),
                SubtaskEntity(taskId = series, title = "Pick three priorities", position = 1),
            ),
        )
        t.insertReminders(listOf(ReminderEntity(taskId = series, offsetMinutes = 0), ReminderEntity(taskId = series, offsetMinutes = 60)))
        t.insertLog(OccurrenceLogEntity(taskId = series, occurrenceDate = LocalDate.of(2026, 10, 26), action = OccurrenceAction.COMPLETED, at = T0 + 100))
        t.insertLog(OccurrenceLogEntity(taskId = series, occurrenceDate = LocalDate.of(2026, 10, 19), action = OccurrenceAction.SKIPPED, at = T0 + 90))

        val s = db.shoppingDao()
        val groceries = s.insertList(ShoppingListEntity(name = "Groceries", color = 1, position = 0, createdAt = T0))
        s.insertList(ShoppingListEntity(name = "Hardware", position = 1, createdAt = T0 + 1))
        s.insertItems(
            listOf(
                ShoppingItemEntity(listId = groceries, name = "Milk", quantity = "2", unit = "l", category = "Dairy", priceCents = 189, createdAt = T0),
                ShoppingItemEntity(listId = groceries, name = "Bread", checked = true, checkedAt = T0 + 10, position = 1, createdAt = T0),
            ),
        )
        s.upsertMemory(ItemMemoryEntity(nameKey = "milk", name = "Milk", category = "Dairy", unit = "l", useCount = 4, lastUsedAt = T0, lastPriceCents = 189))

        val r = db.routineDao()
        val morning = r.insertRoutine(
            RoutineEntity(name = "Morning", emoji = "☀️", weekdays = RecurrenceRule.WEEKDAYS_MASK, timeMinutes = 435, position = 0, createdAt = T0),
        )
        val water = r.insertStep(RoutineStepEntity(routineId = morning, title = "Water", position = 0))
        r.insertStep(RoutineStepEntity(routineId = morning, title = "Stretch", position = 1))
        r.insertCheck(RoutineCheckEntity(stepId = water, routineId = morning, date = LocalDate.of(2026, 10, 28), checkedAt = T0 + 20))

        db.templateDao().insertAll(
            listOf(
                TemplateEntity(kind = TemplateKind.SHOPPING, name = "Weekly groceries", emoji = "🛒", items = "Milk\nEggs", builtin = true, position = 0),
                TemplateEntity(kind = TemplateKind.ROUTINE, name = "Evening", items = "Read\nSleep", position = 1),
            ),
        )
        db.focusDao().insert(FocusSessionEntity(taskId = series, startedAt = T0 + 30, plannedMinutes = 25, focusedSeconds = 1500, completed = true))
        db.focusDao().insert(FocusSessionEntity(startedAt = T0 + 40, plannedMinutes = 45, focusedSeconds = 600))
    }

    private suspend fun exportJson(db: TaskfernDatabase): String {
        val snapshot = BackupStore(db).snapshot()
        return BackupCodec.encode(BackupMapper.toBackup(snapshot, BackupSettings(themeMode = "DARK"), T0, "test"))
    }

    private fun decode(json: String): BackupFile {
        val result = BackupCodec.decode(json)
        assertTrue("backup should be valid: $result", result is BackupReadResult.Success)
        return (result as BackupReadResult.Success).file
    }

    /** The snapshot with alarm bookkeeping cleared and every table in a stable order. */
    private fun DatabaseSnapshot.normalized() = DatabaseSnapshot(
        taskLists = taskLists.sortedBy { it.id },
        tasks = tasks.map { it.copy(nextReminderAt = null) }.sortedBy { it.id },
        subtasks = subtasks.sortedBy { it.id },
        reminders = reminders.sortedBy { it.id },
        occurrenceLog = occurrenceLog.sortedBy { it.id },
        shoppingLists = shoppingLists.sortedBy { it.id },
        shoppingItems = shoppingItems.sortedBy { it.id },
        itemMemory = itemMemory.sortedBy { it.nameKey },
        routines = routines.map { it.copy(nextReminderAt = null) }.sortedBy { it.id },
        routineSteps = routineSteps.sortedBy { it.id },
        routineChecks = routineChecks.sortedWith(compareBy({ it.stepId }, { it.date })),
        templates = templates.sortedBy { it.id },
        focusSessions = focusSessions.sortedBy { it.id },
    )

    /**
     * Contents with ids replaced by what they refer to, so data merged under new ids can be compared with the
     * original. Positions of lists, routines and templates are left out because a merge appends them.
     */
    private fun DatabaseSnapshot.described(): Map<String, List<String>> {
        val lists = taskLists.associate { it.id to it.name }
        val tasksById = tasks.associateBy { it.id }
        fun taskKey(id: Long?) = id?.let { tasksById[it]?.let { t -> "${t.title}@${t.createdAt}" } }
        val shoppingNames = shoppingLists.associate { it.id to it.name }
        val routineNames = routines.associate { it.id to it.name }
        val stepTitles = routineSteps.associate { it.id to it.title }
        return mapOf(
            "lists" to taskLists.map { listOf(it.name, it.color, it.icon, it.favorite, it.createdAt).joinToString("|") },
            "tasks" to tasks.map {
                listOf(
                    it.title, lists[it.listId], it.notes, it.priority, it.dueDate, it.dueTime, it.tags, it.pinned, it.archived,
                    it.isCompleted, it.completedAt, it.createdAt, it.updatedAt, it.deletedAt, it.position, it.recurrence,
                    it.seriesAnchor, it.occurrenceCount, taskKey(it.sourceTaskId), it.snoozeUntil,
                ).joinToString("|")
            },
            "subtasks" to subtasks.map { listOf(taskKey(it.taskId), it.title, it.done, it.position).joinToString("|") },
            "reminders" to reminders.map { "${taskKey(it.taskId)}|${it.offsetMinutes}" },
            "log" to occurrenceLog.map { listOf(taskKey(it.taskId), it.occurrenceDate, it.action, it.at).joinToString("|") },
            "shoppingLists" to shoppingLists.map { listOf(it.name, it.color, it.createdAt).joinToString("|") },
            "items" to shoppingItems.map {
                listOf(shoppingNames[it.listId], it.name, it.quantity, it.unit, it.category, it.checked, it.position, it.priceCents, it.note, it.createdAt, it.checkedAt)
                    .joinToString("|")
            },
            "memory" to itemMemory.map { it.toString() },
            "routines" to routines.map { listOf(it.name, it.emoji, it.color, it.weekdays, it.timeMinutes, it.paused, it.createdAt).joinToString("|") },
            "steps" to routineSteps.map { listOf(routineNames[it.routineId], it.title, it.position).joinToString("|") },
            "checks" to routineChecks.map { listOf(routineNames[it.routineId], stepTitles[it.stepId], it.date, it.checkedAt).joinToString("|") },
            "templates" to templates.map { listOf(it.kind, it.name, it.emoji, it.items, it.builtin).joinToString("|") },
            "focus" to focusSessions.map { listOf(taskKey(it.taskId), it.startedAt, it.plannedMinutes, it.focusedSeconds, it.completed).joinToString("|") },
        ).mapValues { (_, v) -> v.sorted() }
    }

    @Test
    fun replaceRestoresEveryTableWithOriginalIds() = runBlocking<Unit> {
        val source = newDb()
        fill(source)
        val original = BackupStore(source).snapshot()
        val file = decode(exportJson(source))
        assertEquals(original.counts, file.counts)

        val target = newDb()
        target.widgetConfigDao().upsert(WidgetConfigEntity(appWidgetId = 42, kind = WidgetKind.TODAY))
        target.taskDao().insert(TaskEntity(title = "Will be replaced", createdAt = 1, updatedAt = 1))
        BackupStore(target).replace(BackupMapper.fromBackup(file))

        assertEquals(original.normalized(), BackupStore(target).snapshot().normalized())
        // Widget settings are not part of a backup and survive a replace.
        assertNotNull(target.widgetConfigDao().get(42))
    }

    @Test
    fun replaceWithAnEmptyBackupClearsEverything() = runBlocking<Unit> {
        val db = newDb()
        fill(db)
        val empty = decode("{\"format\": \"taskfern-backup\", \"version\": 1, \"exportedAt\": 0}")
        BackupStore(db).replace(BackupMapper.fromBackup(empty))
        assertTrue(BackupStore(db).snapshot().counts.isEmpty)
    }

    @Test
    fun mergeIntoEmptyDatabaseKeepsAllContentAndLinks() = runBlocking<Unit> {
        val source = newDb()
        fill(source)
        val original = BackupStore(source).snapshot()
        val file = decode(exportJson(source))

        val target = newDb()
        val plan = BackupStore(target).merge(BackupMapper.fromBackup(file))
        assertEquals(original.counts, plan.added)
        val merged = BackupStore(target).snapshot()
        assertEquals(original.described(), merged.described())
    }

    @Test
    fun mergingTwiceDoesNotDuplicate() = runBlocking<Unit> {
        val source = newDb()
        fill(source)
        val file = decode(exportJson(source))
        val target = newDb()
        BackupStore(target).merge(BackupMapper.fromBackup(file))
        val once = BackupStore(target).snapshot()

        val second = BackupStore(target).merge(BackupMapper.fromBackup(file))
        assertTrue("second merge added ${second.added}", second.added.isEmpty)
        assertEquals(once.normalized(), BackupStore(target).snapshot().normalized())

        // Merging into the database the backup came from changes nothing either.
        val intoSource = BackupStore(source).merge(BackupMapper.fromBackup(file))
        assertTrue(intoSource.added.isEmpty)
    }

    @Test
    fun mergeCombinesWithExistingDataAndNeverReusesDeletedIds() = runBlocking<Unit> {
        val source = newDb()
        fill(source)
        val file = decode(exportJson(source))

        val target = newDb()
        val t = target.taskDao()
        val errands = t.insertList(TaskListEntity(name = "work", createdAt = 5)) // same name, different case
        val keep = t.insert(TaskEntity(listId = errands, title = "Existing task", createdAt = 7, updatedAt = 7))
        // Use up ids and delete the rows again: imported rows must not take them over.
        repeat(20) { t.insert(TaskEntity(title = "temp $it", createdAt = 8, updatedAt = 8)) }
        val highest = t.exportTasks().maxOf { it.id }
        t.clearTasks()
        t.insert(TaskEntity(id = keep, listId = errands, title = "Existing task", createdAt = 7, updatedAt = 7))

        BackupStore(target).merge(BackupMapper.fromBackup(file))
        val merged = BackupStore(target).snapshot()

        assertEquals(listOf("work", "Home"), merged.taskLists.sortedBy { it.position }.map { it.name })
        val imported = merged.tasks.filter { it.id != keep }
        assertTrue("imported ids ${imported.map { it.id }} must be above $highest", imported.all { it.id > highest })
        val series = merged.tasks.first { it.title == "Weekly review" && !it.isCompleted }
        assertEquals(errands, series.listId)
        assertEquals(series.id, merged.tasks.first { it.title == "Weekly review" && it.isCompleted }.sourceTaskId)
        assertEquals(2, merged.subtasks.count { it.taskId == series.id })
        assertEquals(series.id, merged.focusSessions.first { it.completed }.taskId)
    }

    private companion object {
        const val T0 = 1_761_700_000_000L
    }
}
