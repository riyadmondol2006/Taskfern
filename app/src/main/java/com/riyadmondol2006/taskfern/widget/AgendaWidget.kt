package com.riyadmondol2006.taskfern.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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

/** Tasks for today and the next six days, grouped by day, with an overdue badge. */
class AgendaWidget : TaskfernWidget<AgendaModel>(WidgetKind.AGENDA) {
    override suspend fun load(context: Context, appWidgetId: Int): AgendaModel = WidgetData.agenda(context)

    @Composable
    override fun Content(data: AgendaModel, appWidgetId: Int) = AgendaContent(data)
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}

@Composable
private fun AgendaContent(model: AgendaModel) {
    val context = LocalContext.current
    val short = LocalSize.current.height < 150.dp
    val openCalendar = actionStartActivity(WidgetIntents.calendar(context))
    WidgetRoot(padding = if (short) 10.dp else 12.dp) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight().clickable(openCalendar)) {
                Text(context.getString(R.string.widget_agenda_title), style = WidgetTextStyles.title(), maxLines = 1)
                if (!short) Text(Formatters.longDate(model.today), style = WidgetTextStyles.meta(), maxLines = 1)
            }
            if (model.overdueCount > 0) {
                val overdue = context.resources.getQuantityString(R.plurals.widget_overdue_count, model.overdueCount, model.overdueCount)
                ChipButton(
                    text = overdue,
                    action = actionStartActivity(WidgetIntents.today(context)),
                    emphasis = true,
                    modifier = GlanceModifier.semantics { contentDescription = context.getString(R.string.widget_overdue_open, overdue) },
                )
                Spacer(GlanceModifier.width(8.dp))
            }
            RoundIconButton(
                icon = R.drawable.widget_ic_calendar,
                description = context.getString(R.string.widget_open_calendar),
                action = openCalendar,
                filled = false,
                size = if (short) 32.dp else 40.dp,
            )
        }
        Spacer(GlanceModifier.height(if (short) 4.dp else 8.dp))
        if (model.isEmpty) {
            EmptyState(
                title = context.getString(R.string.widget_agenda_empty),
                body = if (short) null else context.getString(R.string.widget_agenda_empty_body),
                icon = R.drawable.widget_ic_calendar,
                modifier = GlanceModifier.defaultWeight(),
            )
        } else {
            LazyColumn(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(WidgetModels.agendaEntries(model), itemId = { it.stableId }) { entry ->
                    when (entry) {
                        is AgendaEntry.DayHeader -> DayHeader(entry, model.today)
                        is AgendaEntry.NothingDue -> Text(
                            context.getString(R.string.widget_nothing_due),
                            style = WidgetTextStyles.meta(),
                            modifier = GlanceModifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp),
                        )
                        is AgendaEntry.Task -> AgendaTaskRow(entry.task, model.today)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(header: AgendaEntry.DayHeader, today: LocalDate) {
    val context = LocalContext.current
    val day = if (header.isToday || header.date == today.plusDays(1)) {
        "${Formatters.relativeDay(context, header.date, today)} · ${Formatters.shortDate(header.date, today)}"
    } else {
        Formatters.shortDate(header.date, today)
    }
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            day,
            style = WidgetTextStyles.label(if (header.isToday) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (header.count > 0) Text(header.count.toString(), style = WidgetTextStyles.meta(), maxLines = 1)
    }
}

@Composable
private fun AgendaTaskRow(task: WidgetTask, today: LocalDate) {
    val context = LocalContext.current
    CardRow(onClick = actionStartActivity(WidgetIntents.task(context, task.id)), modifier = GlanceModifier.padding(start = 12.dp)) {
        Column(modifier = GlanceModifier.defaultWeight().padding(vertical = 8.dp)) {
            Text(task.title, style = WidgetTextStyles.body(), maxLines = 1)
            // The day is in the header, so the row only adds time and list.
            val meta = taskMeta(context, task, today, showDay = false)
            if (meta.isNotEmpty()) Text(meta, style = WidgetTextStyles.meta(), maxLines = 1)
        }
        if (task.priority > 0) {
            Spacer(GlanceModifier.width(6.dp))
            PriorityFlag(task.priority)
        }
    }
}
