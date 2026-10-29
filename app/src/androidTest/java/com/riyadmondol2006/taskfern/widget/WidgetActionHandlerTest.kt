package com.riyadmondol2006.taskfern.widget

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riyadmondol2006.taskfern.data.db.TaskfernDatabase
import com.riyadmondol2006.taskfern.data.db.WidgetConfigEntity
import com.riyadmondol2006.taskfern.data.db.WidgetKind
import com.riyadmondol2006.taskfern.data.repo.AddItemResult
import com.riyadmondol2006.taskfern.data.repo.ItemInput
import com.riyadmondol2006.taskfern.data.repo.RoutineDraft
import com.riyadmondol2006.taskfern.data.repo.RoutineRepository
import com.riyadmondol2006.taskfern.data.repo.ShoppingRepository
import com.riyadmondol2006.taskfern.data.repo.StepDraft
import com.riyadmondol2006.taskfern.data.repo.TaskDraft
import com.riyadmondol2006.taskfern.data.repo.TaskRepository
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The data effects of widget taps, against a real (in-memory) database. */
@RunWith(AndroidJUnit4::class)
class WidgetActionHandlerTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 10, 29)
    private val clock = Clock.fixed(today.atTime(LocalTime.of(10, 0)).atZone(zone).toInstant(), zone)

    private lateinit var db: TaskfernDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var shopping: ShoppingRepository
    private lateinit var routines: RoutineRepository
    private lateinit var handler: WidgetActionHandler

    @Before
    fun setUp() {
        db = TaskfernDatabase.build(ApplicationProvider.getApplicationContext(), inMemory = true)
        tasks = TaskRepository(db, clock = clock)
        shopping = ShoppingRepository(db, clock = clock)
        routines = RoutineRepository(db, clock = clock)
        handler = WidgetActionHandler(tasks, shopping, routines, today = { LocalDate.now(clock) })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun doubleTapOnARecurringTaskCompletesOnlyTheShownOccurrence() = runTest {
        val id = tasks.create(TaskDraft(title = "Water the ferns", dueDate = today, recurrence = RecurrenceRule.daily()))
        val request = CompleteTaskRequest(id, today)

        assertTrue(handler.completeTask(request))
        assertFalse(handler.completeTask(request))

        val series = tasks.get(id)!!
        assertFalse(series.isCompleted)
        assertEquals(today.plusDays(1), series.dueDate)
        assertEquals(1, db.taskDao().observeCompletedSince(0).first().size)
    }

    @Test
    fun aTapFromAnOutdatedWidgetDoesNothing() = runTest {
        val id = tasks.create(TaskDraft(title = "Pay rent", dueDate = today))
        // The widget still showed yesterday's due date; the task has since been moved to today.
        assertFalse(handler.completeTask(CompleteTaskRequest(id, today.minusDays(1))))
        assertFalse(tasks.get(id)!!.isCompleted)

        assertTrue(handler.completeTask(CompleteTaskRequest(id, today)))
        assertTrue(tasks.get(id)!!.isCompleted)
        assertFalse(handler.completeTask(CompleteTaskRequest(id, today)))
    }

    @Test
    fun completingAMissingTaskIsIgnored() = runTest {
        assertFalse(handler.completeTask(CompleteTaskRequest(999, today)))
    }

    @Test
    fun shoppingCheckIsIdempotentAndIgnoresMissingItems() = runTest {
        val listId = shopping.createList("Groceries")
        val itemId = (shopping.addItem(listId, ItemInput("Oat milk", "2", "l")) as AddItemResult.Added).id

        handler.setShoppingItem(ShoppingCheckRequest(itemId, checked = true))
        handler.setShoppingItem(ShoppingCheckRequest(itemId, checked = true))
        assertTrue(db.shoppingDao().getItem(itemId)!!.checked)
        assertEquals(0, db.shoppingDao().remainingCount(listId))

        handler.setShoppingItem(ShoppingCheckRequest(itemId, checked = false))
        assertFalse(db.shoppingDao().getItem(itemId)!!.checked)

        handler.setShoppingItem(ShoppingCheckRequest(12345, checked = true))
        assertNull(db.shoppingDao().getItem(12345))
    }

    @Test
    fun routineStepsToggleForTodayOnly() = runTest {
        val routineId = routines.create(RoutineDraft(name = "Morning", steps = listOf(StepDraft(title = "Water"), StepDraft(title = "Stretch"))))
        val steps = routines.getSteps(routineId)
        val first = steps[0].id

        assertTrue(handler.setRoutineStep(RoutineStepRequest(first, routineId, today, checked = true)))
        assertTrue(handler.setRoutineStep(RoutineStepRequest(first, routineId, today, checked = true)))
        assertEquals(listOf(true, false), routines.getStepsWithChecks(routineId, today).map { it.checked })

        // Yesterday's checklist (a widget not yet redrawn after midnight) must not change anything.
        assertFalse(handler.setRoutineStep(RoutineStepRequest(steps[1].id, routineId, today.minusDays(1), checked = true)))
        assertEquals(listOf(true, false), routines.getStepsWithChecks(routineId, today).map { it.checked })
        assertEquals(listOf(false, false), routines.getStepsWithChecks(routineId, today.minusDays(1)).map { it.checked })

        assertTrue(handler.setRoutineStep(RoutineStepRequest(first, routineId, today, checked = false)))
        assertEquals(listOf(false, false), routines.getStepsWithChecks(routineId, today).map { it.checked })
    }

    @Test
    fun aStepOfAnotherRoutineOrADeletedStepIsIgnored() = runTest {
        val a = routines.create(RoutineDraft(name = "A", steps = listOf(StepDraft(title = "One"))))
        val b = routines.create(RoutineDraft(name = "B", steps = listOf(StepDraft(title = "Two"))))
        val stepOfB = routines.getSteps(b).single().id

        assertFalse(handler.setRoutineStep(RoutineStepRequest(stepOfB, a, today, checked = true)))
        assertFalse(handler.setRoutineStep(RoutineStepRequest(9999, a, today, checked = true)))
        assertEquals(listOf(false), routines.getStepsWithChecks(b, today).map { it.checked })
    }

    @Test
    fun widgetConfigRoundTrips() = runTest {
        val dao = db.widgetConfigDao()
        dao.upsert(WidgetConfigEntity(appWidgetId = 11, kind = WidgetKind.SHOPPING, targetId = 3))
        dao.upsert(WidgetConfigEntity(appWidgetId = 12, kind = WidgetKind.QUICK_ADD, targetId = null, option = "tomorrow"))
        assertEquals(WidgetConfigEntity(11, WidgetKind.SHOPPING, 3, ""), dao.get(11))

        dao.upsert(WidgetConfigEntity(appWidgetId = 11, kind = WidgetKind.SHOPPING, targetId = 5))
        assertEquals(5L, dao.get(11)!!.targetId)
        assertEquals(QuickAddDue.TOMORROW, QuickAddDue.fromKey(dao.get(12)!!.option))

        dao.delete(listOf(11))
        assertNull(dao.get(11))
        assertEquals(listOf(12), dao.all().map { it.appWidgetId })
    }
}
