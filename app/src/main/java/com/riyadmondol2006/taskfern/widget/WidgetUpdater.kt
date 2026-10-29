package com.riyadmondol2006.taskfern.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import com.riyadmondol2006.taskfern.TaskfernApp
import com.riyadmondol2006.taskfern.data.db.WidgetKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Which Glance widget and receiver serve each [WidgetKind]. */
object WidgetCatalog {
    fun widget(kind: String): GlanceAppWidget = when (kind) {
        WidgetKind.TODAY -> TodayWidget()
        WidgetKind.QUICK_ADD -> QuickAddWidget()
        WidgetKind.SHOPPING -> ShoppingWidget()
        WidgetKind.AGENDA -> AgendaWidget()
        WidgetKind.ROUTINE -> RoutineWidget()
        else -> throw IllegalArgumentException("Unknown widget kind $kind")
    }

    fun receiver(kind: String): Class<out GlanceAppWidgetReceiver> = when (kind) {
        WidgetKind.TODAY -> TodayWidgetReceiver::class.java
        WidgetKind.QUICK_ADD -> QuickAddWidgetReceiver::class.java
        WidgetKind.SHOPPING -> ShoppingWidgetReceiver::class.java
        WidgetKind.AGENDA -> AgendaWidgetReceiver::class.java
        WidgetKind.ROUTINE -> RoutineWidgetReceiver::class.java
        else -> throw IllegalArgumentException("Unknown widget kind $kind")
    }

    /** Widget kinds that show task data (lists included: Quick Add shows the target list's name). */
    val TASK_KINDS = setOf(WidgetKind.TODAY, WidgetKind.AGENDA, WidgetKind.QUICK_ADD)
}

/**
 * Turns data-change events into Glance updates. Event driven: repositories call [tasksChanged], [shoppingChanged] and
 * [routinesChanged] after each committed write; bursts are coalesced on [scope] and only the affected widget kinds are
 * updated. [refreshAll] runs on boot, time changes and just after midnight. Nothing polls.
 */
class WidgetUpdater(private val context: Context, private val scope: CoroutineScope) {
    private val coalescer = UpdateCoalescer(scope) { kinds -> update(kinds) }

    init {
        // Widgets follow the app's theme settings; redraw them when those change while the process is alive.
        scope.launch {
            runCatching {
                val settings = (context.applicationContext as TaskfernApp).container.settings.settings
                settings.map { WidgetLook.from(it) }.distinctUntilChanged().drop(1).collect { update(WidgetSignals.ALL_KINDS) }
            }.onFailure { if (it is CancellationException) throw it else Log.w(TAG, "Theme watcher stopped", it) }
        }
    }

    fun tasksChanged() = coalescer.submit(WidgetCatalog.TASK_KINDS)

    fun shoppingChanged() = coalescer.submit(setOf(WidgetKind.SHOPPING))

    fun routinesChanged() = coalescer.submit(setOf(WidgetKind.ROUTINE))

    /** Redraws every widget now (new day, new time zone, after a reboot) and forgets configs of removed widgets. */
    suspend fun refreshAll() {
        coalescer.discard(WidgetSignals.ALL_KINDS)
        pruneConfigs()
        update(WidgetSignals.ALL_KINDS)
    }

    /** Redraws [kinds] immediately, e.g. right after a widget tap changed data, instead of waiting for the debounce. */
    suspend fun refresh(kinds: Set<String>) {
        coalescer.discard(kinds)
        update(kinds)
    }

    /** Redraws one widget instance, including one that is still being placed (its configuration was just saved). */
    suspend fun refreshInstance(kind: String, appWidgetId: Int) {
        guarded("refresh $kind/$appWidgetId") {
            WidgetSignals.bump(kind)
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            WidgetCatalog.widget(kind).update(context, glanceId)
        }
    }

    private suspend fun update(kinds: Set<String>) {
        for (kind in kinds) {
            guarded("update $kind") {
                WidgetSignals.bump(kind)
                WidgetCatalog.widget(kind).updateAll(context)
            }
        }
    }

    /** Deletes configuration rows whose widget no longer exists on any home screen. */
    private suspend fun pruneConfigs() {
        guarded("prune configs") {
            val manager = AppWidgetManager.getInstance(context) ?: return@guarded
            val live = WidgetSignals.ALL_KINDS.flatMap { kind ->
                manager.getAppWidgetIds(ComponentName(context, WidgetCatalog.receiver(kind))).toList()
            }.toSet()
            val dao = (context.applicationContext as TaskfernApp).container.database.widgetConfigDao()
            val stale = dao.all().map { it.appWidgetId }.filter { it !in live }
            if (stale.isNotEmpty()) dao.delete(stale)
        }
    }

    private suspend fun guarded(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A widget that cannot be drawn must never take the app down; Glance shows its error layout instead.
            Log.w(TAG, "Widget $what failed", e)
        }
    }

    private companion object {
        const val TAG = "TaskfernWidgets"
    }
}
