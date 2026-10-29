package com.riyadmondol2006.taskfern.backup

import kotlinx.serialization.Serializable

/**
 * The Taskfern backup file (JSON). Version 1.
 *
 * Encoding rules, chosen so a file is readable by people and stable across time zones:
 * - Calendar dates (due dates, series anchors, routine check days, end dates) are ISO-8601 local dates: "2026-10-29".
 * - Times of day are 24-hour "HH:mm" strings ("07:30"). They are wall-clock times without a zone, like in the app.
 * - Instants (createdAt, completedAt, exportedAt, …) are Unix epoch milliseconds (UTC).
 * - Weekday sets are lists of ISO day names ("MONDAY" … "SUNDAY").
 * - Ids are the database ids at export time. They are only used to link rows inside the file; a merge gives imported
 *   rows new ids and rewrites every reference.
 * - Derived alarm bookkeeping (`next_reminder_at`) is not exported: alarms are rebuilt after every import.
 * - Home-screen widget settings are not exported: widget ids belong to one launcher install.
 *
 * Every table except `widget_configs` is included, plus a stable subset of user settings.
 */
@Serializable
data class BackupFile(
    val format: String,
    val version: Int,
    val exportedAt: Long,
    val appVersion: String = "",
    val settings: BackupSettings? = null,
    val taskLists: List<BackupTaskList> = emptyList(),
    val tasks: List<BackupTask> = emptyList(),
    val subtasks: List<BackupSubtask> = emptyList(),
    val reminders: List<BackupReminder> = emptyList(),
    val occurrenceLog: List<BackupOccurrence> = emptyList(),
    val shoppingLists: List<BackupShoppingList> = emptyList(),
    val shoppingItems: List<BackupShoppingItem> = emptyList(),
    val itemMemory: List<BackupItemMemory> = emptyList(),
    val routines: List<BackupRoutine> = emptyList(),
    val routineSteps: List<BackupRoutineStep> = emptyList(),
    val routineChecks: List<BackupRoutineCheck> = emptyList(),
    val templates: List<BackupTemplate> = emptyList(),
    val focusSessions: List<BackupFocusSession> = emptyList(),
) {
    companion object {
        const val FORMAT = "taskfern-backup"
        const val VERSION = 1
    }
}

@Serializable
data class BackupTaskList(
    val id: Long,
    val name: String,
    val color: Int = 0,
    val icon: String = "list",
    val position: Int = 0,
    val favorite: Boolean = false,
    val createdAt: Long,
)

@Serializable
data class BackupRecurrence(
    /** "DAILY", "WEEKLY", "MONTHLY" or "YEARLY". */
    val frequency: String,
    val interval: Int = 1,
    /** For WEEKLY: the days it repeats on; empty = the weekday of the series start. */
    val weekdays: List<String> = emptyList(),
    val fromCompletion: Boolean = false,
    val endDate: String? = null,
    val maxOccurrences: Int? = null,
    val paused: Boolean = false,
)

@Serializable
data class BackupTask(
    val id: Long,
    val listId: Long? = null,
    val title: String,
    val notes: String = "",
    /** 0 none, 1 low, 2 medium, 3 high. */
    val priority: Int = 0,
    val dueDate: String? = null,
    val dueTime: String? = null,
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val position: Long = 0,
    val recurrence: BackupRecurrence? = null,
    val seriesAnchor: String? = null,
    val occurrenceCount: Int = 0,
    /** "day" (default, may be omitted), or "week" / "month" / "year" for a plan ending on dueDate. Added in app schema 2. */
    val dueScope: String? = null,
    /** For a completed occurrence of a recurring task: the id of the series it came from. */
    val sourceTaskId: Long? = null,
    val snoozeUntil: Long? = null,
)

@Serializable
data class BackupSubtask(
    val id: Long,
    val taskId: Long,
    val title: String,
    val done: Boolean = false,
    val position: Int = 0,
)

@Serializable
data class BackupReminder(
    val id: Long,
    val taskId: Long,
    /** Minutes before the due moment; 0 = at the due time. */
    val offsetMinutes: Int,
)

@Serializable
data class BackupOccurrence(
    val id: Long,
    val taskId: Long,
    val occurrenceDate: String,
    /** "completed" or "skipped". */
    val action: String,
    val at: Long,
)

@Serializable
data class BackupShoppingList(
    val id: Long,
    val name: String,
    val color: Int = 0,
    val position: Int = 0,
    val createdAt: Long,
)

@Serializable
data class BackupShoppingItem(
    val id: Long,
    val listId: Long,
    val name: String,
    val quantity: String = "",
    val unit: String = "",
    val category: String = "",
    val checked: Boolean = false,
    val position: Int = 0,
    val priceCents: Long? = null,
    val note: String = "",
    val createdAt: Long,
    val checkedAt: Long? = null,
)

@Serializable
data class BackupItemMemory(
    val nameKey: String,
    val name: String,
    val category: String = "",
    val unit: String = "",
    val useCount: Int = 0,
    val lastUsedAt: Long = 0,
    val lastPriceCents: Long? = null,
)

@Serializable
data class BackupRoutine(
    val id: Long,
    val name: String,
    val emoji: String = "",
    val color: Int = 0,
    val weekdays: List<String>,
    val time: String? = null,
    val paused: Boolean = false,
    val position: Int = 0,
    val createdAt: Long,
)

@Serializable
data class BackupRoutineStep(
    val id: Long,
    val routineId: Long,
    val title: String,
    val position: Int = 0,
)

@Serializable
data class BackupRoutineCheck(
    val stepId: Long,
    val routineId: Long,
    val date: String,
    val checkedAt: Long,
)

@Serializable
data class BackupTemplate(
    val id: Long,
    /** "tasks", "shopping" or "routine". */
    val kind: String,
    val name: String,
    val emoji: String = "",
    val items: List<String> = emptyList(),
    val builtin: Boolean = false,
    val position: Int = 0,
)

@Serializable
data class BackupFocusSession(
    val id: Long,
    val taskId: Long? = null,
    val startedAt: Long,
    val plannedMinutes: Int,
    val focusedSeconds: Long = 0,
    val completed: Boolean = false,
)

/**
 * User preferences worth carrying to another phone. Internal bookkeeping (onboarding, seeded templates, last Quick Add
 * list) is deliberately left out. Every field is optional so a file written by a later version with fewer or more
 * settings still restores what it can; a missing field keeps the current value.
 */
@Serializable
data class BackupSettings(
    /** "SYSTEM", "LIGHT" or "DARK". */
    val themeMode: String? = null,
    val amoledBlack: Boolean? = null,
    val dynamicColor: Boolean? = null,
    /** "FERN", "MOSS", "OCEAN", "DUSK" or "CLAY". */
    val accent: String? = null,
    val userName: String? = null,
    /** "HH:mm". */
    val defaultReminderTime: String? = null,
    val autoReminder: Boolean? = null,
    val dailyAgendaEnabled: Boolean? = null,
    /** "HH:mm". */
    val dailyAgendaTime: String? = null,
    val weekStartsMonday: Boolean? = null,
    val showCompletedToday: Boolean? = null,
    /** "SMART", "DUE_DATE", "PRIORITY", "CREATED", "TITLE" or "MANUAL". */
    val taskSort: String? = null,
    val hideCheckedShopping: Boolean? = null,
    val showShoppingPrices: Boolean? = null,
    val currencySymbol: String? = null,
    val focusMinutes: Int? = null,
    val trashRetentionDays: Int? = null,
)
