package com.riyadmondol2006.taskfern.backup

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.riyadmondol2006.taskfern.data.db.TaskfernDatabase

/**
 * Reads and writes every backed-up table in single transactions. Widget settings (`widget_configs`) are never touched.
 * Holds no state; safe to create per use.
 */
class BackupStore(private val db: TaskfernDatabase) {
    private val tasks = db.taskDao()
    private val shopping = db.shoppingDao()
    private val routines = db.routineDao()
    private val templates = db.templateDao()
    private val focus = db.focusDao()

    /** A consistent copy of all backed-up rows. */
    suspend fun snapshot(): DatabaseSnapshot = db.withTransaction { read() }

    /** Deletes all backed-up data and inserts [snapshot] with its original ids, atomically. */
    suspend fun replace(snapshot: DatabaseSnapshot) {
        db.withTransaction {
            // Children first, although cascades would cover most of it.
            routines.clearAllChecks()
            routines.clearSteps()
            routines.clearRoutines()
            shopping.clearItems()
            shopping.clearLists()
            shopping.clearMemory()
            focus.clearAll()
            tasks.clearOccurrenceLog()
            tasks.clearAllReminders()
            tasks.clearSubtasks()
            tasks.clearTasks()
            tasks.clearLists()
            templates.clearAll()
            insert(snapshot)
        }
    }

    /** Merges [incoming] into the existing data (see [MergePlanner] for the duplicate rules), atomically. */
    suspend fun merge(incoming: DatabaseSnapshot): MergePlan = db.withTransaction {
        val plan = MergePlanner.plan(read(), incoming, idFloors())
        insert(plan.insert)
        plan
    }

    private suspend fun read() = DatabaseSnapshot(
        taskLists = tasks.getLists(),
        tasks = tasks.exportTasks(),
        subtasks = tasks.exportSubtasks(),
        reminders = tasks.exportReminders(),
        occurrenceLog = tasks.exportOccurrenceLog(),
        shoppingLists = shopping.getLists(),
        shoppingItems = shopping.allItems(),
        itemMemory = shopping.allMemory(),
        routines = routines.getRoutines(),
        routineSteps = routines.allSteps(),
        routineChecks = routines.allChecks(),
        templates = templates.getAll(),
        focusSessions = focus.all(),
    )

    private suspend fun insert(s: DatabaseSnapshot) {
        if (s.taskLists.isNotEmpty()) tasks.importLists(s.taskLists)
        if (s.tasks.isNotEmpty()) tasks.importTasks(s.tasks.map { it.copy(nextReminderAt = null) })
        if (s.subtasks.isNotEmpty()) tasks.insertSubtasks(s.subtasks)
        if (s.reminders.isNotEmpty()) tasks.insertReminders(s.reminders)
        if (s.occurrenceLog.isNotEmpty()) tasks.importOccurrenceLog(s.occurrenceLog)
        if (s.shoppingLists.isNotEmpty()) shopping.importLists(s.shoppingLists)
        if (s.shoppingItems.isNotEmpty()) shopping.insertItems(s.shoppingItems)
        if (s.itemMemory.isNotEmpty()) shopping.importMemory(s.itemMemory)
        if (s.routines.isNotEmpty()) routines.importRoutines(s.routines.map { it.copy(nextReminderAt = null) })
        if (s.routineSteps.isNotEmpty()) routines.insertSteps(s.routineSteps)
        if (s.routineChecks.isNotEmpty()) routines.importChecks(s.routineChecks)
        if (s.templates.isNotEmpty()) templates.insertAll(s.templates)
        if (s.focusSessions.isNotEmpty()) focus.importAll(s.focusSessions)
    }

    /** The highest id each table has ever handed out, so merged rows never reuse the id of a deleted row. */
    private fun idFloors(): MergePlanner.IdFloors {
        val seq = HashMap<String, Long>()
        runCatching {
            db.query(SimpleSQLiteQuery("SELECT name, seq FROM sqlite_sequence")).use { c ->
                while (c.moveToNext()) seq[c.getString(0)] = c.getLong(1)
            }
        }
        fun of(table: String) = seq[table] ?: 0L
        return MergePlanner.IdFloors(
            taskLists = of("task_lists"), tasks = of("tasks"), subtasks = of("subtasks"), reminders = of("reminders"),
            occurrenceLog = of("occurrence_log"), shoppingLists = of("shopping_lists"),
            shoppingItems = of("shopping_items"), routines = of("routines"), routineSteps = of("routine_steps"),
            templates = of("templates"), focusSessions = of("focus_sessions"),
        )
    }
}
