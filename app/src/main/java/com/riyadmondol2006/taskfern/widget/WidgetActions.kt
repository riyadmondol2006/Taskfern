package com.riyadmondol2006.taskfern.widget

import com.riyadmondol2006.taskfern.domain.LogicalDay
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.riyadmondol2006.taskfern.data.db.WidgetKind
import com.riyadmondol2006.taskfern.data.repo.RoutineRepository
import com.riyadmondol2006.taskfern.data.repo.ShoppingRepository
import com.riyadmondol2006.taskfern.data.repo.TaskRepository
import java.time.LocalDate

/**
 * The data side of widget taps, separate from Glance so it can be tested against a real database. Every method is
 * safe to call twice with the same request and does nothing when the request no longer matches the data.
 */
class WidgetActionHandler(
    private val tasks: TaskRepository,
    private val shopping: ShoppingRepository,
    private val routines: RoutineRepository,
    private val today: () -> LocalDate = { LogicalDay.today() },
) {
    /**
     * Completes the occurrence that was on screen. Returns false when the task had already moved on (completed,
     * deleted, or a recurring task already rolled to its next date), which is exactly the double-tap case.
     */
    suspend fun completeTask(request: CompleteTaskRequest): Boolean =
        tasks.complete(request.taskId, request.expectedDue, checkExpected = true) != null

    /** Sets the item's checked state; a missing item or an item already in that state is left alone. */
    suspend fun setShoppingItem(request: ShoppingCheckRequest) {
        shopping.setChecked(request.itemId, request.checked)
    }

    /**
     * Ticks or unticks a routine step for today. Ignored when the widget was showing another day (a tap just after
     * midnight on yesterday's checklist) or when the step no longer belongs to the routine.
     */
    suspend fun setRoutineStep(request: RoutineStepRequest): Boolean {
        if (request.date != today()) return false
        if (routines.getSteps(request.routineId).none { it.id == request.stepId }) return false
        routines.setStepChecked(request.stepId, request.routineId, request.date, request.checked)
        return true
    }

    companion object {
        fun from(context: Context): WidgetActionHandler {
            val c = WidgetData.container(context)
            return WidgetActionHandler(c.tasks, c.shopping, c.routines)
        }
    }
}

/** Check-off button on a Today row. */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val request = CompleteTaskRequest.from(parameters)
        if (request != null) WidgetActionHandler.from(context).completeTask(request)
        // Redraw even when nothing changed: the tap came from an out-of-date widget.
        WidgetData.container(context).widgets.refresh(WidgetCatalog.TASK_KINDS)
    }
}

/** Check-off button on a shopping item. */
class CheckShoppingItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        ShoppingCheckRequest.from(parameters)?.let { WidgetActionHandler.from(context).setShoppingItem(it) }
        WidgetData.container(context).widgets.refresh(setOf(WidgetKind.SHOPPING))
    }
}

/** Tap on a routine step. */
class ToggleRoutineStepAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RoutineStepRequest.from(parameters)?.let { WidgetActionHandler.from(context).setRoutineStep(it) }
        WidgetData.container(context).widgets.refresh(setOf(WidgetKind.ROUTINE))
    }
}
