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

    @Test fun streakCountsDaysInARowAndBreaksOnAMiss() = runBlocking<Unit> {
        fun repoOn(day: LocalDate) = TaskRepository(db, clock = Clock.fixed(day.atTime(20, 0).atZone(zone).toInstant(), zone))
        val start = today.minusDays(5)
        val id = repoOn(start).create(TaskDraft(title = "Floss", dueDate = start, recurrence = RecurrenceRule.daily()))
        // Done on time three days running.
        for (offset in 0L..2L) repoOn(start.plusDays(offset)).complete(id)
        assertEquals(3, repoOn(start.plusDays(3)).observeTaskStreaks().first()[id]?.current)
        // Day 3 and 4 missed; on day 5 (today) it is overdue, so the current streak is gone but the best stays.
        val s = repo.observeStreak(id).first()
        assertEquals(0, s.current)
        assertEquals(3, s.best)
        // Finishing the overdue occurrence late counts for it, but the days it skipped still break the run…
        repo.complete(id)
        assertEquals(0, repo.observeStreak(id).first().current)
        // …and the next on-time completion starts a new streak of one.
        repoOn(today.plusDays(1)).complete(id)
        assertEquals(1, repoOn(today.plusDays(1)).observeStreak(id).first().current)
    }

    @Test fun routineStreakFollowsCompletedDays() = runBlocking<Unit> {
        val routines = com.riyadmondol2006.taskfern.data.repo.RoutineRepository(
            db, clock = Clock.fixed(today.minusDays(10).atStartOfDay(zone).toInstant(), zone),
        )
        val rid = routines.create(com.riyadmondol2006.taskfern.data.repo.RoutineDraft(
            name = "Morning", steps = listOf(com.riyadmondol2006.taskfern.data.repo.StepDraft(title = "Water"), com.riyadmondol2006.taskfern.data.repo.StepDraft(title = "Stretch")),
        ))
        val steps = routines.getSteps(rid)
        for (back in 1L..4L) steps.forEach { routines.setStepChecked(it.id, rid, today.minusDays(back), true) }
        routines.setStepChecked(steps.first().id, rid, today, true) // today half done: streak still alive
        assertEquals(4, routines.observeRoutineStreaks(today).first()[rid]?.current)
        steps.drop(1).forEach { routines.setStepChecked(it.id, rid, today, true) }
        assertEquals(5, routines.observeRoutineStreaks(today).first()[rid]?.current)
    }

    @Test fun plansStayOutOfDayListsUntilTheirPeriodEnds() = runBlocking<Unit> {
        val weekEnd = com.riyadmondol2006.taskfern.domain.PlanPeriods.end(com.riyadmondol2006.taskfern.data.db.DueScope.WEEK, today, 0)
        val id = repo.create(
            TaskDraft(
                title = "Clean the garage", dueDate = weekEnd, dueTime = java.time.LocalTime.NOON,
                dueScope = com.riyadmondol2006.taskfern.data.db.DueScope.WEEK, recurrence = RecurrenceRule.daily(),
            ),
        )
        val t = repo.get(id)!!
        assertEquals(com.riyadmondol2006.taskfern.data.db.DueScope.WEEK, t.dueScope)
        assertNull("plans have no time", t.dueTime)
        assertNull("plans do not repeat", t.recurrence)
        // Not in Today's day list or the calendar, but in Plans.
        assertTrue(repo.observeDueOnOrBefore(weekEnd).first().none { it.task.id == id })
        assertTrue(repo.observeDueBetween(today, weekEnd).first().none { it.task.id == id })
        assertEquals(1, repo.observePlans(today).first().size)
        // Once the week is over it is simply overdue.
        assertTrue(repo.observeDueOnOrBefore(weekEnd.plusDays(1)).first().any { it.task.id == id })
        // Picking a day turns it back into a day task.
        repo.setDueDate(listOf(id), today)
        assertEquals(com.riyadmondol2006.taskfern.data.db.DueScope.DAY, repo.get(id)!!.dueScope)
        assertTrue(repo.observeDueOnOrBefore(today).first().any { it.task.id == id })
    }
}
