package com.riyadmondol2006.taskfern.data.repo

import com.riyadmondol2006.taskfern.domain.LogicalDay
import androidx.room.withTransaction
import com.riyadmondol2006.taskfern.data.db.OccurrenceAction
import com.riyadmondol2006.taskfern.data.db.OccurrenceLogEntity
import com.riyadmondol2006.taskfern.data.db.ReminderEntity
import com.riyadmondol2006.taskfern.data.db.SubtaskEntity
import com.riyadmondol2006.taskfern.data.db.TaskEntity
import com.riyadmondol2006.taskfern.data.db.TaskListEntity
import com.riyadmondol2006.taskfern.data.db.TaskRow
import com.riyadmondol2006.taskfern.data.db.TaskWithDetails
import com.riyadmondol2006.taskfern.data.db.TaskfernDatabase
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceEngine
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceRule
import com.riyadmondol2006.taskfern.domain.OccurrenceRecord
import com.riyadmondol2006.taskfern.domain.Streak
import com.riyadmondol2006.taskfern.domain.TaskStreaks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/** What the editor or Quick Add hands over to create or update a task. */
data class TaskDraft(
    val title: String,
    val notes: String = "",
    val listId: Long? = null,
    val priority: Int = 0,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val recurrence: RecurrenceRule? = null,
    /** Minutes before the due moment; empty = no reminder. */
    val reminderOffsets: List<Int> = emptyList(),
    val subtasks: List<String> = emptyList(),
)

/** Everything needed to put a task back exactly as it was before it was completed. */
data class CompletionUndo(
    val before: TaskEntity,
    val snapshotId: Long?,
    val logId: Long?,
    val subtasksBefore: List<SubtaskEntity>,
)

/**
 * The one place tasks are written. Every multi-row change is a single transaction; [DataEvents] fire afterwards so
 * alarms and widgets follow the committed data.
 */
class TaskRepository(
    private val db: TaskfernDatabase,
    private val events: DataEvents = DataEvents.None,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val dao = db.taskDao()

    private fun now(): Long = clock.millis()
    private fun today(): LocalDate = LogicalDay.today(clock)

    // ---- Observing ---------------------------------------------------------------------------------------------------

    fun observeOpenRows(): Flow<List<TaskRow>> = dao.observeOpenRows()
    fun observeDueOnOrBefore(day: LocalDate) = dao.observeDueOnOrBefore(day)
    fun observeDueBetween(from: LocalDate, to: LocalDate) = dao.observeDueBetween(from, to)
    fun observeCompletedSince(since: Long) = dao.observeCompletedSince(since)
    fun observeCompleted(limit: Int = 500) = dao.observeCompleted(limit)
    fun observeTrash() = dao.observeTrash()
    fun observeArchived() = dao.observeArchived()
    fun observeRow(id: Long) = dao.observeRow(id)
    fun observeDetails(id: Long): Flow<TaskWithDetails?> = dao.observeDetails(id)
    fun observeDueCounts(from: LocalDate, to: LocalDate) = dao.observeDueCounts(from, to)
    fun observeOpenRecurringDueBefore(to: LocalDate) = dao.observeOpenRecurringDueBefore(to)
    fun observeLists() = dao.observeLists()
    fun observeListsWithCounts() = dao.observeListsWithCounts()
    fun observeLog(taskId: Long) = dao.observeLog(taskId)
    fun observeRecentTitles(limit: Int = 30) = dao.observeRecentTitles(limit)
    fun observeCompletionTimesSince(since: Long) = dao.observeCompletionTimesSince(since)
    fun observeOverdueCount(today: LocalDate) = dao.observeOverdueCount(today)
    fun observeOpenCount() = dao.observeOpenCount()

    /** Current and best streak of every open repeating task that has a history, keyed by task id. */
    fun observeTaskStreaks(): Flow<Map<Long, Streak>> = observeHabits().map { list -> list.associate { it.first.id to it.second } }

    /** Open repeating tasks with a history, paired with their streak. */
    fun observeHabits(): Flow<List<Pair<TaskEntity, Streak>>> =
        combine(dao.observeOpenRecurring(), dao.observeOpenSeriesLogs()) { series, logs ->
            val today = today()
            val byTask = logs.groupBy { it.taskId }
            series.mapNotNull { t ->
                val rule = t.recurrence ?: return@mapNotNull null
                val log = byTask[t.id] ?: return@mapNotNull null
                val streak = TaskStreaks.compute(
                    rule, t.seriesAnchor ?: t.dueDate ?: today,
                    log.map { OccurrenceRecord(it.occurrenceDate, it.action) }, t.dueDate, today,
                )
                (t to streak).takeIf { streak.best > 0 }
            }
        }

    fun observeStreak(taskId: Long): Flow<Streak> =
        combine(dao.observeDetails(taskId), dao.observeFullLog(taskId)) { details, log ->
            val t = details?.task
            val rule = t?.recurrence
            if (t == null || rule == null) Streak.NONE
            else TaskStreaks.compute(
                rule, t.seriesAnchor ?: t.dueDate ?: today(),
                log.map { OccurrenceRecord(it.occurrenceDate, it.action) }, t.dueDate, today(),
            )
        }

    /** The series' log, oldest first (for the history dots in the editor). */
    fun observeFullLog(taskId: Long) = dao.observeFullLog(taskId)

    fun observeTags(): Flow<List<String>> = dao.observeTagColumns().map { columns ->
        columns.flatMap(TaskEntity::tagsFromColumn).distinct().sorted()
    }

    fun search(query: String): Flow<List<TaskRow>> {
        val escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return dao.search(escaped)
    }

    suspend fun get(id: Long): TaskEntity? = dao.get(id)
    suspend fun getDetails(id: Long): TaskWithDetails? = dao.getDetails(id)
    suspend fun getList(id: Long): TaskListEntity? = dao.getList(id)
    suspend fun getLists(): List<TaskListEntity> = dao.getLists()

    // ---- Creating & editing ------------------------------------------------------------------------------------------

    suspend fun create(draft: TaskDraft): Long {
        val title = draft.title.trim()
        require(title.isNotEmpty()) { "A task needs a title" }
        val id = db.withTransaction {
            val time = now()
            val due = draft.dueDate ?: if (draft.recurrence != null) today() else null
            val id = dao.insert(
                TaskEntity(
                    listId = draft.listId?.takeIf { dao.getList(it) != null },
                    title = title,
                    notes = draft.notes.trim(),
                    priority = draft.priority.coerceIn(0, 3),
                    dueDate = due,
                    dueTime = if (due == null) null else draft.dueTime,
                    tags = TaskEntity.tagsToColumn(draft.tags),
                    pinned = draft.pinned,
                    recurrence = draft.recurrence,
                    seriesAnchor = if (draft.recurrence != null) due else null,
                    createdAt = time,
                    updatedAt = time,
                    position = dao.topPosition(),
                ),
            )
            if (due != null && draft.reminderOffsets.isNotEmpty()) {
                dao.insertReminders(draft.reminderOffsets.distinct().map { ReminderEntity(taskId = id, offsetMinutes = it) })
            }
            val steps = draft.subtasks.map { it.trim() }.filter { it.isNotEmpty() }
            if (steps.isNotEmpty()) {
                dao.insertSubtasks(steps.mapIndexed { i, s -> SubtaskEntity(taskId = id, title = s, position = i) })
            }
            id
        }
        events.tasksChanged(listOf(id))
        return id
    }

    /**
     * Saves the editor's fields onto an existing task. Changing the due date or the repeat rule restarts the series
     * from the new due date, so "monthly on the 31st" becomes "monthly on the 15th" when the user moves it.
     */
    suspend fun update(id: Long, draft: TaskDraft) {
        val title = draft.title.trim()
        require(title.isNotEmpty()) { "A task needs a title" }
        db.withTransaction {
            val old = dao.get(id) ?: return@withTransaction
            val due = draft.dueDate ?: if (draft.recurrence != null) (old.dueDate ?: today()) else null
            val scheduleChanged = due != old.dueDate || draft.recurrence?.copy(paused = false) != old.recurrence?.copy(paused = false)
            dao.update(
                old.copy(
                    title = title,
                    notes = draft.notes.trim(),
                    listId = draft.listId?.takeIf { dao.getList(it) != null },
                    priority = draft.priority.coerceIn(0, 3),
                    dueDate = due,
                    dueTime = if (due == null) null else draft.dueTime,
                    tags = TaskEntity.tagsToColumn(draft.tags),
                    pinned = draft.pinned,
                    recurrence = draft.recurrence,
                    seriesAnchor = when {
                        draft.recurrence == null -> null
                        scheduleChanged || old.seriesAnchor == null -> due
                        else -> old.seriesAnchor
                    },
                    snoozeUntil = if (scheduleChanged) null else old.snoozeUntil,
                    updatedAt = now(),
                ),
            )
            dao.clearReminders(id)
            if (due != null && draft.reminderOffsets.isNotEmpty()) {
                dao.insertReminders(draft.reminderOffsets.distinct().map { ReminderEntity(taskId = id, offsetMinutes = it) })
            }
        }
        events.tasksChanged(listOf(id))
    }

    suspend fun rename(id: Long, title: String) = edit(listOf(id)) { it.copy(title = title.trim().ifEmpty { it.title }) }

    suspend fun setPriority(ids: Collection<Long>, priority: Int) = edit(ids) { it.copy(priority = priority.coerceIn(0, 3)) }

    suspend fun setPinned(ids: Collection<Long>, pinned: Boolean) = edit(ids) { it.copy(pinned = pinned) }

    suspend fun moveToList(ids: Collection<Long>, listId: Long?) {
        val target = listId?.takeIf { dao.getList(it) != null }
        edit(ids) { it.copy(listId = target) }
    }

    /** Reschedules; recurring tasks restart their series from the new date. Passing null clears the date. */
    suspend fun setDueDate(ids: Collection<Long>, date: LocalDate?, time: LocalTime? = null, keepTime: Boolean = true) {
        edit(ids) { t ->
            if (t.recurrence != null && date == null) return@edit t // a series needs a date
            t.copy(
                dueDate = date,
                dueTime = when {
                    date == null -> null
                    time != null -> time
                    keepTime -> t.dueTime
                    else -> null
                },
                seriesAnchor = if (t.recurrence != null) date else null,
                snoozeUntil = null,
            )
        }
        if (date == null) db.withTransaction { ids.forEach { dao.clearReminders(it) } }
    }

    suspend fun addTags(ids: Collection<Long>, tags: Collection<String>) =
        edit(ids) { it.copy(tags = TaskEntity.tagsToColumn(it.tagList + tags)) }

    suspend fun setArchived(ids: Collection<Long>, archived: Boolean) = edit(ids) { it.copy(archived = archived) }

    private suspend fun edit(ids: Collection<Long>, change: (TaskEntity) -> TaskEntity) {
        if (ids.isEmpty()) return
        db.withTransaction {
            val time = now()
            dao.updateAll(dao.getMany(ids).map { change(it).copy(updatedAt = time) })
        }
        events.tasksChanged(ids)
    }

    // ---- Completing --------------------------------------------------------------------------------------------------

    /**
     * Completes a task (or the current occurrence of a recurring one) and returns what is needed to undo it.
     *
     * [expectedDue] guards against double taps from widgets and notifications: when given, nothing happens unless the
     * task is still open and still due on that date, so a second tap can never complete the following occurrence.
     */
    suspend fun complete(id: Long, expectedDue: LocalDate? = null, checkExpected: Boolean = false): CompletionUndo? {
        val undo = db.withTransaction {
            val task = dao.get(id) ?: return@withTransaction null
            if (task.isCompleted || task.deletedAt != null) return@withTransaction null
            if (checkExpected && task.dueDate != expectedDue) return@withTransaction null
            val time = now()
            val today = today()
            val subtasksBefore = dao.subtasks(id)
            val rule = task.recurrence
            if (rule == null) {
                dao.update(task.copy(isCompleted = true, completedAt = time, updatedAt = time, snoozeUntil = null, pinned = false))
                return@withTransaction CompletionUndo(task, null, null, subtasksBefore)
            }

            val due = task.dueDate ?: today
            val next = RecurrenceEngine.nextDueDate(
                rule = rule,
                anchor = task.seriesAnchor ?: due,
                currentDue = due,
                completedOn = today,
                today = today,
                completedCount = task.occurrenceCount,
            )
            val logId = dao.insertLog(OccurrenceLogEntity(taskId = id, occurrenceDate = due, action = OccurrenceAction.COMPLETED, at = time))
            if (next == null) {
                // Last occurrence: the series itself becomes the completed task.
                dao.update(
                    task.copy(
                        isCompleted = true, completedAt = time, updatedAt = time, snoozeUntil = null,
                        occurrenceCount = task.occurrenceCount + 1, pinned = false,
                    ),
                )
                CompletionUndo(task, null, logId, subtasksBefore)
            } else {
                val snapshotId = dao.insert(
                    task.copy(
                        id = 0, recurrence = null, seriesAnchor = null, occurrenceCount = 0, isCompleted = true,
                        completedAt = time, createdAt = time, updatedAt = time, sourceTaskId = id, snoozeUntil = null,
                        nextReminderAt = null, pinned = false, dueDate = due,
                    ),
                )
                if (subtasksBefore.isNotEmpty()) {
                    dao.insertSubtasks(subtasksBefore.map { it.copy(id = 0, taskId = snapshotId) })
                    dao.resetSubtasks(id)
                }
                dao.update(
                    task.copy(
                        dueDate = next, occurrenceCount = task.occurrenceCount + 1, snoozeUntil = null, updatedAt = time,
                    ),
                )
                CompletionUndo(task, snapshotId, logId, subtasksBefore)
            }
        }
        if (undo != null) events.tasksChanged(listOfNotNull(id, undo.snapshotId))
        return undo
    }

    suspend fun undoCompletion(undo: CompletionUndo) {
        db.withTransaction {
            val current = dao.get(undo.before.id) ?: return@withTransaction
            undo.snapshotId?.let { dao.hardDelete(it) }
            undo.logId?.let { dao.deleteLog(it) }
            dao.update(undo.before.copy(updatedAt = now(), nextReminderAt = current.nextReminderAt))
            undo.subtasksBefore.forEach { dao.updateSubtask(it) }
        }
        events.tasksChanged(listOfNotNull(undo.before.id, undo.snapshotId))
    }

    /** Re-opens a completed task. A completed occurrence of a series becomes an ordinary one-off task. */
    suspend fun reopen(id: Long) = edit(listOf(id)) { it.copy(isCompleted = false, completedAt = null) }

    suspend fun completeMany(ids: Collection<Long>): List<CompletionUndo> = ids.mapNotNull { complete(it) }

    /** Moves a recurring task to its next occurrence without completing it. */
    suspend fun skipOccurrence(id: Long): CompletionUndo? {
        val undo = db.withTransaction {
            val task = dao.get(id) ?: return@withTransaction null
            val rule = task.recurrence ?: return@withTransaction null
            if (task.isCompleted) return@withTransaction null
            val time = now()
            val due = task.dueDate ?: today()
            val next = RecurrenceEngine.nextDueDate(
                rule, task.seriesAnchor ?: due, due, today(), today(), task.occurrenceCount, countThisOccurrence = false,
            ) ?: return@withTransaction null
            val logId = dao.insertLog(OccurrenceLogEntity(taskId = id, occurrenceDate = due, action = OccurrenceAction.SKIPPED, at = time))
            dao.update(task.copy(dueDate = next, snoozeUntil = null, updatedAt = time))
            CompletionUndo(task, null, logId, emptyList())
        }
        if (undo != null) events.tasksChanged(listOf(id))
        return undo
    }

    /** Stops repeating: the current occurrence stays as a normal task. */
    suspend fun endSeries(id: Long) = edit(listOf(id)) { it.copy(recurrence = null, seriesAnchor = null) }

    suspend fun setSeriesPaused(id: Long, paused: Boolean) = edit(listOf(id)) { t ->
        val rule = t.recurrence ?: return@edit t
        if (paused) {
            t.copy(recurrence = rule.copy(paused = true), snoozeUntil = null)
        } else {
            val resumed = rule.copy(paused = false)
            val anchor = t.seriesAnchor ?: t.dueDate ?: today()
            t.copy(recurrence = resumed, dueDate = RecurrenceEngine.realign(resumed, anchor, t.dueDate, today()) ?: t.dueDate)
        }
    }

    suspend fun snooze(id: Long, untilMillis: Long) = edit(listOf(id)) { it.copy(snoozeUntil = untilMillis) }

    // ---- Deleting ----------------------------------------------------------------------------------------------------

    suspend fun moveToTrash(ids: Collection<Long>) = edit(ids) { it.copy(deletedAt = now(), snoozeUntil = null) }

    suspend fun restoreFromTrash(ids: Collection<Long>) = edit(ids) { it.copy(deletedAt = null) }

    suspend fun deleteForever(ids: Collection<Long>) {
        db.withTransaction { ids.forEach { dao.hardDelete(it) } }
        events.tasksChanged(ids)
    }

    suspend fun emptyTrash(): Int = dao.emptyTrash().also { events.tasksChanged() }

    suspend fun purgeTrashOlderThan(days: Int): Int {
        val before = now() - days * 24L * 60 * 60 * 1000
        return dao.purgeTrash(before).also { if (it > 0) events.tasksChanged() }
    }

    suspend fun duplicate(id: Long): Long? {
        val newId = db.withTransaction {
            val details = dao.getDetails(id) ?: return@withTransaction null
            val time = now()
            val copyId = dao.insert(
                details.task.copy(
                    id = 0, title = details.task.title, isCompleted = false, completedAt = null, createdAt = time,
                    updatedAt = time, deletedAt = null, archived = false, sourceTaskId = null, occurrenceCount = 0,
                    snoozeUntil = null, nextReminderAt = null, position = dao.topPosition(),
                ),
            )
            dao.insertSubtasks(details.subtasks.map { it.copy(id = 0, taskId = copyId, done = false) })
            dao.insertReminders(details.reminders.map { it.copy(id = 0, taskId = copyId) })
            copyId
        }
        if (newId != null) events.tasksChanged(listOf(newId))
        return newId
    }

    /** Persists a manual order: [orderedIds] top to bottom. */
    suspend fun reorder(orderedIds: List<Long>) {
        db.withTransaction { orderedIds.forEachIndexed { i, id -> dao.setPosition(id, i.toLong()) } }
        events.tasksChanged(orderedIds)
    }

    // ---- Subtasks ----------------------------------------------------------------------------------------------------

    suspend fun addSubtask(taskId: Long, title: String) {
        val clean = title.trim().takeIf { it.isNotEmpty() } ?: return
        val added = db.withTransaction {
            // The task may have been deleted meanwhile (another screen, a widget); adding to it would break the database.
            if (dao.get(taskId) == null) return@withTransaction false
            dao.insertSubtask(SubtaskEntity(taskId = taskId, title = clean, position = dao.nextSubtaskPosition(taskId)))
            true
        }
        if (added) events.tasksChanged(listOf(taskId))
    }

    suspend fun updateSubtask(subtask: SubtaskEntity) {
        dao.updateSubtask(subtask)
        events.tasksChanged(listOf(subtask.taskId))
    }

    suspend fun deleteSubtask(subtask: SubtaskEntity) {
        dao.deleteSubtask(subtask.id)
        events.tasksChanged(listOf(subtask.taskId))
    }

    suspend fun reorderSubtasks(subtasks: List<SubtaskEntity>) {
        db.withTransaction { subtasks.forEachIndexed { i, s -> dao.updateSubtask(s.copy(position = i)) } }
    }

    // ---- Lists -------------------------------------------------------------------------------------------------------

    suspend fun createList(name: String, color: Int = 0, icon: String = "list"): Long {
        val id = dao.insertList(
            TaskListEntity(name = name.trim().ifEmpty { "List" }, color = color, icon = icon, position = dao.nextListPosition(), createdAt = now()),
        )
        return id
    }

    suspend fun updateList(list: TaskListEntity) {
        dao.updateList(list.copy(name = list.name.trim().ifEmpty { "List" }))
        events.tasksChanged()
    }

    /** Deletes a list. Its tasks go to the trash (restorable) unless [keepTasks], in which case they move to the Inbox. */
    suspend fun deleteList(id: Long, keepTasks: Boolean) {
        db.withTransaction {
            if (!keepTasks) {
                val time = now()
                val ids = dao.idsInList(id)
                dao.updateAll(dao.getMany(ids).map { it.copy(deletedAt = time, listId = null, updatedAt = time) })
            }
            dao.deleteList(id) // ON DELETE SET NULL moves anything left to the Inbox
        }
        events.tasksChanged()
    }

    suspend fun reorderLists(ordered: List<TaskListEntity>) {
        db.withTransaction { ordered.forEachIndexed { i, l -> dao.updateList(l.copy(position = i)) } }
    }

    // ---- Used by the reminder scheduler -------------------------------------------------------------------------------

    suspend fun tasksNeedingAlarms() = dao.tasksNeedingAlarms()
    suspend fun idsWithScheduledAlarm() = dao.idsWithScheduledAlarm()
    suspend fun reminderOffsets(taskId: Long): List<Int> = dao.reminders(taskId).map { it.offsetMinutes }
    suspend fun setNextReminderAt(id: Long, at: Long?) = dao.setNextReminderAt(id, at)
    suspend fun clearSnooze(id: Long) {
        dao.get(id)?.let { dao.update(it.copy(snoozeUntil = null)) }
    }
}
