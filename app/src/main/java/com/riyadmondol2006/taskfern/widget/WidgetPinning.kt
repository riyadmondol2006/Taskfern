package com.riyadmondol2006.taskfern.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.annotation.StringRes
import com.riyadmondol2006.taskfern.R

/**
 * "Add to home screen" for each widget, using the launcher's own pin dialog (AppWidgetManager.requestPinAppWidget).
 * The launcher decides placement; widgets that need a choice (shopping list, routine) open their configuration screen.
 */
object WidgetPinning {
    data class Entry(@StringRes val label: Int, @StringRes val description: Int, val receiver: Class<*>)

    val entries = listOf(
        Entry(R.string.widget_today_label, R.string.widget_today_picker_description, TodayWidgetReceiver::class.java),
        Entry(R.string.widget_quick_add_label, R.string.widget_quick_add_picker_description, QuickAddWidgetReceiver::class.java),
        Entry(R.string.widget_shopping_label, R.string.widget_shopping_picker_description, ShoppingWidgetReceiver::class.java),
        Entry(R.string.widget_agenda_label, R.string.widget_agenda_picker_description, AgendaWidgetReceiver::class.java),
        Entry(R.string.widget_routine_label, R.string.widget_routine_picker_description, RoutineWidgetReceiver::class.java),
    )

    /** False when the current launcher does not support pinning; the user can still add widgets from the launcher. */
    fun isSupported(context: Context): Boolean =
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun requestPin(context: Context, receiver: Class<*>): Boolean =
        AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, receiver), null, null)
}
