package com.riyadmondol2006.taskfern.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.WidgetKind
import com.riyadmondol2006.taskfern.ui.common.Formatters
import java.time.LocalDate

/** Today's tasks: overdue + due today, with check-off, progress and Quick Add. */
class TodayWidget : TaskfernWidget<TodayModel>(
    WidgetKind.TODAY,
    setOf(
        DpSize(120.dp, 100.dp), DpSize(220.dp, 100.dp),
        DpSize(160.dp, 150.dp), DpSize(250.dp, 150.dp),
        DpSize(250.dp, 260.dp), DpSize(320.dp, 340.dp),
    ),
) {
    override suspend fun load(context: Context, appWidgetId: Int): TodayModel = WidgetData.today(context)

    @Composable
    override fun Content(data: TodayModel, appWidgetId: Int) {
        val size = LocalSize.current
        when {
            size.height < 140.dp || size.width < 150.dp -> TodayCompact(data, appWidgetId)
            else -> TodayList(data, appWidgetId, detailed = size.height >= 260.dp)
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

private const val MORE_ITEM_ID = -2L

@Composable
private fun TodayCompact(model: TodayModel, appWidgetId: Int) {
    val context = LocalContext.current
    val openToday = actionStartActivity(WidgetIntents.today(context))
    WidgetRoot(modifier = GlanceModifier.clickable(openToday), padding = 10.dp) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Formatters.shortDate(model.today, model.today),
                style = WidgetTextStyles.label(),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            RoundIconButton(
                icon = R.drawable.widget_ic_add,
                description = context.getString(R.string.widget_add_task_today),
                action = actionStartActivity(WidgetIntents.quickAdd(context, null, QuickAddDue.TODAY, "today-$appWidgetId")),
                size = 32.dp,
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        if (model.allClear) {
            Text(context.getString(R.string.widget_all_clear_short), style = WidgetTextStyles.title(), maxLines = 1)
            if (model.completedToday > 0) {
                Text(
                    context.resources.getQuantityString(R.plurals.widget_done_today, model.completedToday, model.completedToday),
                    style = WidgetTextStyles.meta(),
                    maxLines = 1,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.remaining.toString(), style = WidgetTextStyles.bigNumber(), maxLines = 1)
                Spacer(GlanceModifier.width(6.dp))
                Column {
                    Text(
                        context.resources.getQuantityString(R.plurals.widget_left_label, model.remaining),
                        style = WidgetTextStyles.meta(),
                        maxLines = 1,
                    )
                    if (model.overdueCount > 0) {
                        Text(
                            context.resources.getQuantityString(R.plurals.widget_overdue_count, model.overdueCount, model.overdueCount),
                            style = WidgetTextStyles.label(GlanceTheme.colors.error),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        ProgressBar(model.progress, GlanceModifier.fillMaxWidth().semantics { contentDescription = progressDescription(context, model) })
    }
}

@Composable
private fun TodayList(model: TodayModel, appWidgetId: Int, detailed: Boolean) {
    val context = LocalContext.current
    val openToday = actionStartActivity(WidgetIntents.today(context))
    WidgetRoot {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight().clickable(openToday)) {
                Text(context.getString(R.string.widget_today_title), style = WidgetTextStyles.title(), maxLines = 1)
                Text(todaySubtitle(context, model), style = WidgetTextStyles.meta(), maxLines = 1)
            }
            if (detailed) {
                ChipButton(context.getString(R.string.widget_open_today), openToday)
                Spacer(GlanceModifier.width(8.dp))
            }
            RoundIconButton(
                icon = R.drawable.widget_ic_add,
                description = context.getString(R.string.widget_add_task_today),
                action = actionStartActivity(WidgetIntents.quickAdd(context, null, QuickAddDue.TODAY, "today-$appWidgetId")),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth().semantics { contentDescription = progressDescription(context, model) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressBar(model.progress, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            Text(
                context.getString(R.string.widget_progress_fraction, model.completedToday, model.completedToday + model.remaining),
                style = WidgetTextStyles.meta(),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        if (model.allClear) {
            EmptyState(
                title = context.getString(R.string.widget_all_clear),
                body = context.getString(R.string.widget_all_clear_body),
                modifier = GlanceModifier.defaultWeight(),
            )
        } else {
            LazyColumn(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(model.tasks, itemId = { it.id }) { task -> TodayTaskRow(task, model.today, detailed) }
                if (model.hiddenCount > 0) {
                    item(itemId = MORE_ITEM_ID) {
                        Text(
                            context.resources.getQuantityString(R.plurals.widget_more_in_app, model.hiddenCount, model.hiddenCount),
                            style = WidgetTextStyles.label(),
                            modifier = GlanceModifier.fillMaxWidth().padding(8.dp).clickable(openToday),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayTaskRow(task: WidgetTask, today: LocalDate, detailed: Boolean) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    CardRow(onClick = actionStartActivity(WidgetIntents.task(context, task.id))) {
        CheckControl(
            checked = false,
            description = context.getString(R.string.widget_complete_task, task.title),
            action = actionRunCallback<CompleteTaskAction>(CompleteTaskRequest(task.id, task.dueDate).toParameters()),
            tint = if (detailed && task.overdue) colors.error else colors.primary,
        )
        Column(modifier = GlanceModifier.defaultWeight().padding(vertical = 8.dp)) {
            Text(task.title, style = WidgetTextStyles.body(), maxLines = if (detailed) 2 else 1)
            if (detailed) {
                val meta = taskMeta(context, task, today)
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = WidgetTextStyles.meta(if (task.overdue) colors.error else colors.onSurfaceVariant),
                        maxLines = 1,
                    )
                }
            }
        }
        if (detailed && task.priority > 0) {
            Spacer(GlanceModifier.width(6.dp))
            PriorityFlag(task.priority)
        }
    }
}

/** "Overdue · Yesterday · Work", "9:30 AM · Work" or "". */
internal fun taskMeta(context: Context, task: WidgetTask, today: LocalDate, showDay: Boolean = true): String {
    val parts = buildList {
        if (task.overdue) {
            add(context.getString(R.string.widget_overdue_since, Formatters.relativeDay(context, task.dueDate, today)))
        } else if (showDay && task.dueDate != today) {
            add(Formatters.relativeDay(context, task.dueDate, today))
        }
        task.dueTime?.let { add(Formatters.time(context, it)) }
        task.listName?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    return parts.joinToString(" · ")
}

private fun todaySubtitle(context: Context, model: TodayModel): String = buildList {
    add(Formatters.shortDate(model.today, model.today))
    if (model.remaining > 0) add(context.resources.getQuantityString(R.plurals.widget_tasks_left, model.remaining, model.remaining))
    if (model.overdueCount > 0) add(context.resources.getQuantityString(R.plurals.widget_overdue_count, model.overdueCount, model.overdueCount))
}.joinToString(" · ")

private fun progressDescription(context: Context, model: TodayModel): String =
    (model.completedToday + model.remaining).let { total ->
        context.resources.getQuantityString(R.plurals.widget_progress_description, total, model.completedToday, total)
    }
