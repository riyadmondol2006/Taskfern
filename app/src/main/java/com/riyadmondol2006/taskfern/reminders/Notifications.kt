package com.riyadmondol2006.taskfern.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.riyadmondol2006.taskfern.MainActivity
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.RoutineEntity
import com.riyadmondol2006.taskfern.data.db.TaskEntity
import com.riyadmondol2006.taskfern.ui.common.Formatters

/** Notification channels and builders. Every notification is plain and local; nothing leaves the device. */
object Notifications {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_ROUTINES = "routines"
    const val CHANNEL_AGENDA = "daily_agenda"
    const val CHANNEL_FOCUS = "focus"

    const val AGENDA_NOTIFICATION_ID = 1
    const val FOCUS_NOTIFICATION_ID = 2

    // Ids for task and routine notifications are derived from their database ids in separate ranges.
    fun taskNotificationId(taskId: Long): Int = (1_000_000 + (taskId % 900_000_000)).toInt()
    fun routineNotificationId(routineId: Long): Int = (100 + (routineId % 900_000)).toInt()

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_reminders_desc)
                },
                NotificationChannel(CHANNEL_ROUTINES, context.getString(R.string.channel_routines), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_routines_desc)
                },
                NotificationChannel(CHANNEL_AGENDA, context.getString(R.string.channel_agenda), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_agenda_desc)
                },
                NotificationChannel(CHANNEL_FOCUS, context.getString(R.string.channel_focus), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_focus_desc)
                },
            ),
        )
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Opens the app on a deep link such as taskfern://task/12. */
    fun openIntent(context: Context, uri: String, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, uri.toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun actionIntent(context: Context, action: String, taskId: Long, expectedDue: Long?): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .setData("taskfern://task/$taskId/$action".toUri())
            .putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
            .putExtra(NotificationActionReceiver.EXTRA_EXPECTED_DUE, expectedDue ?: Long.MIN_VALUE)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun showTaskReminder(context: Context, task: TaskEntity) {
        if (!canPost(context)) return
        val id = taskNotificationId(task.id)
        val due = task.dueDate?.let { Formatters.dueLabel(context, it, task.dueTime) }
        val text = listOfNotNull(due, task.notes.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }).joinToString(" · ")
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_taskfern)
            .setContentTitle(task.title)
            .setContentText(text.ifEmpty { context.getString(R.string.notification_reminder_fallback) })
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(due, task.notes.takeIf { it.isNotBlank() }).joinToString("\n")))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent(context, "taskfern://task/${task.id}", id))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_check, context.getString(R.string.action_complete),
                actionIntent(context, NotificationActionReceiver.ACTION_COMPLETE, task.id, task.dueDate?.toEpochDay()))
            .addAction(R.drawable.ic_snooze, context.getString(R.string.action_snooze_15),
                actionIntent(context, NotificationActionReceiver.ACTION_SNOOZE, task.id, task.dueDate?.toEpochDay()))
        if (task.recurrence == null) {
            builder.addAction(R.drawable.ic_event, context.getString(R.string.action_tomorrow),
                actionIntent(context, NotificationActionReceiver.ACTION_TOMORROW, task.id, task.dueDate?.toEpochDay()))
        }
        post(context, id, builder)
    }

    fun showRoutineReminder(context: Context, routine: RoutineEntity, stepCount: Int) {
        if (!canPost(context)) return
        val id = routineNotificationId(routine.id)
        val builder = NotificationCompat.Builder(context, CHANNEL_ROUTINES)
            .setSmallIcon(R.drawable.ic_stat_taskfern)
            .setContentTitle("${routine.emoji} ${routine.name}")
            .setContentText(context.resources.getQuantityString(R.plurals.routine_steps_count, stepCount, stepCount))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openIntent(context, "taskfern://routine/${routine.id}", id))
            .setAutoCancel(true)
        post(context, id, builder)
    }

    fun showAgenda(context: Context, title: String, lines: List<String>) {
        if (!canPost(context)) return
        val style = NotificationCompat.InboxStyle()
        lines.take(6).forEach { style.addLine(it) }
        if (lines.size > 6) style.setSummaryText(context.getString(R.string.agenda_more, lines.size - 6))
        val builder = NotificationCompat.Builder(context, CHANNEL_AGENDA)
            .setSmallIcon(R.drawable.ic_stat_taskfern)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull() ?: "")
            .setStyle(style)
            .setContentIntent(openIntent(context, "taskfern://today", AGENDA_NOTIFICATION_ID))
            .setAutoCancel(true)
        post(context, AGENDA_NOTIFICATION_ID, builder)
    }

    fun showFocusDone(context: Context, title: String, text: String) {
        if (!canPost(context)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_stat_taskfern)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent(context, "taskfern://focus", FOCUS_NOTIFICATION_ID))
            .setAutoCancel(true)
        post(context, FOCUS_NOTIFICATION_ID, builder)
    }

    fun cancelTask(context: Context, taskId: Long) = NotificationManagerCompat.from(context).cancel(taskNotificationId(taskId))

    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing else to do.
        }
    }
}
