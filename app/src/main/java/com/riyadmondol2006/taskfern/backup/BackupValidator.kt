package com.riyadmondol2006.taskfern.backup

import com.riyadmondol2006.taskfern.data.db.OccurrenceAction
import com.riyadmondol2006.taskfern.data.db.TemplateKind
import com.riyadmondol2006.taskfern.data.prefs.Accent
import com.riyadmondol2006.taskfern.data.prefs.TaskSort
import com.riyadmondol2006.taskfern.data.prefs.ThemeMode
import com.riyadmondol2006.taskfern.domain.recurrence.Frequency
import java.time.DayOfWeek

/**
 * Checks a decoded backup before anything touches the database:
 * - required text is present (titles and names are not blank),
 * - enum-like strings are known values, dates are ISO dates, times are "HH:mm",
 * - numbers are in sane ranges,
 * - ids are positive and unique per table,
 * - every reference points to a row that is in the same file (task → list, subtask/reminder/history → task,
 *   item → shopping list, step → routine, check → step of the same routine, focus session → task).
 *
 * A completed occurrence's link to its series (`sourceTaskId`) is informational and not enforced: a dangling link is
 * dropped on import instead of rejecting the file.
 *
 * Returns human-readable problems (at most [MAX_PROBLEMS]); an empty list means the file is safe to import.
 */
object BackupValidator {
    const val MAX_PROBLEMS = 20

    /** Upper bounds that no real data reaches; they catch corrupt or hostile files. */
    private const val MAX_TEXT = 100_000
    private const val MAX_REMINDER_OFFSET = 366 * 24 * 60
    private const val MAX_FOCUS_MINUTES = 24 * 60

    private val frequencies = Frequency.entries.map { it.name }.toSet()
    private val dayNames = DayOfWeek.entries.map { it.name }.toSet()
    private val actions = setOf(OccurrenceAction.COMPLETED, OccurrenceAction.SKIPPED)
    private val kinds = setOf(TemplateKind.TASKS, TemplateKind.SHOPPING, TemplateKind.ROUTINE)

    fun validate(file: BackupFile): List<String> {
        val p = Problems()

        val listIds = p.uniqueIds("taskLists", file.taskLists.map { it.id })
        file.taskLists.forEachIndexed { i, l ->
            val at = "taskLists[$i]"
            p.require(l.name.isNotBlank(), "$at: the list name is empty.")
            p.textLength(at, l.name)
            p.require(l.createdAt >= 0, "$at: createdAt is negative.")
        }

        val taskIds = p.uniqueIds("tasks", file.tasks.map { it.id })
        file.tasks.forEachIndexed { i, t ->
            val at = "tasks[$i] (id ${t.id})"
            p.require(t.title.isNotBlank(), "$at: the title is empty.")
            p.textLength(at, t.title)
            p.textLength(at, t.notes)
            p.require(t.priority in 0..3, "$at: priority ${t.priority} is not between 0 and 3.")
            p.date(at, "dueDate", t.dueDate)
            p.time(at, "dueTime", t.dueTime)
            p.require(t.dueTime == null || t.dueDate != null, "$at: has a due time but no due date.")
            p.date(at, "seriesAnchor", t.seriesAnchor)
            p.require(t.occurrenceCount >= 0, "$at: occurrenceCount is negative.")
            p.require(t.dueScope == null || t.dueScope in setOf("day", "week", "month", "year"), "$at: unknown dueScope '${t.dueScope}'.")
            p.require(t.dueScope == null || t.dueScope == "day" || t.dueDate != null, "$at: a plan needs a dueDate.")
            p.require(t.createdAt >= 0 && t.updatedAt >= 0, "$at: timestamps must not be negative.")
            p.require(t.listId == null || t.listId in listIds, "$at: list ${t.listId} is not in the backup.")
            t.recurrence?.let { r ->
                p.require(r.frequency in frequencies, "$at: unknown repeat frequency '${r.frequency}'.")
                p.require(r.interval in 1..999, "$at: repeat interval ${r.interval} is not between 1 and 999.")
                r.weekdays.forEach { d -> p.require(d in dayNames, "$at: unknown weekday '$d'.") }
                p.date(at, "recurrence.endDate", r.endDate)
                p.require(r.maxOccurrences == null || r.maxOccurrences >= 1, "$at: maxOccurrences must be at least 1.")
            }
        }

        p.uniqueIds("subtasks", file.subtasks.map { it.id })
        file.subtasks.forEachIndexed { i, s ->
            val at = "subtasks[$i]"
            p.require(s.title.isNotBlank(), "$at: the title is empty.")
            p.textLength(at, s.title)
            p.require(s.taskId in taskIds, "$at: task ${s.taskId} is not in the backup.")
        }

        p.uniqueIds("reminders", file.reminders.map { it.id })
        file.reminders.forEachIndexed { i, r ->
            val at = "reminders[$i]"
            p.require(r.offsetMinutes in 0..MAX_REMINDER_OFFSET, "$at: offset ${r.offsetMinutes} minutes is out of range.")
            p.require(r.taskId in taskIds, "$at: task ${r.taskId} is not in the backup.")
        }

        p.uniqueIds("occurrenceLog", file.occurrenceLog.map { it.id })
        file.occurrenceLog.forEachIndexed { i, o ->
            val at = "occurrenceLog[$i]"
            p.date(at, "occurrenceDate", o.occurrenceDate)
            p.require(o.action in actions, "$at: unknown action '${o.action}'.")
            p.require(o.taskId in taskIds, "$at: task ${o.taskId} is not in the backup.")
        }

        val shoppingIds = p.uniqueIds("shoppingLists", file.shoppingLists.map { it.id })
        file.shoppingLists.forEachIndexed { i, l ->
            val at = "shoppingLists[$i]"
            p.require(l.name.isNotBlank(), "$at: the list name is empty.")
            p.textLength(at, l.name)
        }

        p.uniqueIds("shoppingItems", file.shoppingItems.map { it.id })
        file.shoppingItems.forEachIndexed { i, item ->
            val at = "shoppingItems[$i]"
            p.require(item.name.isNotBlank(), "$at: the item name is empty.")
            p.textLength(at, item.name)
            p.require(item.priceCents == null || item.priceCents >= 0, "$at: the price is negative.")
            p.require(item.listId in shoppingIds, "$at: shopping list ${item.listId} is not in the backup.")
        }

        val memoryKeys = file.itemMemory.map { it.nameKey }
        p.require(memoryKeys.size == memoryKeys.toSet().size, "itemMemory: the same item is listed twice.")
        file.itemMemory.forEachIndexed { i, m ->
            val at = "itemMemory[$i]"
            p.require(m.nameKey.isNotBlank() && m.name.isNotBlank(), "$at: the item name is empty.")
            p.require(m.useCount >= 0, "$at: useCount is negative.")
        }

        val routineIds = p.uniqueIds("routines", file.routines.map { it.id })
        file.routines.forEachIndexed { i, r ->
            val at = "routines[$i]"
            p.require(r.name.isNotBlank(), "$at: the routine name is empty.")
            p.textLength(at, r.name)
            r.weekdays.forEach { d -> p.require(d in dayNames, "$at: unknown weekday '$d'.") }
            p.time(at, "time", r.time)
        }

        p.uniqueIds("routineSteps", file.routineSteps.map { it.id })
        val stepRoutine = file.routineSteps.associate { it.id to it.routineId }
        file.routineSteps.forEachIndexed { i, s ->
            val at = "routineSteps[$i]"
            p.require(s.title.isNotBlank(), "$at: the step is empty.")
            p.textLength(at, s.title)
            p.require(s.routineId in routineIds, "$at: routine ${s.routineId} is not in the backup.")
        }

        val checkKeys = file.routineChecks.map { it.stepId to it.date }
        p.require(checkKeys.size == checkKeys.toSet().size, "routineChecks: the same step is ticked twice on one day.")
        file.routineChecks.forEachIndexed { i, c ->
            val at = "routineChecks[$i]"
            p.date(at, "date", c.date)
            val owner = stepRoutine[c.stepId]
            p.require(owner != null, "$at: step ${c.stepId} is not in the backup.")
            p.require(c.routineId in routineIds, "$at: routine ${c.routineId} is not in the backup.")
            p.require(owner == null || owner == c.routineId, "$at: step ${c.stepId} does not belong to routine ${c.routineId}.")
        }

        p.uniqueIds("templates", file.templates.map { it.id })
        file.templates.forEachIndexed { i, t ->
            val at = "templates[$i]"
            p.require(t.kind in kinds, "$at: unknown template kind '${t.kind}'.")
            p.require(t.name.isNotBlank(), "$at: the template name is empty.")
            p.textLength(at, t.name)
        }

        p.uniqueIds("focusSessions", file.focusSessions.map { it.id })
        file.focusSessions.forEachIndexed { i, f ->
            val at = "focusSessions[$i]"
            p.require(f.plannedMinutes in 1..MAX_FOCUS_MINUTES, "$at: planned minutes ${f.plannedMinutes} out of range.")
            p.require(f.focusedSeconds in 0..MAX_FOCUS_MINUTES * 60L, "$at: focused seconds ${f.focusedSeconds} out of range.")
            p.require(f.taskId == null || f.taskId in taskIds, "$at: task ${f.taskId} is not in the backup.")
        }

        file.settings?.let { s ->
            val at = "settings"
            s.themeMode?.let { v -> p.require(ThemeMode.entries.any { it.name == v }, "$at: unknown theme '$v'.") }
            s.accent?.let { v -> p.require(Accent.entries.any { it.name == v }, "$at: unknown accent '$v'.") }
            s.taskSort?.let { v -> p.require(TaskSort.entries.any { it.name == v }, "$at: unknown sort order '$v'.") }
            p.time(at, "defaultReminderTime", s.defaultReminderTime)
            p.time(at, "dailyAgendaTime", s.dailyAgendaTime)
            s.focusMinutes?.let { p.require(it in 1..180, "$at: focus length $it is not between 1 and 180 minutes.") }
            s.trashRetentionDays?.let { p.require(it in 1..365, "$at: trash retention $it is not between 1 and 365 days.") }
        }

        return p.list
    }

    private class Problems {
        val list = ArrayList<String>()

        fun require(ok: Boolean, message: String) {
            if (!ok && list.size < MAX_PROBLEMS) list += message
        }

        fun uniqueIds(table: String, ids: List<Long>): Set<Long> {
            val set = ids.toHashSet()
            require(set.size == ids.size, "$table: some ids are used twice.")
            require(ids.all { it > 0 }, "$table: ids must be positive.")
            return set
        }

        fun textLength(at: String, value: String) = require(value.length <= MAX_TEXT, "$at: text is too long.")

        fun date(at: String, field: String, value: String?) {
            if (value == null) return
            require(runCatching { BackupMapper.parseDate(value) }.isSuccess, "$at: $field '$value' is not a date (yyyy-MM-dd).")
        }

        fun time(at: String, field: String, value: String?) {
            if (value == null) return
            require(runCatching { BackupMapper.parseTime(value) }.isSuccess, "$at: $field '$value' is not a time (HH:mm).")
        }
    }
}
