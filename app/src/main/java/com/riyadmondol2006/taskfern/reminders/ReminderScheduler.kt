package com.riyadmondol2006.taskfern.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import com.riyadmondol2006.taskfern.data.prefs.SettingsRepository
import com.riyadmondol2006.taskfern.data.repo.RoutineRepository
import com.riyadmondol2006.taskfern.data.repo.TaskRepository
import com.riyadmondol2006.taskfern.domain.ReminderCalculator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Keeps exactly one alarm per task (and per routine) armed for its next reminder.
 *
 * - The next reminder instant is also stored on the row (`next_reminder_at`), which lets the receiver ignore stale
 *   alarms and lets a reboot spot reminders that came due while the phone was off.
 * - Alarms are exact (`setExactAndAllowWhileIdle`) when Android allows it. Without the "Alarms & reminders" permission
 *   they fall back to `setAndAllowWhileIdle`, which Android may deliver some minutes late in Doze. The app says so in
 *   Settings instead of promising exact delivery.
 * - Each alarm's PendingIntent is keyed by a unique data URI, so re-arming replaces the old alarm instead of adding a
 *   duplicate.
 */
class ReminderScheduler(
    private val context: Context,
    private val tasks: TaskRepository,
    private val routines: RoutineRepository,
    private val settings: SettingsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Re-arms the given tasks (after an edit, completion, deletion, snooze…). */
    suspend fun rescheduleTasks(ids: Collection<Long>) = mutex.withLock {
        val defaultTime = settings.current().defaultReminderTime
        ids.distinct().forEach { id -> armTask(id, defaultTime) }
    }

    /**
     * Rebuilds every alarm from the database: after boot, app update, time or zone change, and backup restore.
     * With [catchUp], reminders that came due while alarms could not fire (phone off) are shown now, at most a day late.
     */
    suspend fun rescheduleAll(catchUp: Boolean) = mutex.withLock {
        val defaultTime = settings.current().defaultReminderTime
        val now = clock.instant()
        val candidates = tasks.tasksNeedingAlarms()
        val candidateIds = candidates.map { it.id }.toSet()
        // Cancel alarms of tasks that no longer need one.
        tasks.idsWithScheduledAlarm().filterNot { it in candidateIds }.forEach { id ->
            cancel(taskIntent(id))
            tasks.setNextReminderAt(id, null)
        }
        candidates.forEach { task ->
            val pending = task.nextReminderAt
            if (catchUp && pending != null && pending <= now.toEpochMilli() && now.toEpochMilli() - pending < MISSED_WINDOW_MS) {
                Notifications.showTaskReminder(context, task)
            }
            armTask(task.id, defaultTime)
        }
        routines.getRoutines().forEach { armRoutine(it.id) }
    }

    suspend fun rescheduleRoutines(ids: Collection<Long>) = mutex.withLock {
        ids.distinct().forEach { armRoutine(it) }
    }

    private suspend fun armTask(id: Long, defaultTime: java.time.LocalTime) {
        val task = tasks.get(id)
        val intent = taskIntent(id)
        val paused = task?.recurrence?.paused == true
        if (task == null || task.isCompleted || task.deletedAt != null || task.archived || paused) {
            cancel(intent)
            if (task != null && task.nextReminderAt != null) tasks.setNextReminderAt(id, null)
            if (task == null || task.isCompleted || task.deletedAt != null) Notifications.cancelTask(context, id)
            return
        }
        val next = ReminderCalculator.nextReminder(
            date = task.dueDate,
            time = task.dueTime,
            offsetsMinutes = tasks.reminderOffsets(id),
            snoozeUntil = task.snoozeUntil?.let(Instant::ofEpochMilli),
            defaultTime = defaultTime,
            now = clock.instant(),
            zone = ZoneId.systemDefault(),
        )
        if (next == null) {
            cancel(intent)
            if (task.nextReminderAt != null) tasks.setNextReminderAt(id, null)
        } else {
            set(next.toEpochMilli(), intent)
            if (task.nextReminderAt != next.toEpochMilli()) tasks.setNextReminderAt(id, next.toEpochMilli())
        }
    }

    private suspend fun armRoutine(id: Long) {
        val routine = routines.getRoutine(id)
        val intent = routineIntent(id)
        val minutes = routine?.timeMinutes
        if (routine == null || routine.paused || minutes == null) {
            cancel(intent)
            if (routine?.nextReminderAt != null) routines.setNextReminderAt(id, null)
            return
        }
        val next = ReminderCalculator.nextRoutineReminder(routine.weekdays, minutes, clock.instant(), ZoneId.systemDefault())
        if (next == null) {
            cancel(intent)
            routines.setNextReminderAt(id, null)
        } else {
            set(next.toEpochMilli(), intent)
            routines.setNextReminderAt(id, next.toEpochMilli())
        }
    }

    fun scheduleFocusEnd(atMillis: Long) = set(atMillis, focusIntent())
    fun cancelFocusEnd() = cancel(focusIntent())

    private fun set(atMillis: Long, operation: PendingIntent) {
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
            }
        } catch (_: SecurityException) {
            // The exact-alarm permission was revoked between the check and the call.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        }
    }

    private fun cancel(operation: PendingIntent) = alarmManager.cancel(operation)

    private fun taskIntent(taskId: Long) = broadcast(ReminderReceiver.ACTION_TASK, "taskfern://alarm/task/$taskId") {
        putExtra(ReminderReceiver.EXTRA_ID, taskId)
    }

    private fun routineIntent(routineId: Long) = broadcast(ReminderReceiver.ACTION_ROUTINE, "taskfern://alarm/routine/$routineId") {
        putExtra(ReminderReceiver.EXTRA_ID, routineId)
    }

    private fun focusIntent() = broadcast(ReminderReceiver.ACTION_FOCUS, "taskfern://alarm/focus") {}

    private inline fun broadcast(action: String, uri: String, extras: Intent.() -> Unit): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(action).setData(uri.toUri()).apply(extras)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        /** Reminders missed by more than this (phone off for days) are not replayed after boot. */
        const val MISSED_WINDOW_MS = 24L * 60 * 60 * 1000
    }
}
