package com.riyadmondol2006.taskfern.reminders

import com.riyadmondol2006.taskfern.domain.LogicalDay
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.riyadmondol2006.taskfern.TaskfernApp
import com.riyadmondol2006.taskfern.data.repo.RoutineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Runs [block] off the main thread while keeping the receiver alive (goAsync gives us ~10 s). */
internal fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Fires when a reminder alarm goes off. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TaskfernApp
        val c = app.container
        runAsync {
            when (intent.action) {
                ACTION_TASK -> {
                    val id = intent.getLongExtra(EXTRA_ID, -1)
                    val task = c.tasks.get(id) ?: return@runAsync
                    val now = System.currentTimeMillis()
                    val expected = task.nextReminderAt
                    // Stale or early alarm (e.g. the task was edited after the alarm was set): just re-arm.
                    val due = expected != null && expected <= now + EARLY_TOLERANCE_MS
                    val live = !task.isCompleted && task.deletedAt == null && !task.archived && task.recurrence?.paused != true
                    if (due && live) {
                        Notifications.showTaskReminder(context, task)
                        if (task.snoozeUntil != null && task.snoozeUntil <= now + EARLY_TOLERANCE_MS) c.tasks.clearSnooze(id)
                    }
                    c.reminders.rescheduleTasks(listOf(id))
                }
                ACTION_ROUTINE -> {
                    val id = intent.getLongExtra(EXTRA_ID, -1)
                    val routine = c.routines.getRoutine(id) ?: return@runAsync
                    val today = LogicalDay.today()
                    if (!routine.paused && RoutineRepository.runsOn(routine, today)) {
                        val steps = c.routines.getStepsWithChecks(id, today)
                        val left = steps.count { !it.checked }
                        if (left > 0) Notifications.showRoutineReminder(context, routine, left)
                    }
                    c.reminders.rescheduleRoutines(listOf(id))
                }
                ACTION_FOCUS -> c.focus.onTimerAlarm()
            }
        }
    }

    companion object {
        const val ACTION_TASK = "com.riyadmondol2006.taskfern.action.TASK_REMINDER"
        const val ACTION_ROUTINE = "com.riyadmondol2006.taskfern.action.ROUTINE_REMINDER"
        const val ACTION_FOCUS = "com.riyadmondol2006.taskfern.action.FOCUS_DONE"
        const val EXTRA_ID = "id"
        private const val EARLY_TOLERANCE_MS = 60_000L
    }
}

/** Buttons on a task reminder: complete, snooze, move to tomorrow. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = (context.applicationContext as TaskfernApp).container
        val id = intent.getLongExtra(EXTRA_TASK_ID, -1)
        val expected = intent.getLongExtra(EXTRA_EXPECTED_DUE, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)
        Notifications.cancelTask(context, id)
        runAsync {
            when (intent.action) {
                // expectedDue makes a second tap (or a stale notification) unable to complete the next occurrence.
                ACTION_COMPLETE -> c.tasks.complete(id, expectedDue = expected, checkExpected = true)
                ACTION_SNOOZE -> c.tasks.snooze(id, System.currentTimeMillis() + 15 * 60_000L)
                ACTION_TOMORROW -> {
                    val task = c.tasks.get(id) ?: return@runAsync
                    if (task.dueDate == expected) c.tasks.setDueDate(listOf(id), LogicalDay.today().plusDays(1))
                }
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "complete"
        const val ACTION_SNOOZE = "snooze"
        const val ACTION_TOMORROW = "tomorrow"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_EXPECTED_DUE = "expected_due"
    }
}

/**
 * Re-arms everything when alarms were lost or moved: reboot, app update, clock or time-zone change, and when the user
 * grants or revokes the exact-alarm permission.
 */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED) return
        val c = (context.applicationContext as TaskfernApp).container
        runAsync {
            val catchUp = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED
            c.reminders.rescheduleAll(catchUp = catchUp)
            c.scheduleDailyWork()
            c.widgets.refreshAll()
        }
    }

    companion object {
        private val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
