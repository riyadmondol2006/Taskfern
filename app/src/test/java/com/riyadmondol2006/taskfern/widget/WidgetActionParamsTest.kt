package com.riyadmondol2006.taskfern.widget

import androidx.glance.action.actionParametersOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WidgetActionParamsTest {
    private val day = LocalDate.of(2026, 10, 29)

    @Test
    fun completeTaskRoundTrips() {
        val request = CompleteTaskRequest(taskId = 42, expectedDue = day)
        assertEquals(request, CompleteTaskRequest.from(request.toParameters()))
    }

    @Test
    fun completeTaskNeedsAnIdAndTheExpectedDueDate() {
        assertNull(CompleteTaskRequest.from(actionParametersOf()))
        assertNull(CompleteTaskRequest.from(actionParametersOf(WidgetParams.TaskId to 42L)))
        assertNull(CompleteTaskRequest.from(actionParametersOf(WidgetParams.ExpectedDue to day.toEpochDay())))
        assertNull(CompleteTaskRequest.from(actionParametersOf(WidgetParams.TaskId to 0L, WidgetParams.ExpectedDue to 1L)))
        assertNull(
            CompleteTaskRequest.from(actionParametersOf(WidgetParams.TaskId to 1L, WidgetParams.ExpectedDue to Long.MAX_VALUE)),
        )
    }

    @Test
    fun shoppingCheckRoundTripsBothStates() {
        for (checked in listOf(true, false)) {
            val request = ShoppingCheckRequest(itemId = 7, checked = checked)
            assertEquals(request, ShoppingCheckRequest.from(request.toParameters()))
        }
        assertNull(ShoppingCheckRequest.from(actionParametersOf(WidgetParams.ItemId to 7L)))
        assertNull(ShoppingCheckRequest.from(actionParametersOf(WidgetParams.ItemId to -1L, WidgetParams.Checked to true)))
    }

    @Test
    fun routineStepRoundTripsAndRejectsPartialParameters() {
        val request = RoutineStepRequest(stepId = 3, routineId = 9, date = day, checked = true)
        assertEquals(request, RoutineStepRequest.from(request.toParameters()))

        assertNull(
            RoutineStepRequest.from(
                actionParametersOf(WidgetParams.StepId to 3L, WidgetParams.Day to day.toEpochDay(), WidgetParams.Checked to true),
            ),
        )
        assertNull(
            RoutineStepRequest.from(
                actionParametersOf(WidgetParams.StepId to 3L, WidgetParams.RoutineId to 9L, WidgetParams.Checked to true),
            ),
        )
    }
}
