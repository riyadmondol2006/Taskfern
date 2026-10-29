package com.riyadmondol2006.taskfern.data.repo

import androidx.room.withTransaction
import com.riyadmondol2006.taskfern.data.db.RoutineCheckEntity
import com.riyadmondol2006.taskfern.data.db.RoutineEntity
import com.riyadmondol2006.taskfern.data.db.RoutineStepEntity
import com.riyadmondol2006.taskfern.data.db.TaskfernDatabase
import java.time.Clock
import java.time.LocalDate

/** A step as edited in the routine editor; [id] is 0 for new steps. */
data class StepDraft(val id: Long = 0, val title: String)

data class RoutineDraft(
    val name: String,
    val emoji: String = "🌿",
    val color: Int = 0,
    val weekdays: Int = 0x7F,
    val timeMinutes: Int? = null,
    val steps: List<StepDraft> = emptyList(),
)

/**
 * Routines are reusable checklists. Ticks are stored per step per day, so history is simply the set of ticks and
 * "today" starts empty without any reset job.
 */
class RoutineRepository(
    private val db: TaskfernDatabase,
    private val events: DataEvents = DataEvents.None,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val dao = db.routineDao()
    private fun now() = clock.millis()

    fun observeRoutines() = dao.observeRoutines()
    fun observeRoutine(id: Long) = dao.observeRoutine(id)
    fun observeAllSteps() = dao.observeAllSteps()
    fun observeStepsWithChecks(routineId: Long, date: LocalDate) = dao.observeStepsWithChecks(routineId, date)
    fun observeChecksOn(date: LocalDate) = dao.observeChecksOn(date)
    fun observeProgressBetween(from: LocalDate, to: LocalDate) = dao.observeProgressBetween(from, to)
    suspend fun getRoutines() = dao.getRoutines()
    suspend fun getRoutine(id: Long) = dao.getRoutine(id)
    suspend fun getSteps(routineId: Long) = dao.getSteps(routineId)
    suspend fun getStepsWithChecks(routineId: Long, date: LocalDate) = dao.getStepsWithChecks(routineId, date)

    suspend fun create(draft: RoutineDraft): Long {
        val id = db.withTransaction {
            val id = dao.insertRoutine(
                RoutineEntity(
                    name = draft.name.trim().ifEmpty { "Routine" }, emoji = draft.emoji, color = draft.color,
                    weekdays = draft.weekdays.takeIf { it != 0 } ?: 0x7F, timeMinutes = draft.timeMinutes,
                    position = dao.nextRoutinePosition(), createdAt = now(),
                ),
            )
            dao.insertSteps(
                draft.steps.map { it.title.trim() }.filter { it.isNotEmpty() }
                    .mapIndexed { i, t -> RoutineStepEntity(routineId = id, title = t, position = i) },
            )
            id
        }
        events.routinesChanged(listOf(id))
        return id
    }

    /** Saves the editor. Steps keep their ids (and so their history) when only renamed or reordered. */
    suspend fun update(id: Long, draft: RoutineDraft) {
        db.withTransaction {
            val old = dao.getRoutine(id) ?: return@withTransaction
            dao.updateRoutine(
                old.copy(
                    name = draft.name.trim().ifEmpty { old.name }, emoji = draft.emoji, color = draft.color,
                    weekdays = draft.weekdays.takeIf { it != 0 } ?: 0x7F, timeMinutes = draft.timeMinutes,
                ),
            )
            val clean = draft.steps.filter { it.title.isNotBlank() }
            dao.deleteStepsExcept(id, clean.map { it.id }.filter { it != 0L })
            val existing = dao.getSteps(id).associateBy { it.id }
            clean.forEachIndexed { i, step ->
                val current = existing[step.id]
                if (current != null) {
                    dao.updateSteps(listOf(current.copy(title = step.title.trim(), position = i)))
                } else {
                    dao.insertStep(RoutineStepEntity(routineId = id, title = step.title.trim(), position = i))
                }
            }
        }
        events.routinesChanged(listOf(id))
    }

    suspend fun setPaused(id: Long, paused: Boolean) {
        val r = dao.getRoutine(id) ?: return
        dao.updateRoutine(r.copy(paused = paused))
        events.routinesChanged(listOf(id))
    }

    suspend fun delete(id: Long) {
        dao.deleteRoutine(id)
        events.routinesChanged(listOf(id))
    }

    suspend fun duplicate(id: Long): Long? {
        val r = dao.getRoutine(id) ?: return null
        return create(
            RoutineDraft(
                name = "${r.name} (copy)", emoji = r.emoji, color = r.color, weekdays = r.weekdays, timeMinutes = r.timeMinutes,
                steps = dao.getSteps(id).map { StepDraft(title = it.title) },
            ),
        )
    }

    /** Ticks or unticks a step for [date]. Idempotent, so repeated widget taps never flip it back unexpectedly. */
    suspend fun setStepChecked(stepId: Long, routineId: Long, date: LocalDate, checked: Boolean) {
        if (checked) dao.insertCheck(RoutineCheckEntity(stepId, routineId, date, now()))
        else dao.deleteCheck(stepId, date)
        events.routinesChanged(listOf(routineId))
    }

    suspend fun toggleStep(stepId: Long, routineId: Long, date: LocalDate) {
        setStepChecked(stepId, routineId, date, !dao.isChecked(stepId, date))
    }

    suspend fun checkAll(routineId: Long, date: LocalDate) {
        db.withTransaction { dao.getSteps(routineId).forEach { dao.insertCheck(RoutineCheckEntity(it.id, routineId, date, now())) } }
        events.routinesChanged(listOf(routineId))
    }

    suspend fun resetDay(routineId: Long, date: LocalDate) {
        dao.clearChecks(routineId, date)
        events.routinesChanged(listOf(routineId))
    }

    suspend fun setNextReminderAt(id: Long, at: Long?) = dao.setNextReminderAt(id, at)

    companion object {
        fun runsOn(routine: RoutineEntity, date: LocalDate): Boolean =
            routine.weekdays and (1 shl (date.dayOfWeek.value - 1)) != 0
    }
}
