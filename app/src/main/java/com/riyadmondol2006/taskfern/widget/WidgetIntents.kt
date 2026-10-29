package com.riyadmondol2006.taskfern.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.riyadmondol2006.taskfern.MainActivity
import com.riyadmondol2006.taskfern.ui.quickadd.QuickAddActivity

/** Explicit intents the widgets start. Each carries a distinct data URI so launchers never merge two of them. */
object WidgetIntents {
    private fun deepLink(context: Context, link: String): Intent =
        Intent(Intent.ACTION_VIEW, link.toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun today(context: Context) = deepLink(context, "taskfern://today")
    fun calendar(context: Context) = deepLink(context, "taskfern://calendar")
    fun task(context: Context, taskId: Long) = deepLink(context, "taskfern://task/$taskId")
    fun shoppingHome(context: Context) = deepLink(context, "taskfern://shopping")
    fun shoppingList(context: Context, listId: Long) = deepLink(context, "taskfern://shopping/$listId")
    fun routines(context: Context) = deepLink(context, "taskfern://routines")
    fun routine(context: Context, routineId: Long) = deepLink(context, "taskfern://routine/$routineId")

    /**
     * Opens the Quick Add sheet. [source] makes the intent unique per widget instance, because PendingIntents that
     * differ only in extras would otherwise be shared between widgets with different defaults.
     */
    fun quickAdd(context: Context, listId: Long?, due: QuickAddDue, source: String): Intent =
        Intent(context, QuickAddActivity::class.java)
            .setAction(QuickAddActivity.ACTION_QUICK_ADD)
            .setData("taskfern-widget://quickadd/$source/${listId ?: 0}/${due.key}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply {
                if (listId != null) putExtra(QuickAddActivity.EXTRA_LIST_ID, listId)
                due.extra?.let { putExtra(QuickAddActivity.EXTRA_DUE, it) }
            }

    /** Reopens a widget's configuration activity, e.g. after its list or routine was deleted. */
    fun configure(context: Context, activity: Class<*>, appWidgetId: Int): Intent =
        Intent(context, activity)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .setData("taskfern-widget://configure/$appWidgetId".toUri())
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
