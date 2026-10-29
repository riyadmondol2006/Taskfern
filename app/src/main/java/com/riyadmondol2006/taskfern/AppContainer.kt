package com.riyadmondol2006.taskfern

import android.content.Context
import com.riyadmondol2006.taskfern.data.db.TaskfernDatabase
import com.riyadmondol2006.taskfern.data.prefs.SettingsRepository
import com.riyadmondol2006.taskfern.data.repo.DataEvents
import com.riyadmondol2006.taskfern.data.repo.FocusRepository
import com.riyadmondol2006.taskfern.data.repo.RoutineRepository
import com.riyadmondol2006.taskfern.data.repo.ShoppingRepository
import com.riyadmondol2006.taskfern.data.repo.TaskRepository
import com.riyadmondol2006.taskfern.data.repo.TemplateRepository
import com.riyadmondol2006.taskfern.focus.FocusController
import com.riyadmondol2006.taskfern.reminders.DailyWork
import com.riyadmondol2006.taskfern.reminders.ReminderScheduler
import com.riyadmondol2006.taskfern.update.UpdateChecker
import com.riyadmondol2006.taskfern.update.UpdateController
import com.riyadmondol2006.taskfern.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.riyadmondol2006.taskfern.domain.LogicalDay

/**
 * Hand-written dependency container, one per process. The object graph is small, so plain constructor injection keeps
 * startup fast and avoids an annotation-processing framework. Widgets, receivers and workers reach it through
 * [TaskfernApp.container].
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Lives as long as the process; used for side effects that must outlive a screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: TaskfernDatabase by lazy { TaskfernDatabase.build(appContext) }
    val settings = SettingsRepository(appContext)

    private val events = object : DataEvents {
        override fun tasksChanged(taskIds: Collection<Long>) {
            appScope.launch {
                if (taskIds.isEmpty()) reminders.rescheduleAll(catchUp = false) else reminders.rescheduleTasks(taskIds)
            }
            widgets.tasksChanged()
        }

        override fun shoppingChanged(listIds: Collection<Long>) = widgets.shoppingChanged()

        override fun routinesChanged(routineIds: Collection<Long>) {
            appScope.launch {
                if (routineIds.isEmpty()) reminders.rescheduleAll(catchUp = false) else reminders.rescheduleRoutines(routineIds)
            }
            widgets.routinesChanged()
        }
    }

    val tasks: TaskRepository by lazy { TaskRepository(database, events) }
    val shopping: ShoppingRepository by lazy { ShoppingRepository(database, events) }
    val routines: RoutineRepository by lazy { RoutineRepository(database, events) }
    val templates: TemplateRepository by lazy { TemplateRepository(database, tasks, shopping, routines) }
    val focusRepository: FocusRepository by lazy { FocusRepository(database) }

    val reminders: ReminderScheduler by lazy { ReminderScheduler(appContext, tasks, routines, settings) }
    val widgets: WidgetUpdater by lazy { WidgetUpdater(appContext, appScope) }
    val focus: FocusController by lazy { FocusController(appContext, focusRepository, reminders) }

    /** Optional update check against GitHub Releases; the app's only network use, and only after the user agrees. */
    val updates: UpdateController by lazy { UpdateController(settings, UpdateChecker(), appScope) }

    /**
     * Keeps [LogicalDay] in step with the "New day starts at" setting. When it changes, the nightly job moves and
     * widgets redraw for the (possibly different) day.
     */
    fun followDayStart() {
        appScope.launch {
            settings.settings.map { it.dayStartMinutes }.distinctUntilChanged().collect { minutes ->
                val changed = LogicalDay.startMinutes != minutes
                LogicalDay.startMinutes = minutes
                if (changed) {
                    scheduleDailyWork()
                    widgets.refreshAll()
                }
            }
        }
    }

    /** (Re)enqueues the once-a-day jobs with the current settings. Cheap and idempotent. */
    suspend fun scheduleDailyWork() {
        val s = settings.current()
        DailyWork.scheduleRollover(appContext)
        DailyWork.scheduleAgenda(appContext, s.dailyAgendaEnabled, s.dailyAgendaMinutes)
    }
}
