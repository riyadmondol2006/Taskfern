package com.riyadmondol2006.taskfern.widget

import android.content.Context
import com.riyadmondol2006.taskfern.AppContainer
import com.riyadmondol2006.taskfern.TaskfernApp
import com.riyadmondol2006.taskfern.data.db.WidgetConfigEntity
import com.riyadmondol2006.taskfern.data.db.WidgetKind
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/**
 * One-shot reads for each widget: a handful of small queries per update, straight from Room. Reading a Room Flow with
 * `first()` returns the current result and closes the query immediately.
 */
internal object WidgetData {
    fun container(context: Context): AppContainer = (context.applicationContext as TaskfernApp).container

    /** This instance's configuration, ignoring a row left behind by a widget of another kind with a recycled id. */
    suspend fun config(context: Context, appWidgetId: Int, kind: String): WidgetConfigEntity? =
        container(context).database.widgetConfigDao().get(appWidgetId)?.takeIf { it.kind == kind }

    suspend fun today(context: Context, today: LocalDate = LocalDate.now()): TodayModel {
        val tasks = container(context).tasks
        val rows = tasks.observeDueOnOrBefore(today).first()
        val midnight = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val completed = tasks.observeCompletedSince(midnight).first().size
        return WidgetModels.today(rows, completed, today)
    }

    suspend fun agenda(context: Context, today: LocalDate = LocalDate.now()): AgendaModel {
        val tasks = container(context).tasks
        val last = today.plusDays((WidgetModels.AGENDA_DAYS - 1).toLong())
        val rows = tasks.observeDueBetween(today, last).first()
        val overdue = tasks.observeOverdueCount(today).first()
        return WidgetModels.agenda(rows, overdue, today)
    }

    suspend fun quickAdd(context: Context, appWidgetId: Int): QuickAddModel {
        val config = config(context, appWidgetId, WidgetKind.QUICK_ADD)
        val due = QuickAddDue.fromKey(config?.option)
        // A deleted list falls back to the Inbox, so the sheet never receives a list id that no longer exists.
        val list = config?.targetId?.let { container(context).tasks.getList(it) }
        return QuickAddModel(listId = list?.id, listName = list?.name, due = due)
    }

    suspend fun shopping(context: Context, appWidgetId: Int): ShoppingModel {
        val shopping = container(context).shopping
        val dao = container(context).database.shoppingDao()
        val lists = shopping.getLists()
        val configured = config(context, appWidgetId, WidgetKind.SHOPPING)?.targetId
        val target = WidgetModels.resolveTarget(configured, lists.map { it.id })
        val list = lists.firstOrNull { it.id == target.id } ?: return ShoppingModel.NoLists
        val items = dao.getUncheckedItems(list.id, WidgetModels.SHOPPING_LIMIT).map(WidgetModels::shoppingItem)
        return ShoppingModel.Showing(
            listId = list.id,
            listName = list.name,
            remaining = dao.remainingCount(list.id),
            items = items,
            configuredMissing = target.configuredMissing,
        )
    }

    suspend fun routine(context: Context, appWidgetId: Int, today: LocalDate = LocalDate.now()): RoutineModel {
        val routines = container(context).routines
        val all = routines.getRoutines()
        val configured = config(context, appWidgetId, WidgetKind.ROUTINE)?.targetId
        val target = WidgetModels.resolveTarget(configured, all.map { it.id })
        val routine = all.firstOrNull { it.id == target.id } ?: return RoutineModel.NoRoutines
        val steps = routines.getStepsWithChecks(routine.id, today)
        return WidgetModels.routine(routine, steps, today, target.configuredMissing)
    }
}
