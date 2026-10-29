package com.riyadmondol2006.taskfern.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceRule
import java.time.LocalDate
import java.time.LocalTime

/** A user-made task list. Tasks with no list live in the Inbox. */
@Entity(tableName = "task_lists")
data class TaskListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "color") val color: Int = 0,
    val icon: String = "list",
    val position: Int = 0,
    val favorite: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** What a task's due date means: a single day, or the last day of a week, month or year it is planned for. */
object DueScope {
    const val DAY = 0
    const val WEEK = 1
    const val MONTH = 2
    const val YEAR = 3
}

object Priority {
    const val NONE = 0
    const val LOW = 1
    const val MEDIUM = 2
    const val HIGH = 3
}

/**
 * A task. Recurring tasks are a single row whose due date rolls forward; each completed occurrence is kept as a
 * separate completed, non-recurring row pointing back via [sourceTaskId] so the Completed view and statistics need no
 * special cases.
 *
 * [tags] is stored normalised as ",tag1,tag2," so a tag can be matched with LIKE '%,tag,%'.
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskListEntity::class,
            parentColumns = ["id"],
            childColumns = ["list_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("list_id"),
        Index("due_date"),
        Index("is_completed", "deleted_at", "archived"),
        Index("source_task_id"),
        Index("completed_at"),
    ],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "list_id") val listId: Long? = null,
    val title: String,
    val notes: String = "",
    val priority: Int = Priority.NONE,
    @ColumnInfo(name = "due_date") val dueDate: LocalDate? = null,
    @ColumnInfo(name = "due_time") val dueTime: LocalTime? = null,
    val tags: String = "",
    val pinned: Boolean = false,
    val archived: Boolean = false,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    val position: Long = 0,
    @Embedded(prefix = "rec_") val recurrence: RecurrenceRule? = null,
    @ColumnInfo(name = "series_anchor") val seriesAnchor: LocalDate? = null,
    @ColumnInfo(name = "occurrence_count") val occurrenceCount: Int = 0,
    @ColumnInfo(name = "source_task_id") val sourceTaskId: Long? = null,
    @ColumnInfo(name = "snooze_until") val snoozeUntil: Long? = null,
    @ColumnInfo(name = "next_reminder_at") val nextReminderAt: Long? = null,
    /** [DueScope]: DAY, or a plan for the week/month/year ending on [dueDate]. Added in schema version 2. */
    @ColumnInfo(name = "due_scope", defaultValue = "0") val dueScope: Int = DueScope.DAY,
) {
    val tagList: List<String> get() = tagsFromColumn(tags)
    val isPlan: Boolean get() = dueScope != DueScope.DAY && dueDate != null
    val isRecurring: Boolean get() = recurrence != null

    companion object {
        fun tagsToColumn(tags: Collection<String>): String {
            val clean = tags.map { it.trim().trimStart('#').lowercase() }.filter { it.isNotEmpty() }.distinct()
            return if (clean.isEmpty()) "" else clean.joinToString(",", prefix = ",", postfix = ",")
        }

        fun tagsFromColumn(column: String): List<String> = column.split(',').filter { it.isNotBlank() }
    }
}

/** A checklist step inside a task ("subtask"). */
@Entity(
    tableName = "subtasks",
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["task_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("task_id")],
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: Long,
    val title: String,
    val done: Boolean = false,
    val position: Int = 0,
)

/** A reminder, as minutes before the task's due moment (0 = at the due time). */
@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["task_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["task_id", "offset_minutes"], unique = true)],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: Long,
    @ColumnInfo(name = "offset_minutes") val offsetMinutes: Int,
)

object OccurrenceAction {
    const val COMPLETED = "completed"
    const val SKIPPED = "skipped"
}

/** History of a recurring series: which occurrences were completed or skipped. */
@Entity(
    tableName = "occurrence_log",
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["task_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("task_id"), Index("at")],
)
data class OccurrenceLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: Long,
    @ColumnInfo(name = "occurrence_date") val occurrenceDate: LocalDate,
    val action: String,
    val at: Long,
)

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int = 0,
    val position: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "shopping_items",
    foreignKeys = [ForeignKey(ShoppingListEntity::class, ["id"], ["list_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("list_id", "checked")],
)
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "list_id") val listId: Long,
    val name: String,
    /** Free text so "1.5", "2" and "½" all work; empty when not given. */
    val quantity: String = "",
    val unit: String = "",
    val category: String = "",
    val checked: Boolean = false,
    val position: Int = 0,
    @ColumnInfo(name = "price_cents") val priceCents: Long? = null,
    val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "checked_at") val checkedAt: Long? = null,
)

/** Local memory of items the user has bought, for suggestions and "buy again". Keyed by normalised name. */
@Entity(tableName = "item_memory", indices = [Index("use_count"), Index("last_used_at")])
data class ItemMemoryEntity(
    @PrimaryKey @ColumnInfo(name = "name_key") val nameKey: String,
    val name: String,
    val category: String = "",
    val unit: String = "",
    @ColumnInfo(name = "use_count") val useCount: Int = 0,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long = 0,
    @ColumnInfo(name = "last_price_cents") val lastPriceCents: Long? = null,
)

/** A reusable checklist that is done on some days of the week, e.g. a morning routine. */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "🌿",
    val color: Int = 0,
    /** Bit mask of days it runs on (Mon = bit 0 … Sun = bit 6). */
    val weekdays: Int = 0x7F,
    /** Optional time of day in minutes after midnight; reminds at that time when set. */
    @ColumnInfo(name = "time_minutes") val timeMinutes: Int? = null,
    val paused: Boolean = false,
    val position: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "next_reminder_at") val nextReminderAt: Long? = null,
)

@Entity(
    tableName = "routine_steps",
    foreignKeys = [ForeignKey(RoutineEntity::class, ["id"], ["routine_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("routine_id")],
)
data class RoutineStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "routine_id") val routineId: Long,
    val title: String,
    val position: Int = 0,
)

/** A step ticked off on a given day. Removing the row un-ticks it. */
@Entity(
    tableName = "routine_checks",
    primaryKeys = ["step_id", "date"],
    foreignKeys = [
        ForeignKey(RoutineStepEntity::class, ["id"], ["step_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(RoutineEntity::class, ["id"], ["routine_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("routine_id", "date")],
)
data class RoutineCheckEntity(
    @ColumnInfo(name = "step_id") val stepId: Long,
    @ColumnInfo(name = "routine_id") val routineId: Long,
    val date: LocalDate,
    @ColumnInfo(name = "checked_at") val checkedAt: Long,
)

object TemplateKind {
    const val TASKS = "tasks"
    const val SHOPPING = "shopping"
    const val ROUTINE = "routine"
}

/** An editable template. [items] holds one entry per line. */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val name: String,
    val emoji: String = "",
    val items: String,
    val builtin: Boolean = false,
    val position: Int = 0,
) {
    val itemLines: List<String> get() = items.lines().map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(
    tableName = "focus_sessions",
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["task_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("task_id"), Index("started_at")],
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: Long? = null,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "planned_minutes") val plannedMinutes: Int,
    @ColumnInfo(name = "focused_seconds") val focusedSeconds: Long = 0,
    val completed: Boolean = false,
)

object WidgetKind {
    const val TODAY = "today"
    const val QUICK_ADD = "quick_add"
    const val SHOPPING = "shopping"
    const val AGENDA = "agenda"
    const val ROUTINE = "routine"
}

/** Per-instance home-screen widget settings. Not part of backups: widget ids belong to one launcher install. */
@Entity(tableName = "widget_configs")
data class WidgetConfigEntity(
    @PrimaryKey @ColumnInfo(name = "app_widget_id") val appWidgetId: Int,
    val kind: String,
    /** Shopping list id, routine id or task list id, depending on [kind]; null = default. */
    @ColumnInfo(name = "target_id") val targetId: Long? = null,
    /** Quick Add widget: due-date preset ("none", "today", "tomorrow"). */
    val option: String = "",
)
