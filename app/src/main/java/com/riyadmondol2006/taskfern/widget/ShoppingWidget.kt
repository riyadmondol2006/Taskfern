package com.riyadmondol2006.taskfern.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.WidgetKind
import com.riyadmondol2006.taskfern.widget.config.ShoppingWidgetConfigActivity

/** One shopping list (chosen per instance) with check-off buttons. */
class ShoppingWidget : TaskfernWidget<ShoppingModel>(
    WidgetKind.SHOPPING,
    setOf(DpSize(110.dp, 110.dp), DpSize(180.dp, 180.dp), DpSize(250.dp, 250.dp)),
) {
    override suspend fun load(context: Context, appWidgetId: Int): ShoppingModel = WidgetData.shopping(context, appWidgetId)

    @Composable
    override fun Content(data: ShoppingModel, appWidgetId: Int) {
        when (data) {
            ShoppingModel.NoLists -> NoShoppingLists()
            is ShoppingModel.Showing -> ShoppingList(data, appWidgetId, compact = LocalSize.current.height < 150.dp)
        }
    }
}

class ShoppingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShoppingWidget()
}


@Composable
private fun NoShoppingLists() {
    val context = LocalContext.current
    WidgetRoot {
        EmptyState(
            title = context.getString(R.string.widget_shopping_no_lists),
            body = context.getString(R.string.widget_shopping_no_lists_body),
            icon = R.drawable.widget_ic_cart,
            modifier = GlanceModifier.defaultWeight(),
            action = { ChipButton(context.getString(R.string.widget_open_shopping), actionStartActivity(WidgetIntents.shoppingHome(context))) },
        )
    }
}

@Composable
private fun ShoppingList(model: ShoppingModel.Showing, appWidgetId: Int, compact: Boolean) {
    val context = LocalContext.current
    val openList = actionStartActivity(WidgetIntents.shoppingList(context, model.listId))
    val remaining = if (model.remaining == 0) {
        context.getString(R.string.widget_shopping_done)
    } else {
        context.resources.getQuantityString(R.plurals.widget_items_left, model.remaining, model.remaining)
    }
    WidgetRoot(modifier = if (compact) GlanceModifier.clickable(openList) else GlanceModifier) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.widget_ic_cart),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight().clickable(openList)) {
                Text(model.listName, style = WidgetTextStyles.title(), maxLines = 1)
                Text(remaining, style = WidgetTextStyles.meta(), maxLines = 1)
            }
            RoundIconButton(
                icon = R.drawable.widget_ic_add,
                description = context.getString(R.string.widget_shopping_add, model.listName),
                action = openList,
                size = if (compact) 32.dp else 40.dp,
            )
        }
        if (model.configuredMissing) {
            Spacer(GlanceModifier.height(6.dp))
            NoticeBanner(
                text = context.getString(R.string.widget_list_deleted),
                action = actionStartActivity(WidgetIntents.configure(context, ShoppingWidgetConfigActivity::class.java, appWidgetId)),
            )
        }
        if (compact) {
            // Too small for a list: the first item, if any, as a hint of what is next.
            model.items.firstOrNull()?.let { first ->
                Spacer(GlanceModifier.defaultWeight())
                Text(first.name, style = WidgetTextStyles.body(), maxLines = 1)
            }
            return@WidgetRoot
        }
        Spacer(GlanceModifier.height(8.dp))
        if (model.items.isEmpty()) {
            EmptyState(
                title = context.getString(R.string.widget_shopping_done),
                body = context.getString(R.string.widget_shopping_done_body),
                icon = R.drawable.widget_ic_cart,
                modifier = GlanceModifier.defaultWeight(),
            )
        } else {
            FittingRows(
                items = model.items,
                alreadyHidden = model.hiddenCount,
                rowHeight = 48.dp,
                reservedHeight = 80.dp,
                moreLabel = { n -> context.resources.getQuantityString(R.plurals.widget_more_in_app, n, n) },
                modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
            ) { item -> ShoppingItemRow(item) }
        }
    }
}

@Composable
private fun ShoppingItemRow(item: WidgetShoppingItem) {
    val context = LocalContext.current
    // Callbacks only inside the list (see TodayWidget): the header and the add button open the list in the app.
    CardRow(onClick = null) {
        CheckControl(
            checked = false,
            description = context.getString(R.string.widget_shopping_check, item.name),
            action = actionRunCallback<CheckShoppingItemAction>(ShoppingCheckRequest(item.id, checked = true).toParameters()),
        )
        Text(item.name, style = WidgetTextStyles.body(), maxLines = 1, modifier = GlanceModifier.defaultWeight().padding(vertical = 10.dp))
        if (item.detail.isNotEmpty()) {
            Spacer(GlanceModifier.width(8.dp))
            Text(item.detail, style = WidgetTextStyles.meta(), maxLines = 1)
        }
    }
}
