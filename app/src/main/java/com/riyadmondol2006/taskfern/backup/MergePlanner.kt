package com.riyadmondol2006.taskfern.backup

import com.riyadmondol2006.taskfern.data.repo.ShoppingRepository
import java.time.LocalDate

/** The rows a merge will insert (with final ids and rewritten references) and what it adds and skips. */
data class MergePlan(
    val insert: DatabaseSnapshot,
    val added: BackupCounts,
    val skipped: BackupCounts,
)

/**
 * Plans a merge of an imported backup into the existing data. Pure: the caller reads the current rows and the
 * highest ids ever used inside a transaction, calls [plan], then inserts [MergePlan.insert] in the same transaction.
 *
 * Every inserted row gets a fresh id above anything the table has used, and every reference (list, task, series,
 * shopping list, routine, step) is rewritten to the new ids or to the existing row it was matched with.
 *
 * Duplicate rules — an imported row matches an existing (pre-merge) row when:
 * - task list, shopping list, routine: same name, ignoring case and surrounding spaces. The imported contents are
 *   merged into the existing one instead of creating a second list with the same name.
 * - task: same title and same creation time. A matched task is skipped together with its subtasks, reminders and
 *   history (the existing task already has its own).
 * - shopping item: same normalised name already on the (existing) list it merges into.
 * - remembered item (suggestions): same normalised name key.
 * - routine step: same title in the existing routine it merges into; unmatched steps are appended to that routine.
 * - routine check: same step and day.
 * - template: same kind and name, ignoring case.
 * - focus session: same start time.
 * Rows are only compared with data that existed before the merge, so a backup merged into an empty app reproduces
 * every row, even two lists that share a name.
 */
object MergePlanner {

    /** Highest id ever used per table (from SQLite's AUTOINCREMENT bookkeeping); 0 when unknown. */
    data class IdFloors(
        val taskLists: Long = 0,
        val tasks: Long = 0,
        val subtasks: Long = 0,
        val reminders: Long = 0,
        val occurrenceLog: Long = 0,
        val shoppingLists: Long = 0,
        val shoppingItems: Long = 0,
        val routines: Long = 0,
        val routineSteps: Long = 0,
        val templates: Long = 0,
        val focusSessions: Long = 0,
    )

    private class Ids(floor: Long, existingMax: Long?) {
        private var next = maxOf(floor, existingMax ?: 0L) + 1
        fun take(): Long = next++
    }

    private fun nameKey(name: String) = name.trim().lowercase()

    fun plan(existing: DatabaseSnapshot, incoming: DatabaseSnapshot, floors: IdFloors = IdFloors()): MergePlan {
        // ---- Task lists -------------------------------------------------------------------------------------------
        val listIds = Ids(floors.taskLists, existing.taskLists.maxOfOrNull { it.id })
        val existingLists = existing.taskLists.groupBy { nameKey(it.name) }.mapValues { it.value.first().id }
        var listPos = existing.taskLists.maxOfOrNull { it.position } ?: -1
        val listMap = HashMap<Long, Long>()
        val newLists = incoming.taskLists.sortedWith(compareBy({ it.position }, { it.id })).mapNotNull { l ->
            val match = existingLists[nameKey(l.name)]
            if (match != null) {
                listMap[l.id] = match
                null
            } else {
                val id = listIds.take()
                listMap[l.id] = id
                l.copy(id = id, position = ++listPos)
            }
        }

        // ---- Tasks (ids first, so series links can point forward) -------------------------------------------------
        val taskIds = Ids(floors.tasks, existing.tasks.maxOfOrNull { it.id })
        val existingTasks = existing.tasks.groupBy { it.title.trim() to it.createdAt }.mapValues { it.value.first().id }
        val taskMap = HashMap<Long, Long>()
        val insertedTaskIds = HashSet<Long>()
        incoming.tasks.forEach { t ->
            val match = existingTasks[t.title.trim() to t.createdAt]
            if (match != null) {
                taskMap[t.id] = match
            } else {
                taskMap[t.id] = taskIds.take()
                insertedTaskIds += t.id
            }
        }
        val newTasks = incoming.tasks.filter { it.id in insertedTaskIds }.map { t ->
            t.copy(
                id = taskMap.getValue(t.id),
                listId = t.listId?.let { listMap[it] },
                sourceTaskId = t.sourceTaskId?.let { taskMap[it] },
                nextReminderAt = null,
            )
        }

        val subtaskIds = Ids(floors.subtasks, existing.subtasks.maxOfOrNull { it.id })
        val newSubtasks = incoming.subtasks.filter { it.taskId in insertedTaskIds }
            .map { it.copy(id = subtaskIds.take(), taskId = taskMap.getValue(it.taskId)) }

        val reminderIds = Ids(floors.reminders, existing.reminders.maxOfOrNull { it.id })
        val newReminders = incoming.reminders.filter { it.taskId in insertedTaskIds }
            .map { it.copy(id = reminderIds.take(), taskId = taskMap.getValue(it.taskId)) }

        val logIds = Ids(floors.occurrenceLog, existing.occurrenceLog.maxOfOrNull { it.id })
        val newLog = incoming.occurrenceLog.filter { it.taskId in insertedTaskIds }
            .map { it.copy(id = logIds.take(), taskId = taskMap.getValue(it.taskId)) }

        // ---- Shopping ---------------------------------------------------------------------------------------------
        val shoppingIds = Ids(floors.shoppingLists, existing.shoppingLists.maxOfOrNull { it.id })
        val existingShopping = existing.shoppingLists.groupBy { nameKey(it.name) }.mapValues { it.value.first().id }
        var shoppingPos = existing.shoppingLists.maxOfOrNull { it.position } ?: -1
        val shoppingMap = HashMap<Long, Long>()
        val matchedShopping = HashSet<Long>() // ids of existing lists that receive imported items
        val newShoppingLists = incoming.shoppingLists.sortedWith(compareBy({ it.position }, { it.id })).mapNotNull { l ->
            val match = existingShopping[nameKey(l.name)]
            if (match != null) {
                shoppingMap[l.id] = match
                matchedShopping += match
                null
            } else {
                val id = shoppingIds.take()
                shoppingMap[l.id] = id
                l.copy(id = id, position = ++shoppingPos)
            }
        }
        val existingItemNames: Map<Long, Set<String>> = existing.shoppingItems
            .filter { it.listId in matchedShopping }
            .groupBy({ it.listId }, { ShoppingRepository.nameKey(it.name) })
            .mapValues { it.value.toSet() }
        val itemPositions = HashMap<Long, Int>().apply {
            existing.shoppingItems.filter { it.listId in matchedShopping }.forEach { merge(it.listId, it.position) { a, b -> maxOf(a, b) } }
        }
        val itemIds = Ids(floors.shoppingItems, existing.shoppingItems.maxOfOrNull { it.id })
        val newItems = incoming.shoppingItems.sortedWith(compareBy({ it.position }, { it.id })).mapNotNull { item ->
            val target = shoppingMap.getValue(item.listId)
            if (target in matchedShopping) {
                if (ShoppingRepository.nameKey(item.name) in existingItemNames[target].orEmpty()) return@mapNotNull null
                val pos = (itemPositions[target] ?: -1) + 1
                itemPositions[target] = pos
                item.copy(id = itemIds.take(), listId = target, position = pos)
            } else {
                item.copy(id = itemIds.take(), listId = target)
            }
        }
        val existingMemory = existing.itemMemory.mapTo(HashSet()) { it.nameKey }
        val newMemory = incoming.itemMemory.filter { it.nameKey !in existingMemory }

        // ---- Routines ---------------------------------------------------------------------------------------------
        val routineIds = Ids(floors.routines, existing.routines.maxOfOrNull { it.id })
        val existingRoutines = existing.routines.groupBy { nameKey(it.name) }.mapValues { it.value.first().id }
        var routinePos = existing.routines.maxOfOrNull { it.position } ?: -1
        val routineMap = HashMap<Long, Long>()
        val matchedRoutines = HashSet<Long>()
        val newRoutines = incoming.routines.sortedWith(compareBy({ it.position }, { it.id })).mapNotNull { r ->
            val match = existingRoutines[nameKey(r.name)]
            if (match != null) {
                routineMap[r.id] = match
                matchedRoutines += match
                null
            } else {
                val id = routineIds.take()
                routineMap[r.id] = id
                r.copy(id = id, position = ++routinePos, nextReminderAt = null)
            }
        }
        val existingSteps: Map<Long, Map<String, Long>> = existing.routineSteps
            .filter { it.routineId in matchedRoutines }
            .groupBy { it.routineId }
            .mapValues { (_, steps) -> steps.groupBy { nameKey(it.title) }.mapValues { it.value.first().id } }
        val stepPositions = HashMap<Long, Int>().apply {
            existing.routineSteps.filter { it.routineId in matchedRoutines }.forEach { merge(it.routineId, it.position) { a, b -> maxOf(a, b) } }
        }
        val stepIds = Ids(floors.routineSteps, existing.routineSteps.maxOfOrNull { it.id })
        val stepMap = HashMap<Long, Long>()
        val newSteps = incoming.routineSteps.sortedWith(compareBy({ it.position }, { it.id })).mapNotNull { s ->
            val routine = routineMap.getValue(s.routineId)
            if (routine in matchedRoutines) {
                val match = existingSteps[routine]?.get(nameKey(s.title))
                if (match != null) {
                    stepMap[s.id] = match
                    return@mapNotNull null
                }
                val pos = (stepPositions[routine] ?: -1) + 1
                stepPositions[routine] = pos
                val id = stepIds.take()
                stepMap[s.id] = id
                s.copy(id = id, routineId = routine, position = pos)
            } else {
                val id = stepIds.take()
                stepMap[s.id] = id
                s.copy(id = id, routineId = routine)
            }
        }
        val existingChecks: Set<Pair<Long, LocalDate>> = existing.routineChecks.mapTo(HashSet()) { it.stepId to it.date }
        val newChecks = incoming.routineChecks.mapNotNull { c ->
            val step = stepMap.getValue(c.stepId)
            if (step to c.date in existingChecks) null
            else c.copy(stepId = step, routineId = routineMap.getValue(c.routineId))
        }

        // ---- Templates and focus history ---------------------------------------------------------------------------
        val templateIds = Ids(floors.templates, existing.templates.maxOfOrNull { it.id })
        val existingTemplates = existing.templates.mapTo(HashSet()) { it.kind to nameKey(it.name) }
        var templatePos = existing.templates.maxOfOrNull { it.position } ?: -1
        val newTemplates = incoming.templates.sortedWith(compareBy({ it.position }, { it.id }))
            .filter { (it.kind to nameKey(it.name)) !in existingTemplates }
            .map { it.copy(id = templateIds.take(), position = ++templatePos) }

        val focusIds = Ids(floors.focusSessions, existing.focusSessions.maxOfOrNull { it.id })
        val existingFocus = existing.focusSessions.mapTo(HashSet()) { it.startedAt }
        val newFocus = incoming.focusSessions.filter { it.startedAt !in existingFocus }
            .map { it.copy(id = focusIds.take(), taskId = it.taskId?.let { t -> taskMap[t] }) }

        val insert = DatabaseSnapshot(
            taskLists = newLists, tasks = newTasks, subtasks = newSubtasks, reminders = newReminders,
            occurrenceLog = newLog, shoppingLists = newShoppingLists, shoppingItems = newItems, itemMemory = newMemory,
            routines = newRoutines, routineSteps = newSteps, routineChecks = newChecks, templates = newTemplates,
            focusSessions = newFocus,
        )
        val added = insert.counts
        val all = incoming.counts
        val skipped = BackupCounts(
            taskLists = all.taskLists - added.taskLists, tasks = all.tasks - added.tasks,
            subtasks = all.subtasks - added.subtasks, reminders = all.reminders - added.reminders,
            occurrenceLog = all.occurrenceLog - added.occurrenceLog, shoppingLists = all.shoppingLists - added.shoppingLists,
            shoppingItems = all.shoppingItems - added.shoppingItems, itemMemory = all.itemMemory - added.itemMemory,
            routines = all.routines - added.routines, routineSteps = all.routineSteps - added.routineSteps,
            routineChecks = all.routineChecks - added.routineChecks, templates = all.templates - added.templates,
            focusSessions = all.focusSessions - added.focusSessions,
        )
        return MergePlan(insert, added, skipped)
    }
}
