package com.riyadmondol2006.taskfern.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.WidgetKind

/**
 * A launcher-style button that opens the Quick Add sheet with this instance's default list and due date. Widgets
 * cannot take text input, so typing happens in the sheet.
 */
class QuickAddWidget : TaskfernWidget<QuickAddModel>(WidgetKind.QUICK_ADD) {
    override suspend fun load(context: Context, appWidgetId: Int): QuickAddModel = WidgetData.quickAdd(context, appWidgetId)

    @Composable
    override fun Content(data: QuickAddModel, appWidgetId: Int) {
        val context = LocalContext.current
        val colors = GlanceTheme.colors
        val listName = data.listName ?: context.getString(R.string.view_inbox)
        val dueLabel = when (data.due) {
            QuickAddDue.NONE -> null
            QuickAddDue.TODAY -> context.getString(R.string.today)
            QuickAddDue.TOMORROW -> context.getString(R.string.tomorrow)
        }
        val description = if (dueLabel == null) {
            context.getString(R.string.widget_quick_add_description, listName)
        } else {
            context.getString(R.string.widget_quick_add_description_due, listName, dueLabel)
        }
        val open = actionStartActivity(WidgetIntents.quickAdd(context, data.listId, data.due, "quickadd-$appWidgetId"))
        val size = LocalSize.current
        if (size.width < 100.dp) {
            // Round button centred in its cell, whatever the cell's aspect ratio.
            val diameter = (minOf(size.width, size.height) - 4.dp).coerceAtLeast(36.dp)
            Box(
                modifier = GlanceModifier.fillMaxSize().clickable(open).semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = GlanceModifier.size(diameter).appWidgetBackground()
                        .shapeBackground(R.drawable.widget_shape_circle, colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.widget_ic_add),
                        contentDescription = null,
                        modifier = GlanceModifier.size(diameter * 0.5f),
                        colorFilter = ColorFilter.tint(colors.onPrimary),
                    )
                }
            }
            return
        }

        val base = GlanceModifier.fillMaxSize().appWidgetBackground()
        val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            base.background(colors.primary).cornerRadius(android.R.dimen.system_app_widget_background_radius)
        } else {
            base.shapeBackground(R.drawable.widget_shape_pill, colors.primary)
        }
        Row(
            modifier = background.padding(horizontal = 14.dp).clickable(open).semantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_ic_add),
                contentDescription = null,
                modifier = GlanceModifier.size(24.dp),
                colorFilter = ColorFilter.tint(colors.onPrimary),
            )
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    context.getString(R.string.widget_quick_add_title),
                    style = TextStyle(color = colors.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    listOfNotNull(listName, dueLabel).joinToString(" · "),
                    style = TextStyle(color = colors.onPrimary, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}
