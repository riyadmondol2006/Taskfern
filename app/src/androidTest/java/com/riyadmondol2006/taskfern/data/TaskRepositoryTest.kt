package com.riyadmondol2006.taskfern.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.riyadmondol2006.taskfern.data.db.TaskfernDatabase
import com.riyadmondol2006.taskfern.data.repo.TaskDraft
import com.riyadmondol2006.taskfern.data.repo.TaskRepository
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TaskRepositoryTest {
    private lateinit var db: TaskfernDatabase
    private lateinit var repo: TaskRepository
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 10, 29)
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)

    @Before fun setUp() {
        db = TaskfernDatabase.build(InstrumentationRegistry.getInstrumentation().targetContext, inMemory = true)
        repo = TaskRepository(db, clock = clock)
    }

    @After fun tearDown() = db.close()

    @Test fun createEditComplete() = runBlocking {
        val id = repo.create(TaskDraft(title = "  Buy milk ", dueDate = today, reminderOffsets = listOf(0, 60), subtasks = listOf("Oat", "")))
        val details = repo.getDetails(id)!!
        assertEquals("Buy milk", details.task.title)
        assertEquals(2, details.reminders.size)
        assertEquals(1, details.subtasks.size)

        repo.update(id, TaskDraft(title = "Buy oat milk", dueDate = today.plusDays(1), priority = 3, tags = listOf("#Shop", "shop")))
        val edited = repo.get(id)!!
        assertEquals("Buy oat milk", edited.title)
        assertEquals(listOf("shop"), edited.tagList)
        assertEquals(0, repo.getDetails(id)!!.reminders.size) // reminders replaced by the draft's (none)

        val undo = repo.complete(id)!!
        assertTrue(repo.get(id)!!.isCompleted)
        repo.undoCompletion(undo)
        assertFalse(repo.get(id)!!.isCompleted)
    }

    @Test fun completingRecurringTaskRollsForwardAndKeepsHistory() = runBlocking {
        val id = repo.create(TaskDraft(title = "Water plants", dueDate = today, recurrence = RecurrenceRule.daily(2), subtasks = listOf("Balcony")))
        val sub = repo.getDetails(id)!!.subtasks.single()
        repo.updateSubtask(sub.copy(done = true))

        val undo = repo.complete(id)!!
        val series = repo.get(id)!!
        assertFalse(series.isCompleted)
        assertEquals(today.plusDays(2), series.dueDate)
        assertEquals(1, series.occurrenceCount)
        assertFalse("checklist resets for the next occurrence", repo.getDetails(id)!!.subtasks.single().done)

        val snapshot = repo.get(undo.snapshotId!!)!!
        assertTrue(snapshot.isCompleted)
        assertNull(snapshot.recurrence)
        assertEquals(id, snapshot.sourceTaskId)
        assertEquals(today, snapshot.dueDate)
        assertTrue(repo.getDetails(snapshot.id)!!.subtasks.single().done)
        assertEquals(1, repo.observeLog(id).first().size)

        // Undo puts everything back exactly.
        repo.undoCompletion(undo)
        assertEquals(today, repo.get(id)!!.dueDate)
        assertEquals(0, repo.get(id)!!.occurrenceCount)
        assertNull(repo.get(undo.snapshotId!!))
        assertTrue(repo.observeLog(id).first().isEmpty())
        assertTrue(repo.getDetails(id)!!.subtasks.single().done)
    }

    @Test fun staleCompleteActionIsIgnored() = runBlocking {
        val id = repo.create(TaskDraft(title = "Stretch", dueDate = today, recurrence = RecurrenceRule.daily()))
        // First tap from a widget completes today's occurrence.
        assertNotNull(repo.complete(id, expectedDue = today, checkExpected = true))
        // A second tap carrying the same (now old) due date must not complete tomorrow's occurrence.
        assertNull(repo.complete(id, expectedDue = today, checkExpected = true))
        assertEquals(today.plusDays(1), repo.get(id)!!.dueDate)
        assertEquals(1, repo.observeCompleted(10).first().size)
    }

    @Test fun lastOccurrenceCompletesTheSeries() = runBlocking {
        val id = repo.create(TaskDraft(title = "Course", dueDate = today, recurrence = RecurrenceRule.weekly(1).copy(maxOccurrences = 2)))
        repo.complete(id)
        assertFalse(repo.get(id)!!.isCompleted)
        repo.complete(id)
        val done = repo.get(id)!!
        assertTrue(done.isCompleted)
        assertEquals(2, done.occurrenceCount)
    }

    @Test fun skipMovesWithoutCompleting() = runBlocking {
        val id = repo.create(TaskDraft(title = "Gym", dueDate = today, recurrence = RecurrenceRule.daily()))
        repo.skipOccurrence(id)
        assertEquals(today.plusDays(1), repo.get(id)!!.dueDate)
        assertTrue(repo.observeCompleted(10).first().isEmpty())
    }

    @Test fun pauseAndResumeRealignsToToday() = runBlocking {
        val id = repo.create(TaskDraft(title = "Read", dueDate = today.minusDays(5), recurrence = RecurrenceRule.daily()))
        repo.setSeriesPaused(id, true)
        assertTrue(repo.observeDueOnOrBefore(today).first().none { it.task.id == id })
        repo.setSeriesPaused(id, false)
        assertEquals(today, repo.get(id)!!.dueDate)
    }

    @Test fun trashRestoreAndPurge() = runBlocking {
        val id = repo.create(TaskDraft(title = "Old"))
        repo.moveToTrash(listOf(id))
        assertTrue(repo.observeOpenRows().first().isEmpty())
        assertEquals(1, repo.observeTrash().first().size)
        repo.restoreFromTrash(listOf(id))
        assertEquals(1, repo.observeOpenRows().first().size)
        repo.moveToTrash(listOf(id))
        // Purging with a fixed clock removes nothing deleted "now"; emptying the trash removes it.
        assertEquals(0, repo.purgeTrashOlderThan(30))
        assertEquals(1, repo.emptyTrash())
        assertNull(repo.get(id))
    }

    @Test fun deletingAListKeepsOrTrashesItsTasks() = runBlocking {
        val keepList = repo.createList("Work")
        val dropList = repo.createList("Old")
        val a = repo.create(TaskDraft(title = "A", listId = keepList))
        val b = repo.create(TaskDraft(title = "B", listId = dropList))
        repo.deleteList(keepList, keepTasks = true)
        repo.deleteList(dropList, keepTasks = false)
        assertNull(repo.get(a)!!.listId)
        assertNull(repo.get(a)!!.deletedAt)
        assertNotNull(repo.get(b)!!.deletedAt)
    }

    @Test fun searchEscapesWildcards() = runBlocking {
        repo.create(TaskDraft(title = "100% done"))
        repo.create(TaskDraft(title = "Something else"))
        assertEquals(1, repo.search("100%").first().size)
        assertEquals(0, repo.search("_").first().size)
    }

    @Test fun duplicateCopiesStepsAndReminders() = runBlocking {
        val id = repo.create(TaskDraft(title = "Pack", dueDate = today, reminderOffsets = listOf(30), subtasks = listOf("Socks", "Charger")))
        val copy = repo.duplicate(id)!!
        val d = repo.getDetails(copy)!!
        assertEquals(2, d.subtasks.size)
        assertEquals(listOf(30), d.reminders.map { it.offsetMinutes })
    }

    @Test fun thousandsOfTasksStayFast() = runBlocking {
        repeat(3000) { i -> repo.create(TaskDraft(title = "Task $i", dueDate = today.plusDays((i % 90 - 30).toLong()), priority = i % 4)) }
        val start = System.nanoTime()
        val open = repo.observeOpenRows().first()
        val due = repo.observeDueOnOrBefore(today).first()
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(3000, open.size)
        assertTrue(due.isNotEmpty())
        assertTrue("Loading 3000 tasks took $ms ms", ms < 1500)
    }
}
