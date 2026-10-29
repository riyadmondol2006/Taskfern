package com.riyadmondol2006.taskfern

import android.app.Application
import com.riyadmondol2006.taskfern.reminders.Notifications
import kotlinx.coroutines.launch

class TaskfernApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        // Startup housekeeping off the main thread: built-in templates once, alarms re-armed (cheap, and it repairs
        // anything lost to a force-stop), and the daily jobs enqueued.
        container.appScope.launch {
            val settings = container.settings.current()
            if (!settings.templatesSeeded) {
                container.templates.seedBuiltins()
                container.settings.setTemplatesSeeded(true)
            }
            container.reminders.rescheduleAll(catchUp = false)
            container.scheduleDailyWork()
            // Widgets redraw with the current day and theme; also starts the updater that follows theme changes.
            container.widgets.refreshAll()
        }
    }
}
