package com.riyadmondol2006.taskfern.widget

import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import java.time.LocalDate

/**
 * Parameters carried by widget taps. Each action names the exact rows it acts on by id (never by position) plus the
 * state that was on screen, so a tap that arrives after the data changed cannot hit the wrong item.
 */
object WidgetParams {
    val TaskId = ActionParameters.Key<Long>("taskfern.task_id")
    val ExpectedDue = ActionParameters.Key<Long>("taskfern.expected_due_epoch_day")
    val ItemId = ActionParameters.Key<Long>("taskfern.item_id")
    val StepId = ActionParameters.Key<Long>("taskfern.step_id")
    val RoutineId = ActionParameters.Key<Long>("taskfern.routine_id")
    val Day = ActionParameters.Key<Long>("taskfern.epoch_day")
    val Checked = ActionParameters.Key<Boolean>("taskfern.checked")

    internal fun epochDay(value: Long?): LocalDate? =
        value?.takeIf { it in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay() }?.let(LocalDate::ofEpochDay)
}

/** Complete [taskId] only if it is still due on [expectedDue] (the occurrence that was shown). */
data class CompleteTaskRequest(val taskId: Long, val expectedDue: LocalDate) {
    fun toParameters(): ActionParameters = actionParametersOf(
        WidgetParams.TaskId to taskId,
        WidgetParams.ExpectedDue to expectedDue.toEpochDay(),
    )

    companion object {
        fun from(parameters: ActionParameters): CompleteTaskRequest? {
            val id = parameters[WidgetParams.TaskId]?.takeIf { it > 0 } ?: return null
            val due = WidgetParams.epochDay(parameters[WidgetParams.ExpectedDue]) ?: return null
            return CompleteTaskRequest(id, due)
        }
    }
}

/** Set shopping item [itemId] to [checked] (a target state, so repeated taps are harmless). */
data class ShoppingCheckRequest(val itemId: Long, val checked: Boolean) {
    fun toParameters(): ActionParameters = actionParametersOf(
        WidgetParams.ItemId to itemId,
        WidgetParams.Checked to checked,
    )

    companion object {
        fun from(parameters: ActionParameters): ShoppingCheckRequest? {
            val id = parameters[WidgetParams.ItemId]?.takeIf { it > 0 } ?: return null
            val checked = parameters[WidgetParams.Checked] ?: return null
            return ShoppingCheckRequest(id, checked)
        }
    }
}

/** Set routine step [stepId] of [routineId] to [checked] for [date] (the day the widget showed). */
data class RoutineStepRequest(val stepId: Long, val routineId: Long, val date: LocalDate, val checked: Boolean) {
    fun toParameters(): ActionParameters = actionParametersOf(
        WidgetParams.StepId to stepId,
        WidgetParams.RoutineId to routineId,
        WidgetParams.Day to date.toEpochDay(),
        WidgetParams.Checked to checked,
    )

    companion object {
        fun from(parameters: ActionParameters): RoutineStepRequest? {
            val step = parameters[WidgetParams.StepId]?.takeIf { it > 0 } ?: return null
            val routine = parameters[WidgetParams.RoutineId]?.takeIf { it > 0 } ?: return null
            val date = WidgetParams.epochDay(parameters[WidgetParams.Day]) ?: return null
            val checked = parameters[WidgetParams.Checked] ?: return null
            return RoutineStepRequest(step, routine, date, checked)
        }
    }
}
