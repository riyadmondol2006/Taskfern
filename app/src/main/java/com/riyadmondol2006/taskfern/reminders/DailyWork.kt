package com.riyadmondol2006.taskfern.reminders

import com.riyadmondol2006.taskfern.domain.LogicalDay
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.TaskfernApp
import com.riyadmondol2006.taskfern.ui.common.Formatters
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Two small once-a-day jobs, each re-enqueued for the next day when it runs. WorkManager keeps them across reboots and
 * lets Android batch them, which is fine because neither needs to-the-minute precision:
 *  - [DayRolloverWorker] just after midnight: refreshes widgets so "Today" means today, and empties old trash.
 *  - [DailyAgendaWorker] at the user's chosen time, when enabled: one quiet summary notification.
 */
object DailyWork {
    private const val ROLLOVER = "day_rollover"
    private const val AGENDA = "daily_agenda"

    fun scheduleRollover(context: Context) {
        val now = LocalDateTime.now()
        // One minute after the user's day start (midnight by default).
        val next = LogicalDay.nextStart(now).plusMinutes(1)
        val request = OneTimeWorkRequestBuilder<DayRolloverWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ROLLOVER, ExistingWorkPolicy.REPLACE, request)
    }

    fun scheduleAgenda(context: Context, enabled: Boolean, minuteOfDay: Int) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(AGENDA)
            return
        }
        val now = LocalDateTime.now()
        var next = LogicalDay.today().atTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = OneTimeWorkRequestBuilder<DailyAgendaWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniqueWork(AGENDA, ExistingWorkPolicy.REPLACE, request)
    }
}

class DayRolloverWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as TaskfernApp).container
        c.tasks.purgeTrashOlderThan(c.settings.current().trashRetentionDays)
        c.widgets.refreshAll()
        DailyWork.scheduleRollover(applicationContext)
        return Result.success()
    }
}

class DailyAgendaWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as TaskfernApp).container
        val settings = c.settings.current()
        if (settings.dailyAgendaEnabled) {
            val today = LogicalDay.today()
            val rows = c.tasks.observeDueOnOrBefore(today).first()
            val ctx = applicationContext
            if (rows.isNotEmpty()) {
                val overdue = rows.count { it.task.dueDate!!.isBefore(today) }
                val title = ctx.resources.getQuantityString(R.plurals.agenda_title, rows.size, rows.size) +
                    if (overdue > 0) " · " + ctx.resources.getQuantityString(R.plurals.agenda_overdue, overdue, overdue) else ""
                val lines = rows.map { r ->
                    val time = r.task.dueTime?.let { Formatters.time(ctx, it) + "  " } ?: ""
                    time + r.task.title
                }
                Notifications.showAgenda(ctx, title, lines)
            }
            DailyWork.scheduleAgenda(ctx, true, settings.dailyAgendaMinutes)
        }
        return Result.success()
    }
}
