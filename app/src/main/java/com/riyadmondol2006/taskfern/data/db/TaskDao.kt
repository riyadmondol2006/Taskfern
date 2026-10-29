package com.riyadmondol2006.taskfern.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** A task with the little extra the list rows need, read in one query. */
data class TaskRow(
    @Embedded val task: TaskEntity,
    @ColumnInfo(name = "list_name") val listName: String?,
    @ColumnInfo(name = "list_color") val listColor: Int?,
    @ColumnInfo(name = "subtask_total") val subtaskTotal: Int,
    @ColumnInfo(name = "subtask_done") val subtaskDone: Int,
    @ColumnInfo(name = "reminder_count") val reminderCount: Int,
)

data class TaskWithDetails(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "task_id") val subtasks: List<SubtaskEntity>,
    @Relation(parentColumn = "id", entityColumn = "task_id") val reminders: List<ReminderEntity>,
)

data class TaskListWithCount(
    @Embedded val list: TaskListEntity,
    @ColumnInfo(name = "open_count") val openCount: Int,
)

data class DayCount(
    @ColumnInfo(name = "due_date") val date: LocalDate,
    val count: Int,
)

private const val ROW_SELECT = """
    SELECT t.*, l.name AS list_name, l.color AS list_color,
        (SELECT COUNT(*) FROM subtasks s WHERE s.task_id = t.id) AS subtask_total,
        (SELECT COUNT(*) FROM subtasks s WHERE s.task_id = t.id AND s.done = 1) AS subtask_done,
        (SELECT COUNT(*) FROM reminders r WHERE r.task_id = t.id) AS reminder_count
    FROM tasks t LEFT JOIN task_lists l ON l.id = t.list_id
"""

private const val LIVE = "t.deleted_at IS NULL AND t.archived = 0"
private const val OPEN = "$LIVE AND t.is_completed = 0"
/** Paused recurring tasks stay in their list but are left out of date-driven views. */
private const val NOT_PAUSED = "(t.rec_paused IS NULL OR t.rec_paused = 0)"
/** Week/month/year plans are not day tasks; they join day lists only once their period has ended (overdue). */
private const val DAY_TASK = "t.due_scope = 0"

@Dao
interface TaskDao {

    // ---- Reads for screens -------------------------------------------------------------------------------------------

    @Query("$ROW_SELECT WHERE $OPEN ORDER BY t.pinned DESC, t.due_date IS NULL, t.due_date, t.due_time IS NULL, t.due_time, t.priority DESC, t.position, t.id")
    fun observeOpenRows(): Flow<List<TaskRow>>

    @Query("$ROW_SELECT WHERE $OPEN AND $NOT_PAUSED AND t.due_date IS NOT NULL AND t.due_date <= :day AND ($DAY_TASK OR t.due_date < :day) ORDER BY t.due_date, t.due_time IS NULL, t.due_time, t.priority DESC, t.position, t.id")
    fun observeDueOnOrBefore(day: LocalDate): Flow<List<TaskRow>>

    @Query("$ROW_SELECT WHERE $OPEN AND $NOT_PAUSED AND $DAY_TASK AND t.due_date BETWEEN :from AND :to ORDER BY t.due_date, t.due_time IS NULL, t.due_time, t.priority DESC, t.position, t.id")
    fun observeDueBetween(from: LocalDate, to: LocalDate): Flow<List<TaskRow>>

    @Query("$ROW_SELECT WHERE $LIVE AND t.is_completed = 1 AND t.completed_at >= :since ORDER BY t.completed_at DESC")
    fun observeCompletedSince(since: Long): Flow<List<TaskRow>>

    @Query("$ROW_SELECT WHERE $LIVE AND t.is_completed = 1 ORDER BY t.completed_at DESC LIMIT :limit")
    fun observeCompleted(limit: Int): Flow<List<TaskRow>>

    @Query("$ROW_SELECT WHERE t.deleted_at IS NOT NULL ORDER BY t.deleted_at DESC")
    fun observeTrash(): Flow<List<TaskRow>>

    @Query("$ROW_SELECT WHERE t.deleted_at IS NULL AND t.archived = 1 ORDER BY t.updated_at DESC")
    fun observeArchived(): Flow<List<TaskRow>>

    @Query(
        """$ROW_SELECT WHERE $LIVE AND (t.title LIKE '%' || :query || '%' ESCAPE '\' OR t.notes LIKE '%' || :query || '%' ESCAPE '\'
        OR t.tags LIKE '%,' || :query || '%' ESCAPE '\'
        OR t.id IN (SELECT s.task_id FROM subtasks s WHERE s.title LIKE '%' || :query || '%' ESCAPE '\'))
        ORDER BY t.is_completed, t.due_date IS NULL, t.due_date, t.updated_at DESC LIMIT 200""",
    )
    fun search(query: String): Flow<List<TaskRow>>

    @Query("$ROW_SELECT WHERE t.id = :id")
    fun observeRow(id: Long): Flow<TaskRow?>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeDetails(id: Long): Flow<TaskWithDetails?>

    @Query("SELECT due_date, COUNT(*) AS count FROM tasks t WHERE $OPEN AND $NOT_PAUSED AND $DAY_TASK AND t.due_date BETWEEN :from AND :to GROUP BY t.due_date")
    fun observeDueCounts(from: LocalDate, to: LocalDate): Flow<List<DayCount>>

    @Query("SELECT * FROM tasks t WHERE $OPEN AND t.rec_frequency IS NOT NULL AND $NOT_PAUSED AND t.due_date IS NOT NULL AND t.due_date <= :to")
    fun observeOpenRecurringDueBefore(to: LocalDate): Flow<List<TaskEntity>>

    /** Open plans whose period has not ended yet, soonest first. */
    @Query("$ROW_SELECT WHERE $OPEN AND t.due_scope != 0 AND t.due_date >= :today ORDER BY t.due_date, t.due_scope, t.priority DESC, t.position, t.id")
    fun observePlans(today: LocalDate): Flow<List<TaskRow>>

    @Query("SELECT DISTINCT tags FROM tasks WHERE tags != '' AND deleted_at IS NULL")
    fun observeTagColumns(): Flow<List<String>>

    @Query("SELECT title FROM tasks WHERE deleted_at IS NULL GROUP BY lower(title) ORDER BY MAX(created_at) DESC LIMIT :limit")
    fun observeRecentTitles(limit: Int): Flow<List<String>>

    // ---- Statistics --------------------------------------------------------------------------------------------------

    @Query("SELECT completed_at FROM tasks WHERE deleted_at IS NULL AND is_completed = 1 AND completed_at >= :since")
    fun observeCompletionTimesSince(since: Long): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM tasks t WHERE $OPEN AND $NOT_PAUSED AND t.due_date < :today")
    fun observeOverdueCount(today: LocalDate): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks t WHERE $OPEN")
    fun observeOpenCount(): Flow<Int>

    // ---- Lists -------------------------------------------------------------------------------------------------------

    @Query(
        """SELECT l.*, (SELECT COUNT(*) FROM tasks t WHERE t.list_id = l.id AND $OPEN) AS open_count
        FROM task_lists l ORDER BY l.favorite DESC, l.position, l.id""",
    )
    fun observeListsWithCounts(): Flow<List<TaskListWithCount>>

    @Query("SELECT * FROM task_lists ORDER BY favorite DESC, position, id")
    fun observeLists(): Flow<List<TaskListEntity>>

    @Query("SELECT * FROM task_lists WHERE id = :id")
    suspend fun getList(id: Long): TaskListEntity?

    @Query("SELECT * FROM task_lists ORDER BY position, id")
    suspend fun getLists(): List<TaskListEntity>

    @Insert
    suspend fun insertList(list: TaskListEntity): Long

    @Update
    suspend fun updateList(list: TaskListEntity)

    @Query("DELETE FROM task_lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Query("SELECT COALESCE(MAX(position), 0) + 1 FROM task_lists")
    suspend fun nextListPosition(): Int

    // ---- Single-row reads/writes -------------------------------------------------------------------------------------

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getDetails(id: Long): TaskWithDetails?

    @Query("SELECT id FROM tasks WHERE list_id = :listId")
    suspend fun idsInList(listId: Long): List<Long>

    @Query("SELECT * FROM tasks WHERE id IN (:ids)")
    suspend fun getMany(ids: Collection<Long>): List<TaskEntity>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Update
    suspend fun updateAll(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Query("DELETE FROM tasks WHERE deleted_at IS NOT NULL AND deleted_at < :before")
    suspend fun purgeTrash(before: Long): Int

    @Query("DELETE FROM tasks WHERE deleted_at IS NOT NULL")
    suspend fun emptyTrash(): Int

    @Query("SELECT COALESCE(MIN(position), 0) - 1 FROM tasks")
    suspend fun topPosition(): Long

    @Query("SELECT COALESCE(MAX(position), 0) + 1 FROM tasks")
    suspend fun bottomPosition(): Long

    @Query("UPDATE tasks SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Long)

    @Query("UPDATE tasks SET next_reminder_at = :at WHERE id = :id")
    suspend fun setNextReminderAt(id: Long, at: Long?)

    /** Tasks that may need an alarm: open, dated, with reminders or a pending snooze. */
    @Query(
        """SELECT * FROM tasks t WHERE $OPEN AND $NOT_PAUSED AND (t.snooze_until IS NOT NULL OR
        (t.due_date IS NOT NULL AND EXISTS (SELECT 1 FROM reminders r WHERE r.task_id = t.id)))""",
    )
    suspend fun tasksNeedingAlarms(): List<TaskEntity>

    @Query("SELECT id FROM tasks WHERE next_reminder_at IS NOT NULL")
    suspend fun idsWithScheduledAlarm(): List<Long>

    // ---- Subtasks & reminders -----------------------------------------------------------------------------------------

    @Query("SELECT * FROM subtasks WHERE task_id = :taskId ORDER BY position, id")
    suspend fun subtasks(taskId: Long): List<SubtaskEntity>

    @Insert
    suspend fun insertSubtask(subtask: SubtaskEntity): Long

    @Insert
    suspend fun insertSubtasks(subtasks: List<SubtaskEntity>)

    @Update
    suspend fun updateSubtask(subtask: SubtaskEntity)

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun deleteSubtask(id: Long)

    @Query("UPDATE subtasks SET done = 0 WHERE task_id = :taskId")
    suspend fun resetSubtasks(taskId: Long)

    @Query("SELECT COALESCE(MAX(position), 0) + 1 FROM subtasks WHERE task_id = :taskId")
    suspend fun nextSubtaskPosition(taskId: Long): Int

    @Query("SELECT * FROM reminders WHERE task_id = :taskId ORDER BY offset_minutes")
    suspend fun reminders(taskId: Long): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReminders(reminders: List<ReminderEntity>)

    @Query("DELETE FROM reminders WHERE task_id = :taskId")
    suspend fun clearReminders(taskId: Long)

    // ---- Occurrence log ----------------------------------------------------------------------------------------------

    @Insert
    suspend fun insertLog(entry: OccurrenceLogEntity): Long

    @Query("DELETE FROM occurrence_log WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Query("SELECT * FROM occurrence_log WHERE task_id = :taskId ORDER BY at DESC LIMIT 60")
    fun observeLog(taskId: Long): Flow<List<OccurrenceLogEntity>>

    // ---- Streaks -----------------------------------------------------------------------------------------------------

    @Query("SELECT * FROM tasks t WHERE $OPEN AND t.rec_frequency IS NOT NULL")
    fun observeOpenRecurring(): Flow<List<TaskEntity>>

    /** History of every open series, oldest first, for streaks. Indexed by task_id; bounded by real use. */
    @Query(
        """SELECT l.* FROM occurrence_log l JOIN tasks t ON t.id = l.task_id
        WHERE t.deleted_at IS NULL AND t.archived = 0 AND t.is_completed = 0 AND t.rec_frequency IS NOT NULL
        ORDER BY l.task_id, l.occurrence_date""",
    )
    fun observeOpenSeriesLogs(): Flow<List<OccurrenceLogEntity>>

    @Query("SELECT * FROM occurrence_log WHERE task_id = :taskId ORDER BY occurrence_date")
    fun observeFullLog(taskId: Long): Flow<List<OccurrenceLogEntity>>

    // ---- Backup: whole-table export, explicit-id import and clearing (used by the backup package) -------------------

    @Query("SELECT * FROM tasks ORDER BY id")
    suspend fun exportTasks(): List<TaskEntity>

    @Query("SELECT * FROM subtasks ORDER BY id")
    suspend fun exportSubtasks(): List<SubtaskEntity>

    @Query("SELECT * FROM reminders ORDER BY id")
    suspend fun exportReminders(): List<ReminderEntity>

    @Query("SELECT * FROM occurrence_log ORDER BY id")
    suspend fun exportOccurrenceLog(): List<OccurrenceLogEntity>

    @Insert
    suspend fun importLists(lists: List<TaskListEntity>)

    @Insert
    suspend fun importTasks(tasks: List<TaskEntity>)

    @Insert
    suspend fun importOccurrenceLog(entries: List<OccurrenceLogEntity>)

    @Query("DELETE FROM occurrence_log")
    suspend fun clearOccurrenceLog()

    @Query("DELETE FROM reminders")
    suspend fun clearAllReminders()

    @Query("DELETE FROM subtasks")
    suspend fun clearSubtasks()

    @Query("DELETE FROM tasks")
    suspend fun clearTasks()

    @Query("DELETE FROM task_lists")
    suspend fun clearLists()
}
