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
class SubtaskRepositoryTest {
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

    private suspend fun titles(taskId: Long) = repo.observeSubtasks(taskId).first().map { it.title }

    @Test fun addTickRenameAndObserve() = runBlocking {
        val id = repo.create(TaskDraft(title = "Pack for the trip"))
        assertTrue(repo.observeSubtasks(id).first().isEmpty())

        assertNotNull(repo.addSubtask(id, "  Passport "))
        assertNull("blank titles are ignored", repo.addSubtask(id, "   "))
        assertEquals(2, repo.addSubtasks(id, listOf("Charger", "", "Socks")))
        assertEquals(listOf("Passport", "Charger", "Socks"), titles(id))

        val charger = repo.observeSubtasks(id).first()[1]
        repo.setSubtaskDone(charger.id, true)
        assertTrue(repo.observeSubtasks(id).first()[1].done)
        val row = repo.observeRow(id).first()!!
        assertEquals(3, row.subtaskTotal)
        assertEquals(1, row.subtaskDone)

        // Ticking twice (a double tap) leaves it ticked.
        repo.setSubtaskDone(charger.id, true)
        assertTrue(repo.observeSubtasks(id).first()[1].done)

        repo.renameSubtask(charger.id, "Phone charger")
        repo.renameSubtask(charger.id, "  ")
        val renamed = repo.observeSubtasks(id).first()[1]
        assertEquals("Phone charger", renamed.title)
        assertTrue("renaming keeps the tick", renamed.done)
    }

    @Test fun deleteAndUndoRestoresTheSamePlace() = runBlocking {
        val id = repo.create(TaskDraft(title = "Bake", subtasks = listOf("Flour", "Eggs", "Sugar", "Butter")))
        val eggs = repo.observeSubtasks(id).first()[1]
        repo.setSubtaskDone(eggs.id, true)
        val ticked = repo.observeSubtasks(id).first()[1]

        assertTrue(repo.deleteSubtask(ticked))
        assertEquals(listOf("Flour", "Sugar", "Butter"), titles(id))

        assertTrue(repo.restoreSubtask(ticked))
        val back = repo.observeSubtasks(id).first()
        assertEquals(listOf("Flour", "Eggs", "Sugar", "Butter"), back.map { it.title })
        assertEquals(ticked.id, back[1].id)
        assertTrue(back[1].done)

        // A second undo (or an undo for something never deleted) changes nothing.
        assertFalse(repo.restoreSubtask(ticked))
        assertEquals(4, repo.observeSubtasks(id).first().size)
    }

    @Test fun undoAfterReorderingStillLandsAtTheOldIndex() = runBlocking {
        val id = repo.create(TaskDraft(title = "Morning", subtasks = listOf("A", "B", "C", "D")))
        val b = repo.observeSubtasks(id).first()[1]
        repo.deleteSubtask(b)
        val d = repo.observeSubtasks(id).first().last()
        assertTrue(repo.moveSubtask(d.id, -2)) // D to the top: D A C
        assertEquals(listOf("D", "A", "C"), titles(id))
        repo.restoreSubtask(b)
        assertEquals(listOf("D", "B", "A", "C"), titles(id))
    }

    @Test fun reorder() = runBlocking {
        val id = repo.create(TaskDraft(title = "Clean", subtasks = listOf("Kitchen", "Bath", "Hall")))
        val list = repo.observeSubtasks(id).first()

        assertTrue(repo.moveSubtask(list[2].id, -1))
        assertEquals(listOf("Kitchen", "Hall", "Bath"), titles(id))
        assertFalse("already at the top", repo.moveSubtask(list[0].id, -1))
        assertFalse("already at the bottom", repo.moveSubtask(list[1].id, 1))

        val current = repo.observeSubtasks(id).first()
        repo.setSubtaskDone(current[0].id, true)
        // A whole-list reorder writes positions only, so a stale copy cannot undo the tick.
        repo.reorderSubtasks(current.reversed())
        val after = repo.observeSubtasks(id).first()
        assertEquals(listOf("Bath", "Hall", "Kitchen"), after.map { it.title })
        assertTrue(after.last().done)

        // New subtasks go to the end.
        repo.addSubtask(id, "Garage")
        assertEquals("Garage", titles(id).last())
    }

    @Test fun deletingTheLastSubtaskIsFine() = runBlocking {
        val id = repo.create(TaskDraft(title = "Call", subtasks = listOf("Dial")))
        val only = repo.observeSubtasks(id).first().single()
        assertTrue(repo.deleteSubtask(only))
        assertTrue(repo.observeSubtasks(id).first().isEmpty())
        assertEquals(0, repo.observeRow(id).first()!!.subtaskTotal)
        assertFalse("deleting twice does nothing", repo.deleteSubtask(only))
    }

    @Test fun actingOnSubtasksOfADeletedTaskDoesNotCrash() = runBlocking {
        val id = repo.create(TaskDraft(title = "Gone soon", subtasks = listOf("One", "Two")))
        val (one, two) = repo.observeSubtasks(id).first()
        repo.deleteSubtask(two)
        repo.moveToTrash(listOf(id))
        repo.deleteForever(listOf(id))
        assertNull(repo.get(id))
        assertTrue(repo.observeSubtasks(id).first().isEmpty())

        repo.updateSubtask(one.copy(done = true))
        repo.setSubtaskDone(one.id, true)
        repo.renameSubtask(one.id, "Renamed")
        assertFalse(repo.deleteSubtask(one))
        assertFalse(repo.moveSubtask(one.id, 1))
        assertFalse("undo after the task is gone does nothing", repo.restoreSubtask(two))
        assertNull(repo.addSubtask(id, "Late"))
        assertEquals(0, repo.addSubtasks(id, listOf("Late", "Later")))
        repo.reorderSubtasks(listOf(one, two))
        assertTrue(repo.observeSubtasks(id).first().isEmpty())
    }

    @Test fun completingARepeatingTaskResetsTicksForTheNextOccurrence() = runBlocking {
        val id = repo.create(TaskDraft(title = "Weekly review", dueDate = today, recurrence = RecurrenceRule.daily(7), subtasks = listOf("Inbox")))
        val step = repo.observeSubtasks(id).first().single()
        repo.setSubtaskDone(step.id, true)
        val undo = repo.complete(id)!!
        assertFalse(repo.observeSubtasks(id).first().single().done)
        repo.undoCompletion(undo)
        assertTrue(repo.observeSubtasks(id).first().single().done)
    }
}
