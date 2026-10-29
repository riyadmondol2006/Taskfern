package com.riyadmondol2006.taskfern.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.WidgetKind
import com.riyadmondol2006.taskfern.ui.common.Formatters
import com.riyadmondol2006.taskfern.widget.config.RoutineWidgetConfigActivity

/** Today's checklist of one routine (chosen per instance). */
class RoutineWidget : TaskfernWidget<RoutineModel>(
    WidgetKind.ROUTINE,
    setOf(DpSize(110.dp, 110.dp), DpSize(180.dp, 180.dp), DpSize(250.dp, 250.dp)),
) {
    override suspend fun load(context: Context, appWidgetId: Int): RoutineModel = WidgetData.routine(context, appWidgetId)

    @Composable
    override fun Content(data: RoutineModel, appWidgetId: Int) {
        when (data) {
            RoutineModel.NoRoutines -> NoRoutines()
            is RoutineModel.Showing -> RoutineChecklist(data, appWidgetId, compact = LocalSize.current.height < 150.dp)
        }
    }
}

class RoutineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RoutineWidget()
}

@Composable
private fun NoRoutines() {
    val context = LocalContext.current
    WidgetRoot {
        EmptyState(
            title = context.getString(R.string.widget_routine_none),
            body = context.getString(R.string.widget_routine_none_body),
            modifier = GlanceModifier.defaultWeight(),
            action = { ChipButton(context.getString(R.string.widget_open_routines), actionStartActivity(WidgetIntents.routines(context))) },
        )
    }
}

private fun statusLine(context: Context, model: RoutineModel.Showing): String = when (val status = model.status) {
    RoutineStatus.Paused -> context.getString(R.string.paused)
    is RoutineStatus.NotScheduled -> {
        val next = status.nextDay?.let { context.getString(R.string.widget_routine_next, Formatters.relativeDay(context, it, model.date)) }
        listOfNotNull(context.getString(R.string.widget_routine_not_scheduled), next).joinToString(" · ")
    }
    RoutineStatus.Active -> when {
        model.total == 0 -> context.getString(R.string.widget_routine_no_steps)
        model.allDone -> context.getString(R.string.widget_routine_all_done)
        else -> context.resources.getQuantityString(R.plurals.widget_routine_progress, model.total, model.done, model.total)
    }
}

@Composable
private fun RoutineChecklist(model: RoutineModel.Showing, appWidgetId: Int, compact: Boolean) {
    val context = LocalContext.current
    val openRoutine = actionStartActivity(WidgetIntents.routine(context, model.routineId))
    val active = model.status == RoutineStatus.Active
    WidgetRoot(modifier = if (compact) GlanceModifier.clickable(openRoutine) else GlanceModifier) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(openRoutine)
                .semantics { contentDescription = context.getString(R.string.widget_open_routine, model.name) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(model.emoji, style = TextStyle(fontSize = if (compact) 20.sp else 24.sp), maxLines = 1)
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(model.name, style = WidgetTextStyles.title(), maxLines = 1)
                Text(statusLine(context, model), style = WidgetTextStyles.meta(), maxLines = if (compact) 2 else 1)
            }
        }
        if (model.configuredMissing) {
            Spacer(GlanceModifier.height(6.dp))
            NoticeBanner(
                text = context.getString(R.string.widget_routine_deleted),
                action = actionStartActivity(WidgetIntents.configure(context, RoutineWidgetConfigActivity::class.java, appWidgetId)),
            )
        }
        if (active && model.total > 0) {
            Spacer(GlanceModifier.height(8.dp))
            ProgressBar(
                model.progress,
                GlanceModifier.fillMaxWidth().semantics {
                    contentDescription = context.resources.getQuantityString(R.plurals.widget_routine_progress, model.total, model.done, model.total)
                },
            )
        }
        if (compact) return@WidgetRoot
        Spacer(GlanceModifier.height(8.dp))
        when {
            !active -> EmptyState(
                title = statusLine(context, model),
                body = null,
                icon = R.drawable.widget_ic_today,
                modifier = GlanceModifier.defaultWeight(),
            )
            model.total == 0 -> EmptyState(
                title = context.getString(R.string.widget_routine_no_steps),
                body = null,
                icon = R.drawable.widget_ic_today,
                modifier = GlanceModifier.defaultWeight(),
                action = { ChipButton(context.getString(R.string.widget_open_routine, model.name), openRoutine) },
            )
            else -> LazyColumn(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(model.steps, itemId = { it.id }) { step -> StepRow(step, model) }
            }
        }
    }
}

@Composable
private fun StepRow(step: WidgetStep, model: RoutineModel.Showing) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    val toggle = actionRunCallback<ToggleRoutineStepAction>(
        RoutineStepRequest(step.id, model.routineId, model.date, checked = !step.checked).toParameters(),
    )
    val description = context.getString(
        if (step.checked) R.string.widget_step_uncheck else R.string.widget_step_check,
        step.title,
    )
    CardRow(onClick = toggle) {
        CheckControl(checked = step.checked, description = description, action = toggle)
        Text(
            step.title,
            style = TextStyle(
                color = if (step.checked) colors.onSurfaceVariant else colors.onSurface,
                fontSize = 14.sp,
                textDecoration = if (step.checked) TextDecoration.LineThrough else TextDecoration.None,
            ),
            maxLines = 2,
            modifier = GlanceModifier.defaultWeight().padding(vertical = 10.dp),
        )
    }
}
