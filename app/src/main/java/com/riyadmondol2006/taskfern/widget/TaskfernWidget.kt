package com.riyadmondol2006.taskfern.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent

/**
 * Shared shape of every Taskfern widget: load once per update in [provideGlance], render with the app's theme, keep a
 * live session current through [WidgetSignals], and forget per-instance configuration when the widget is removed.
 */
abstract class TaskfernWidget<T>(private val kind: String) : GlanceAppWidget() {

    /**
     * Exact sizes: the layout sees the widget's real size, so lists show as many rows as truly fit (Responsive mode
     * would report the nearest smaller bucket and leave empty space). Glance re-lays out when the user resizes.
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    /** Reads everything this instance shows. Called once per update, off the main thread. */
    abstract suspend fun load(context: Context, appWidgetId: Int): T

    @Composable
    abstract fun Content(data: T, appWidgetId: Int)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initial = loadSnapshot(context, kind) { load(context, appWidgetId) }
        provideContent {
            val snapshot = rememberSnapshot(kind, initial) { load(context, appWidgetId) }
            TaskfernWidgetTheme(snapshot.look) { Content(snapshot.data, appWidgetId) }
        }
    }

    /** Called by Glance from the receiver's `onDeleted`. */
    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        WidgetData.container(context).database.widgetConfigDao().delete(listOf(appWidgetId))
    }
}
