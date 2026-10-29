package com.riyadmondol2006.taskfern.backup

import com.riyadmondol2006.taskfern.backup.BackupFixtures.withoutAlarms
import com.riyadmondol2006.taskfern.data.db.RoutineEntity
import com.riyadmondol2006.taskfern.data.db.RoutineStepEntity
import com.riyadmondol2006.taskfern.data.db.ShoppingItemEntity
import com.riyadmondol2006.taskfern.data.db.ShoppingListEntity
import com.riyadmondol2006.taskfern.data.db.TaskEntity
import com.riyadmondol2006.taskfern.data.db.TaskListEntity
import com.riyadmondol2006.taskfern.data.db.TemplateEntity
import com.riyadmondol2006.taskfern.data.db.TemplateKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MergePlannerTest {
    private val incoming = BackupFixtures.snapshot().withoutAlarms()

    @Test
    fun `merging into an empty app keeps every row and every link`() {
        val plan = MergePlanner.plan(DatabaseSnapshot(), incoming)
        assertEquals(incoming.counts, plan.added)
        assertTrue(plan.skipped.isEmpty)
        val s = plan.insert

        val listByName = s.taskLists.associateBy { it.name }
        val tasksByTitleCreated = s.tasks.associateBy { it.title to it.createdAt }
        val series = tasksByTitleCreated.getValue("Weekly review" to BackupFixtures.T0)
        val occurrence = tasksByTitleCreated.getValue("Weekly review" to BackupFixtures.T0 + 100)
        assertEquals(listByName.getValue("Work").id, series.listId)
        assertEquals(series.id, occurrence.sourceTaskId)
        assertEquals(listOf(series.id, series.id), s.subtasks.map { it.taskId })
        assertEquals(setOf(series.id), s.reminders.map { it.taskId }.toSet())
        assertEquals(setOf(series.id), s.occurrenceLog.map { it.taskId }.toSet())
        assertEquals(series.id, s.focusSessions.first { it.startedAt == BackupFixtures.T0 + 30 }.taskId)
        assertNull(s.focusSessions.first { it.startedAt == BackupFixtures.T0 + 40 }.taskId)
        assertEquals(setOf(s.shoppingLists.single().id), s.shoppingItems.map { it.listId }.toSet())
        val routine = s.routines.single()
        assertEquals(setOf(routine.id), s.routineSteps.map { it.routineId }.toSet())
        val water = s.routineSteps.first { it.title == "Water" }
        assertEquals(water.id, s.routineChecks.single().stepId)
        assertEquals(routine.id, s.routineChecks.single().routineId)
    }

    @Test
    fun `new ids start above existing rows and above the autoincrement floor`() {
        val existing = DatabaseSnapshot(
            taskLists = listOf(TaskListEntity(id = 40, name = "Errands", createdAt = 1)),
            tasks = listOf(TaskEntity(id = 500, title = "Existing", createdAt = 1, updatedAt = 1)),
        )
        val plan = MergePlanner.plan(existing, incoming, MergePlanner.IdFloors(tasks = 900, subtasks = 77))
        assertEquals(listOf(41L, 42L), plan.insert.taskLists.map { it.id }.sorted())
        assertEquals(listOf(901L, 902L, 903L, 904L), plan.insert.tasks.map { it.id }.sorted())
        assertEquals(listOf(78L, 79L), plan.insert.subtasks.map { it.id })
        val allIds = plan.insert.tasks.map { it.id } + existing.tasks.map { it.id }
        assertEquals(allIds.size, allIds.toSet().size)
    }

    @Test
    fun `merging the same backup twice adds nothing the second time`() {
        val first = MergePlanner.plan(DatabaseSnapshot(), incoming).insert
        val second = MergePlanner.plan(first, incoming)
        assertTrue("expected nothing added but got ${second.added}", second.added.isEmpty)
        assertEquals(incoming.counts, second.skipped)
    }

    @Test
    fun `lists with the same name are merged, ignoring case and spaces`() {
        val existing = DatabaseSnapshot(taskLists = listOf(TaskListEntity(id = 1, name = "  work ", createdAt = 1, position = 5)))
        val plan = MergePlanner.plan(existing, incoming)
        assertEquals(listOf("Home"), plan.insert.taskLists.map { it.name })
        assertEquals(6, plan.insert.taskLists.single().position) // appended after existing lists
        val series = plan.insert.tasks.first { it.createdAt == BackupFixtures.T0 }
        assertEquals(1L, series.listId)
        assertEquals(1, plan.skipped.taskLists)
    }

    @Test
    fun `a duplicate task is skipped with its children and links point to the existing task`() {
        val existingSeries = TaskEntity(id = 9, title = "Weekly review", createdAt = BackupFixtures.T0, updatedAt = 1)
        val plan = MergePlanner.plan(DatabaseSnapshot(tasks = listOf(existingSeries)), incoming)
        assertTrue(plan.insert.tasks.none { it.createdAt == BackupFixtures.T0 })
        assertTrue(plan.insert.subtasks.isEmpty())
        assertTrue(plan.insert.reminders.isEmpty())
        assertTrue(plan.insert.occurrenceLog.isEmpty())
        // The completed occurrence and the focus session now point at the existing series.
        assertEquals(9L, plan.insert.tasks.first { it.createdAt == BackupFixtures.T0 + 100 }.sourceTaskId)
        assertEquals(9L, plan.insert.focusSessions.first { it.startedAt == BackupFixtures.T0 + 30 }.taskId)
        assertEquals(1, plan.skipped.tasks)
        assertEquals(2, plan.skipped.subtasks)
    }

    @Test
    fun `shopping items merge into a same-named list without duplicating names`() {
        val existing = DatabaseSnapshot(
            shoppingLists = listOf(ShoppingListEntity(id = 5, name = "groceries", createdAt = 1)),
            shoppingItems = listOf(ShoppingItemEntity(id = 8, listId = 5, name = " MILK ", createdAt = 1, position = 3)),
        )
        val plan = MergePlanner.plan(existing, incoming)
        assertTrue(plan.insert.shoppingLists.isEmpty())
        val bread = plan.insert.shoppingItems.single()
        assertEquals("Bread", bread.name)
        assertEquals(5L, bread.listId)
        assertEquals(4, bread.position)
        assertEquals(1, plan.skipped.shoppingItems)
    }

    @Test
    fun `routine steps match by title and unknown steps are appended`() {
        val existing = DatabaseSnapshot(
            routines = listOf(RoutineEntity(id = 3, name = "MORNING", createdAt = 1)),
            routineSteps = listOf(RoutineStepEntity(id = 30, routineId = 3, title = "water", position = 0)),
        )
        val plan = MergePlanner.plan(existing, incoming)
        assertTrue(plan.insert.routines.isEmpty())
        val stretch = plan.insert.routineSteps.single()
        assertEquals("Stretch", stretch.title)
        assertEquals(3L, stretch.routineId)
        assertEquals(1, stretch.position)
        val check = plan.insert.routineChecks.single()
        assertEquals(30L, check.stepId)
        assertEquals(3L, check.routineId)
    }

    @Test
    fun `an existing routine check for the same day is skipped`() {
        val first = MergePlanner.plan(DatabaseSnapshot(), incoming).insert
        val plan = MergePlanner.plan(first, incoming)
        assertTrue(plan.insert.routineChecks.isEmpty())
        assertEquals(LocalDate.of(2026, 10, 28), first.routineChecks.single().date)
    }

    @Test
    fun `templates with the same kind and name are skipped, other kinds are kept`() {
        val existing = DatabaseSnapshot(
            templates = listOf(TemplateEntity(id = 1, kind = TemplateKind.TASKS, name = "Weekly Groceries", items = "x")),
        )
        val plan = MergePlanner.plan(existing, incoming)
        assertEquals(1, plan.insert.templates.size) // different kind, so not a duplicate
        val same = DatabaseSnapshot(
            templates = listOf(TemplateEntity(id = 1, kind = TemplateKind.SHOPPING, name = "weekly groceries", items = "x")),
        )
        assertTrue(MergePlanner.plan(same, incoming).insert.templates.isEmpty())
    }

    @Test
    fun `remembered items are only added when unknown`() {
        val first = MergePlanner.plan(DatabaseSnapshot(), incoming).insert
        assertEquals(1, first.itemMemory.size)
        assertTrue(MergePlanner.plan(first, incoming).insert.itemMemory.isEmpty())
    }

    @Test
    fun `two imported lists sharing a name both survive a merge into an empty app`() {
        val twins = incoming.copy(
            taskLists = listOf(TaskListEntity(id = 1, name = "Ideas", createdAt = 1), TaskListEntity(id = 2, name = "Ideas", createdAt = 2)),
            tasks = emptyList(), subtasks = emptyList(), reminders = emptyList(), occurrenceLog = emptyList(),
            focusSessions = emptyList(),
        )
        assertEquals(2, MergePlanner.plan(DatabaseSnapshot(), twins).insert.taskLists.size)
    }
}
