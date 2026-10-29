package com.riyadmondol2006.taskfern.abuse

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.riyadmondol2006.taskfern.data.db.TaskfernDatabase
import com.riyadmondol2006.taskfern.data.repo.AddItemResult
import com.riyadmondol2006.taskfern.data.repo.ItemInput
import com.riyadmondol2006.taskfern.data.repo.RoutineDraft
import com.riyadmondol2006.taskfern.data.repo.RoutineRepository
import com.riyadmondol2006.taskfern.data.repo.ShoppingRepository
import com.riyadmondol2006.taskfern.data.repo.StepDraft
import com.riyadmondol2006.taskfern.data.repo.TaskDraft
import com.riyadmondol2006.taskfern.data.repo.TaskRepository
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * The ways people (and stale widgets, notifications and double taps) break apps, run against the real database.
 * None of these may crash, corrupt data or complete more than the user meant to.
 */
@RunWith(AndroidJUnit4::class)
class UserAbuseTest {
    private lateinit var db: TaskfernDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var shopping: ShoppingRepository
    private lateinit var routines: RoutineRepository
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 10, 29)
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)

    @Before fun setUp() {
        db = TaskfernDatabase.build(InstrumentationRegistry.getInstrumentation().targetContext, inMemory = true)
        tasks = TaskRepository(db, clock = clock)
        shopping = ShoppingRepository(db, clock = clock)
        routines = RoutineRepository(db, clock = clock)
    }

    @After fun tearDown() = db.close()

    // ---- Frantic tapping ---------------------------------------------------------------------------------------------

    @Test fun twentySimultaneousTapsCompleteOneOccurrence() = runBlocking<Unit> {
        val id = tasks.create(TaskDraft(title = "Stretch", dueDate = today, recurrence = RecurrenceRule.daily()))
        val due = tasks.get(id)!!.dueDate
        val results = (1..20).map { async(Dispatchers.IO) { tasks.complete(id, expectedDue = due, checkExpected = true) } }.awaitAll()
        assertEquals(1, results.count { it != null })
        assertEquals(today.plusDays(1), tasks.get(id)!!.dueDate)
        assertEquals(1, tasks.observeCompleted(50).first().size)
    }

    @Test fun simultaneousTapsOnAOneOffTaskCompleteItOnce() = runBlocking<Unit> {
        val id = tasks.create(TaskDraft(title = "Pay rent", dueDate = today))
        val results = (1..20).map { async(Dispatchers.IO) { tasks.complete(id) } }.awaitAll()
        assertEquals(1, results.count { it != null })
        assertTrue(tasks.get(id)!!.isCompleted)
    }

    @Test fun doubleTapOnARowInTheAppCompletesOneOccurrence() = runBlocking<Unit> {
        val id = tasks.create(TaskDraft(title = "Vitamins", dueDate = today, recurrence = RecurrenceRule.daily()))
        val row = tasks.observeRow(id).first()!!
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            // Two taps on the same row before the list redraws: both carry the same (old) due date.
            val vm = com.riyadmondol2006.taskfern.ui.tasks.TaskActionsViewModel(tasks)
            vm.toggle(row)
            vm.toggle(row)
        }
        // Let both launched coroutines finish.
        repeat(50) { if (tasks.observeCompleted(10).first().isNotEmpty()) return@repeat; kotlinx.coroutines.delay(20) }
        kotlinx.coroutines.delay(300)
        assertEquals(1, tasks.observeCompleted(10).first().size)
        assertEquals(today.plusDays(1), tasks.get(id)!!.dueDate)
    }

    @Test fun undoPressedTwiceIsHarmless() = runBlocking<Unit> {
        val id = tasks.create(TaskDraft(title = "Gym", dueDate = today, recurrence = RecurrenceRule.daily()))
        val undo = tasks.complete(id)!!
        tasks.undoCompletion(undo)
        tasks.undoCompletion(undo)
        assertEquals(today, tasks.get(id)!!.dueDate)
        assertTrue(tasks.observeCompleted(50).first().isEmpty())
    }

    @Test fun completeUndoCompleteAgainKeepsCountsRight() = runBlocking<Unit> {
        val id = tasks.create(TaskDraft(title = "Course", dueDate = today, recurrence = RecurrenceRule.daily().copy(maxOccurrences = 2)))
        repeat(5) { tasks.undoCompletion(tasks.complete(id)!!) }
        tasks.complete(id)
        assertEquals(1, tasks.get(id)!!.occurrenceCount)
        tasks.complete(id)
        assertTrue(tasks.get(id)!!.isCompleted)
    }

    // ---- Acting on things that are gone (stale widgets, notifications, second screens) --------------------------------

    @Test fun actionsOnDeletedTasksDoNothing() = runBlocking<Unit> {
        val id = tasks.create(TaskDraft(title = "Old", dueDate = today, recurrence = RecurrenceRule.daily()))
        tasks.moveToTrash(listOf(id))
        assertNull(tasks.complete(id))
        tasks.deleteForever(listOf(id))
        assertNull(tasks.complete(id))
        assertNull(tasks.skipOccurrence(id))
        tasks.snooze(id, 0)
        tasks.setDueDate(listOf(id), today)
        tasks.setSeriesPaused(id, true)
        tasks.update(id, TaskDraft(title = "Ghost"))
        tasks.reopen(id)
        tasks.addSubtask(id, "Step on a deleted task")
        tasks.restoreFromTrash(listOf(id))
        tasks.duplicate(id)
        assertNull(tasks.get(id))
        assertTrue(tasks.observeOpenRows().first().isEmpty())
    }

    @Test fun nonsenseIdsAreIgnored() = runBlocking<Unit> {
        listOf(0L, -1L, Long.MAX_VALUE).forEach { bad ->
            assertNull(tasks.complete(bad))
            tasks.moveToTrash(listOf(bad))
            tasks.reorder(listOf(bad, bad))
            tasks.setPriority(listOf(bad), 3)
        }
    }

    @Test fun taskForADeletedListLandsInTheInbox() = runBlocking<Unit> {
        val list = tasks.createList("Temp")
        tasks.deleteList(list, keepTasks = true)
        val id = tasks.create(TaskDraft(title = "Orphan", listId = list))
        assertNull(tasks.get(id)!!.listId)
    }

    @Test fun shoppingActionsOnDeletedThingsDoNothing() = runBlocking<Unit> {
        val list = shopping.createList("Groceries")
        val item = (shopping.addItem(list, ItemInput("Milk")) as AddItemResult.Added).id
        shopping.deleteList(list)
        shopping.setChecked(item, true)
        shopping.clearChecked(list)
        shopping.uncheckAll(list)
        shopping.renameList(list, "Back?")
        assertNull(shopping.duplicateList(list))
        // Adding to a list deleted moments ago (an open screen, a widget shortcut) must not crash.
        assertEquals(AddItemResult.ListGone, shopping.addItem(list, ItemInput("Bread")))
        // Undo of a deleted item after its list is gone.
        shopping.restoreItem(com.riyadmondol2006.taskfern.data.db.ShoppingItemEntity(listId = list, name = "Eggs", createdAt = 0))
        assertTrue(shopping.getLists().isEmpty())
    }

    @Test fun focusOnADeletedTaskStillStarts() = runBlocking<Unit> {
        val id = tasks.create(TaskDraft(title = "Write report"))
        tasks.deleteForever(listOf(id))
        val focus = com.riyadmondol2006.taskfern.data.repo.FocusRepository(db)
        val session = focus.start(id, clock.millis(), 25)
        focus.finish(session, 60, completed = false)
        assertTrue(session > 0)
    }

    @Test fun routineActionsOnDeletedRoutinesDoNothing() = runBlocking<Unit> {
        val id = routines.create(RoutineDraft(name = "Morning", steps = listOf(StepDraft(title = "Water"))))
        val step = routines.getSteps(id).single().id
        routines.delete(id)
        // A widget drawn before the deletion still carries these ids.
        routines.setStepChecked(step, id, today, true)
        routines.toggleStep(step, id, today)
        routines.checkAll(id, today)
        routines.resetDay(id, today)
        routines.setPaused(id, true)
        assertTrue(routines.getRoutines().isEmpty())
    }

    // ---- Odd input ---------------------------------------------------------------------------------------------------

    @Test fun hostileTextIsStoredExactlyAndSearchable() = runBlocking<Unit> {
        val titles = listOf(
            "'; DROP TABLE tasks; --", "100% sure_not_wild", "🌿 Water the ferns 🌿", "اتصل بأمي", "明日の買い物",
            "line\nbreak", "\\backslash\\", "a".repeat(20_000),
        )
        val ids = titles.map { tasks.create(TaskDraft(title = it, notes = it)) }
        ids.zip(titles).forEach { (id, t) -> assertEquals(t.trim(), tasks.get(id)!!.title) }
        assertEquals(1, tasks.search("DROP TABLE").first().size)
        assertEquals(1, tasks.search("100%").first().size)
        assertEquals(1, tasks.search("_not_").first().size)
        assertEquals(1, tasks.search("🌿").first().size)
        assertEquals(titles.size, tasks.observeOpenRows().first().size)
    }

    @Test fun blankTitlesAreRefusedWithoutHarm() = runBlocking<Unit> {
        listOf("", "   ", "\n\t").forEach { blank ->
            val failed = runCatching { tasks.create(TaskDraft(title = blank)) }.isFailure
            assertTrue("blank title was accepted", failed)
        }
        assertTrue(tasks.observeOpenRows().first().isEmpty())
        // Shopping: a blank item never reaches the list.
        val list = shopping.createList("  ")
        assertTrue(runCatching { shopping.addItem(list, ItemInput("   ")) }.isFailure)
        assertEquals("Shopping", shopping.getList(list)!!.name)
    }

    @Test fun addingTheSameItemTenTimesAtOnceAddsItOnce() = runBlocking<Unit> {
        val list = shopping.createList("Groceries")
        val results = (1..10).map { async(Dispatchers.IO) { shopping.addItem(list, ItemInput("Bananas")) } }.awaitAll()
        assertEquals(1, results.count { it is AddItemResult.Added })
        assertEquals(1, shopping.getItems(list).size)
    }

    @Test fun reminderOffsetsAndPrioritiesAreClamped() = runBlocking<Unit> {
        val id = tasks.create(TaskDraft(title = "Clamp", priority = 99, dueDate = today, reminderOffsets = listOf(0, 0, 0, 60)))
        assertEquals(3, tasks.get(id)!!.priority)
        assertEquals(listOf(0, 60), tasks.reminderOffsets(id))
    }

    @Test fun aRepeatingTaskCannotLoseItsDate() = runBlocking<Unit> {
        val id = tasks.create(TaskDraft(title = "Series", dueDate = today, recurrence = RecurrenceRule.weekdays()))
        tasks.setDueDate(listOf(id), null)
        assertEquals(today, tasks.get(id)!!.dueDate)
        tasks.update(id, TaskDraft(title = "Series", dueDate = null, recurrence = RecurrenceRule.weekdays()))
        assertTrue(tasks.get(id)!!.dueDate != null)
    }

    @Test fun tenThousandTasksDoNotSlowTheMainQueries() = runBlocking<Unit> {
        run {
            val now = clock.millis()
            db.runInTransaction {
                repeat(10_000) { i ->
                    db.openHelper.writableDatabase.execSQL(
                        "INSERT INTO tasks (title, notes, priority, due_date, tags, pinned, archived, is_completed, created_at, updated_at, position, occurrence_count) " +
                            "VALUES ('Task $i', '', ${i % 4}, ${today.toEpochDay() + i % 120 - 30}, '', 0, 0, ${if (i % 5 == 0) 1 else 0}, $now, $now, $i, 0)",
                    )
                }
            }
            val start = System.nanoTime()
            val open = tasks.observeOpenRows().first()
            val due = tasks.observeDueOnOrBefore(today).first()
            val search = tasks.search("Task 99").first()
            val counts = tasks.observeDueCounts(today, today.plusDays(41)).first()
            val ms = (System.nanoTime() - start) / 1_000_000
            assertEquals(8_000, open.size)
            assertTrue(due.isNotEmpty() && search.isNotEmpty() && counts.isNotEmpty())
            assertTrue("main queries over 10 000 tasks took $ms ms", ms < 3_000)
        }
    }
}
