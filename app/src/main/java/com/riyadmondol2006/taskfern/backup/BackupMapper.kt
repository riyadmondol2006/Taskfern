package com.riyadmondol2006.taskfern.backup

import com.riyadmondol2006.taskfern.data.db.FocusSessionEntity
import com.riyadmondol2006.taskfern.data.db.ItemMemoryEntity
import com.riyadmondol2006.taskfern.data.db.OccurrenceLogEntity
import com.riyadmondol2006.taskfern.data.db.ReminderEntity
import com.riyadmondol2006.taskfern.data.db.RoutineCheckEntity
import com.riyadmondol2006.taskfern.data.db.RoutineEntity
import com.riyadmondol2006.taskfern.data.db.RoutineStepEntity
import com.riyadmondol2006.taskfern.data.db.ShoppingItemEntity
import com.riyadmondol2006.taskfern.data.db.ShoppingListEntity
import com.riyadmondol2006.taskfern.data.db.SubtaskEntity
import com.riyadmondol2006.taskfern.data.db.TaskEntity
import com.riyadmondol2006.taskfern.data.db.TaskListEntity
import com.riyadmondol2006.taskfern.data.db.TemplateEntity
import com.riyadmondol2006.taskfern.data.prefs.Accent
import com.riyadmondol2006.taskfern.data.prefs.Settings
import com.riyadmondol2006.taskfern.data.prefs.TaskSort
import com.riyadmondol2006.taskfern.data.prefs.ThemeMode
import com.riyadmondol2006.taskfern.domain.recurrence.Frequency
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Every backed-up table as Room entities. Used for export, replace and as the input of a merge. */
data class DatabaseSnapshot(
    val taskLists: List<TaskListEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val subtasks: List<SubtaskEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val occurrenceLog: List<OccurrenceLogEntity> = emptyList(),
    val shoppingLists: List<ShoppingListEntity> = emptyList(),
    val shoppingItems: List<ShoppingItemEntity> = emptyList(),
    val itemMemory: List<ItemMemoryEntity> = emptyList(),
    val routines: List<RoutineEntity> = emptyList(),
    val routineSteps: List<RoutineStepEntity> = emptyList(),
    val routineChecks: List<RoutineCheckEntity> = emptyList(),
    val templates: List<TemplateEntity> = emptyList(),
    val focusSessions: List<FocusSessionEntity> = emptyList(),
) {
    val counts: BackupCounts
        get() = BackupCounts(
            taskLists = taskLists.size, tasks = tasks.size, subtasks = subtasks.size, reminders = reminders.size,
            occurrenceLog = occurrenceLog.size, shoppingLists = shoppingLists.size, shoppingItems = shoppingItems.size,
            itemMemory = itemMemory.size, routines = routines.size, routineSteps = routineSteps.size,
            routineChecks = routineChecks.size, templates = templates.size, focusSessions = focusSessions.size,
        )
}

/** Row counts per table, for previews, confirmations and result messages. */
data class BackupCounts(
    val taskLists: Int = 0,
    val tasks: Int = 0,
    val subtasks: Int = 0,
    val reminders: Int = 0,
    val occurrenceLog: Int = 0,
    val shoppingLists: Int = 0,
    val shoppingItems: Int = 0,
    val itemMemory: Int = 0,
    val routines: Int = 0,
    val routineSteps: Int = 0,
    val routineChecks: Int = 0,
    val templates: Int = 0,
    val focusSessions: Int = 0,
) {
    val total: Int
        get() = taskLists + tasks + subtasks + reminders + occurrenceLog + shoppingLists + shoppingItems + itemMemory +
            routines + routineSteps + routineChecks + templates + focusSessions

    val isEmpty: Boolean get() = total == 0
}

val BackupFile.counts: BackupCounts
    get() = BackupCounts(
        taskLists = taskLists.size, tasks = tasks.size, subtasks = subtasks.size, reminders = reminders.size,
        occurrenceLog = occurrenceLog.size, shoppingLists = shoppingLists.size, shoppingItems = shoppingItems.size,
        itemMemory = itemMemory.size, routines = routines.size, routineSteps = routineSteps.size,
        routineChecks = routineChecks.size, templates = templates.size, focusSessions = focusSessions.size,
    )

/**
 * Converts between Room entities and the backup file model. Pure: no I/O, no clock.
 *
 * [fromBackup] expects a file that passed [BackupValidator]; it throws on malformed values.
 */
object BackupMapper {
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun date(value: LocalDate): String = value.toString()
    fun parseDate(value: String): LocalDate = LocalDate.parse(value)
    fun time(value: LocalTime): String = value.format(TIME)
    fun time(minutes: Int): String = time(LocalTime.of(minutes / 60 % 24, minutes % 60))
    fun parseTime(value: String): LocalTime = LocalTime.parse(value, TIME)
    fun parseMinutes(value: String): Int = parseTime(value).let { it.hour * 60 + it.minute }

    fun weekdays(mask: Int): List<String> = RecurrenceRule.weekdaysFromMask(mask).map { it.name }
    fun weekdayMask(names: List<String>): Int = RecurrenceRule.weekdayMask(*names.map { DayOfWeek.valueOf(it) }.toTypedArray())

    fun toBackup(snapshot: DatabaseSnapshot, settings: BackupSettings?, exportedAt: Long, appVersion: String): BackupFile {
        val taskIds = snapshot.tasks.mapTo(HashSet()) { it.id }
        return BackupFile(
            format = BackupFile.FORMAT,
            version = BackupFile.VERSION,
            exportedAt = exportedAt,
            appVersion = appVersion,
            settings = settings,
            taskLists = snapshot.taskLists.map {
                BackupTaskList(it.id, it.name, it.color, it.icon, it.position, it.favorite, it.createdAt)
            },
            tasks = snapshot.tasks.map { t ->
                BackupTask(
                    id = t.id, listId = t.listId, title = t.title, notes = t.notes, priority = t.priority,
                    dueDate = t.dueDate?.let(::date), dueTime = t.dueTime?.let(::time), tags = t.tagList,
                    pinned = t.pinned, archived = t.archived, completed = t.isCompleted, completedAt = t.completedAt,
                    createdAt = t.createdAt, updatedAt = t.updatedAt, deletedAt = t.deletedAt, position = t.position,
                    recurrence = t.recurrence?.let { r ->
                        BackupRecurrence(
                            frequency = r.frequency.name, interval = r.interval, weekdays = weekdays(r.weekdays),
                            fromCompletion = r.fromCompletion, endDate = r.endDate?.let(::date),
                            maxOccurrences = r.maxOccurrences, paused = r.paused,
                        )
                    },
                    seriesAnchor = t.seriesAnchor?.let(::date), occurrenceCount = t.occurrenceCount,
                    dueScope = scopeName(t.dueScope),
                    // The series row may have been deleted for good; the link is informational only.
                    sourceTaskId = t.sourceTaskId?.takeIf { it in taskIds }, snoozeUntil = t.snoozeUntil,
                )
            },
            subtasks = snapshot.subtasks.map { BackupSubtask(it.id, it.taskId, it.title, it.done, it.position) },
            reminders = snapshot.reminders.map { BackupReminder(it.id, it.taskId, it.offsetMinutes) },
            occurrenceLog = snapshot.occurrenceLog.map {
                BackupOccurrence(it.id, it.taskId, date(it.occurrenceDate), it.action, it.at)
            },
            shoppingLists = snapshot.shoppingLists.map { BackupShoppingList(it.id, it.name, it.color, it.position, it.createdAt) },
            shoppingItems = snapshot.shoppingItems.map {
                BackupShoppingItem(
                    it.id, it.listId, it.name, it.quantity, it.unit, it.category, it.checked, it.position, it.priceCents,
                    it.note, it.createdAt, it.checkedAt,
                )
            },
            itemMemory = snapshot.itemMemory.map {
                BackupItemMemory(it.nameKey, it.name, it.category, it.unit, it.useCount, it.lastUsedAt, it.lastPriceCents)
            },
            routines = snapshot.routines.map {
                BackupRoutine(
                    it.id, it.name, it.emoji, it.color, weekdays(it.weekdays), it.timeMinutes?.let(::time), it.paused,
                    it.position, it.createdAt,
                )
            },
            routineSteps = snapshot.routineSteps.map { BackupRoutineStep(it.id, it.routineId, it.title, it.position) },
            routineChecks = snapshot.routineChecks.map { BackupRoutineCheck(it.stepId, it.routineId, date(it.date), it.checkedAt) },
            templates = snapshot.templates.map {
                BackupTemplate(it.id, it.kind, it.name, it.emoji, it.itemLines, it.builtin, it.position)
            },
            focusSessions = snapshot.focusSessions.map {
                BackupFocusSession(it.id, it.taskId, it.startedAt, it.plannedMinutes, it.focusedSeconds, it.completed)
            },
        )
    }

    fun fromBackup(file: BackupFile): DatabaseSnapshot {
        val taskIds = file.tasks.mapTo(HashSet()) { it.id }
        return DatabaseSnapshot(
            taskLists = file.taskLists.map {
                TaskListEntity(it.id, it.name.trim(), it.color, it.icon, it.position, it.favorite, it.createdAt)
            },
            tasks = file.tasks.map { t ->
                TaskEntity(
                    id = t.id, listId = t.listId, title = t.title.trim(), notes = t.notes, priority = t.priority,
                    dueDate = t.dueDate?.let(::parseDate), dueTime = t.dueTime?.let(::parseTime),
                    tags = TaskEntity.tagsToColumn(t.tags), pinned = t.pinned, archived = t.archived,
                    isCompleted = t.completed, completedAt = t.completedAt, createdAt = t.createdAt,
                    updatedAt = t.updatedAt, deletedAt = t.deletedAt, position = t.position,
                    recurrence = t.recurrence?.let { r ->
                        RecurrenceRule(
                            frequency = Frequency.valueOf(r.frequency), interval = r.interval,
                            weekdays = weekdayMask(r.weekdays), fromCompletion = r.fromCompletion,
                            endDate = r.endDate?.let(::parseDate), maxOccurrences = r.maxOccurrences, paused = r.paused,
                        )
                    },
                    seriesAnchor = t.seriesAnchor?.let(::parseDate), occurrenceCount = t.occurrenceCount,
                    dueScope = scopeValue(t.dueScope),
                    sourceTaskId = t.sourceTaskId?.takeIf { it in taskIds }, snoozeUntil = t.snoozeUntil,
                    nextReminderAt = null,
                )
            },
            subtasks = file.subtasks.map { SubtaskEntity(it.id, it.taskId, it.title, it.done, it.position) },
            reminders = file.reminders.map { ReminderEntity(it.id, it.taskId, it.offsetMinutes) },
            occurrenceLog = file.occurrenceLog.map {
                OccurrenceLogEntity(it.id, it.taskId, parseDate(it.occurrenceDate), it.action, it.at)
            },
            shoppingLists = file.shoppingLists.map { ShoppingListEntity(it.id, it.name.trim(), it.color, it.position, it.createdAt) },
            shoppingItems = file.shoppingItems.map {
                ShoppingItemEntity(
                    it.id, it.listId, it.name, it.quantity, it.unit, it.category, it.checked, it.position, it.priceCents,
                    it.note, it.createdAt, it.checkedAt,
                )
            },
            itemMemory = file.itemMemory.map {
                ItemMemoryEntity(it.nameKey, it.name, it.category, it.unit, it.useCount, it.lastUsedAt, it.lastPriceCents)
            },
            routines = file.routines.map {
                RoutineEntity(
                    id = it.id, name = it.name.trim(), emoji = it.emoji, color = it.color,
                    weekdays = weekdayMask(it.weekdays).takeIf { m -> m != 0 } ?: 0x7F,
                    timeMinutes = it.time?.let(::parseMinutes), paused = it.paused, position = it.position,
                    createdAt = it.createdAt, nextReminderAt = null,
                )
            },
            routineSteps = file.routineSteps.map { RoutineStepEntity(it.id, it.routineId, it.title, it.position) },
            routineChecks = file.routineChecks.map { RoutineCheckEntity(it.stepId, it.routineId, parseDate(it.date), it.checkedAt) },
            templates = file.templates.map {
                TemplateEntity(it.id, it.kind, it.name.trim(), it.emoji, it.items.joinToString("\n"), it.builtin, it.position)
            },
            focusSessions = file.focusSessions.map {
                FocusSessionEntity(it.id, it.taskId, it.startedAt, it.plannedMinutes, it.focusedSeconds, it.completed)
            },
        )
    }

    fun settingsToBackup(s: Settings): BackupSettings = BackupSettings(
        themeMode = s.themeMode.name,
        amoledBlack = s.amoledBlack,
        dynamicColor = s.dynamicColor,
        accent = s.accent.name,
        userName = s.userName,
        defaultReminderTime = time(s.defaultReminderMinutes),
        autoReminder = s.autoReminder,
        dailyAgendaEnabled = s.dailyAgendaEnabled,
        dailyAgendaTime = time(s.dailyAgendaMinutes),
        weekStartsMonday = s.weekStartsMonday,
        showCompletedToday = s.showCompletedToday,
        taskSort = s.taskSort.name,
        hideCheckedShopping = s.hideCheckedShopping,
        showShoppingPrices = s.showShoppingPrices,
        currencySymbol = s.currencySymbol,
        focusMinutes = s.focusMinutes,
        trashRetentionDays = s.trashRetentionDays,
    )

    /** [current] with every field present in [b] applied. Internal flags are always kept from [current]. */
    fun applySettings(current: Settings, b: BackupSettings): Settings = current.copy(
        themeMode = b.themeMode?.let { ThemeMode.valueOf(it) } ?: current.themeMode,
        amoledBlack = b.amoledBlack ?: current.amoledBlack,
        dynamicColor = b.dynamicColor ?: current.dynamicColor,
        accent = b.accent?.let { Accent.valueOf(it) } ?: current.accent,
        userName = b.userName?.trim()?.take(40) ?: current.userName,
        defaultReminderMinutes = b.defaultReminderTime?.let(::parseMinutes) ?: current.defaultReminderMinutes,
        autoReminder = b.autoReminder ?: current.autoReminder,
        dailyAgendaEnabled = b.dailyAgendaEnabled ?: current.dailyAgendaEnabled,
        dailyAgendaMinutes = b.dailyAgendaTime?.let(::parseMinutes) ?: current.dailyAgendaMinutes,
        weekStartsMonday = b.weekStartsMonday ?: current.weekStartsMonday,
        showCompletedToday = b.showCompletedToday ?: current.showCompletedToday,
        taskSort = b.taskSort?.let { TaskSort.valueOf(it) } ?: current.taskSort,
        hideCheckedShopping = b.hideCheckedShopping ?: current.hideCheckedShopping,
        showShoppingPrices = b.showShoppingPrices ?: current.showShoppingPrices,
        currencySymbol = b.currencySymbol?.trim()?.take(4) ?: current.currencySymbol,
        focusMinutes = b.focusMinutes ?: current.focusMinutes,
        trashRetentionDays = b.trashRetentionDays ?: current.trashRetentionDays,
    )

private fun scopeName(scope: Int): String? = when (scope) {
    com.riyadmondol2006.taskfern.data.db.DueScope.WEEK -> "week"
    com.riyadmondol2006.taskfern.data.db.DueScope.MONTH -> "month"
    com.riyadmondol2006.taskfern.data.db.DueScope.YEAR -> "year"
    else -> null
}

private fun scopeValue(name: String?): Int = when (name) {
    "week" -> com.riyadmondol2006.taskfern.data.db.DueScope.WEEK
    "month" -> com.riyadmondol2006.taskfern.data.db.DueScope.MONTH
    "year" -> com.riyadmondol2006.taskfern.data.db.DueScope.YEAR
    else -> com.riyadmondol2006.taskfern.data.db.DueScope.DAY
}
}
