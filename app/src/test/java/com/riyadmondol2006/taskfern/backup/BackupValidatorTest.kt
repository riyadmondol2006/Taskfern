package com.riyadmondol2006.taskfern.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidatorTest {
    private val valid = BackupFixtures.file()

    private fun problems(file: BackupFile) = BackupValidator.validate(file)

    private fun assertProblem(file: BackupFile, fragment: String) {
        val p = problems(file)
        assertTrue("expected a problem containing '$fragment' in $p", p.any { fragment in it })
    }

    @Test
    fun `the fixture is valid`() {
        assertEquals(emptyList<String>(), problems(valid))
    }

    @Test
    fun `blank titles and names are rejected`() {
        assertProblem(valid.copy(tasks = valid.tasks.map { it.copy(title = "  ") }), "title is empty")
        assertProblem(valid.copy(taskLists = valid.taskLists.map { it.copy(name = "") }), "list name is empty")
        assertProblem(valid.copy(routines = valid.routines.map { it.copy(name = "") }), "routine name is empty")
    }

    @Test
    fun `unknown enum values are rejected`() {
        val badRule = valid.tasks.map { t -> t.recurrence?.let { t.copy(recurrence = it.copy(frequency = "HOURLY")) } ?: t }
        assertProblem(valid.copy(tasks = badRule), "HOURLY")
        val badDay = valid.routines.map { it.copy(weekdays = listOf("MONDAY", "FUNDAY")) }
        assertProblem(valid.copy(routines = badDay), "FUNDAY")
        assertProblem(valid.copy(templates = valid.templates.map { it.copy(kind = "recipes") }), "recipes")
        assertProblem(valid.copy(occurrenceLog = valid.occurrenceLog.map { it.copy(action = "deleted") }), "deleted")
        assertProblem(valid.copy(settings = BackupSettings(themeMode = "SEPIA")), "SEPIA")
        assertProblem(valid.copy(settings = BackupSettings(accent = "PINK")), "PINK")
    }

    @Test
    fun `malformed dates and times are rejected`() {
        assertProblem(valid.copy(tasks = valid.tasks.map { it.copy(dueDate = "2026-02-30") }), "2026-02-30")
        assertProblem(valid.copy(tasks = valid.tasks.map { it.copy(dueTime = "9.30") }), "9.30")
        assertProblem(valid.copy(routines = valid.routines.map { it.copy(time = "25:00") }), "25:00")
        assertProblem(valid.copy(routineChecks = valid.routineChecks.map { it.copy(date = "yesterday") }), "yesterday")
    }

    @Test
    fun `a time without a date is rejected`() {
        assertProblem(valid.copy(tasks = valid.tasks.map { it.copy(dueDate = null, dueTime = "10:00") }), "no due date")
    }

    @Test
    fun `out of range numbers are rejected`() {
        assertProblem(valid.copy(tasks = valid.tasks.map { it.copy(priority = 7) }), "priority 7")
        val zeroInterval = valid.tasks.map { t -> t.recurrence?.let { t.copy(recurrence = it.copy(interval = 0)) } ?: t }
        assertProblem(valid.copy(tasks = zeroInterval), "interval 0")
        assertProblem(valid.copy(reminders = valid.reminders.map { it.copy(offsetMinutes = -5) }), "offset -5")
        assertProblem(valid.copy(focusSessions = valid.focusSessions.map { it.copy(plannedMinutes = 0) }), "planned minutes 0")
        assertProblem(valid.copy(shoppingItems = valid.shoppingItems.map { it.copy(priceCents = -1) }), "price is negative")
        assertProblem(valid.copy(settings = BackupSettings(focusMinutes = 999)), "focus length 999")
        assertProblem(valid.copy(settings = BackupSettings(trashRetentionDays = 0)), "trash retention 0")
    }

    @Test
    fun `duplicate and non-positive ids are rejected`() {
        assertProblem(valid.copy(tasks = valid.tasks + valid.tasks.first()), "tasks: some ids are used twice")
        assertProblem(valid.copy(taskLists = valid.taskLists.map { it.copy(id = 0) }), "ids must be positive")
        assertProblem(valid.copy(itemMemory = valid.itemMemory + valid.itemMemory), "listed twice")
        assertProblem(valid.copy(routineChecks = valid.routineChecks + valid.routineChecks), "ticked twice")
    }

    @Test
    fun `every reference must point into the file`() {
        assertProblem(valid.copy(tasks = valid.tasks.map { it.copy(listId = 404) }), "list 404")
        assertProblem(valid.copy(reminders = valid.reminders.map { it.copy(taskId = 404) }), "task 404")
        assertProblem(valid.copy(occurrenceLog = valid.occurrenceLog.map { it.copy(taskId = 404) }), "task 404")
        assertProblem(valid.copy(shoppingItems = valid.shoppingItems.map { it.copy(listId = 404) }), "shopping list 404")
        assertProblem(valid.copy(routineSteps = valid.routineSteps.map { it.copy(routineId = 404) }), "routine 404")
        assertProblem(valid.copy(routineChecks = valid.routineChecks.map { it.copy(stepId = 404) }), "step 404")
        assertProblem(valid.copy(focusSessions = valid.focusSessions.map { it.copy(taskId = 404) }), "task 404")
    }

    @Test
    fun `a check must belong to the routine of its step`() {
        val other = BackupRoutine(id = 71, name = "Evening", weekdays = listOf("MONDAY"), createdAt = 0)
        val file = valid.copy(
            routines = valid.routines + other,
            routineChecks = valid.routineChecks.map { it.copy(routineId = 71) },
        )
        assertProblem(file, "does not belong to routine 71")
    }

    @Test
    fun `the number of reported problems is capped`() {
        val many = (1..100L).map { BackupSubtask(id = 1000 + it, taskId = 9999, title = "x") }
        assertEquals(BackupValidator.MAX_PROBLEMS, problems(valid.copy(subtasks = many)).size)
    }
}
