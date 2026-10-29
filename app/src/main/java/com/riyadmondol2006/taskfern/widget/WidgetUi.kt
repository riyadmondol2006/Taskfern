package com.riyadmondol2006.taskfern.widget

import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.riyadmondol2006.taskfern.R

/** What a widget renders: its data plus the theme, tagged with the [WidgetSignals] version it was loaded at. */
class WidgetSnapshot<T>(val version: Long, val look: WidgetLook, val data: T)

suspend fun <T> loadSnapshot(context: Context, kind: String, load: suspend () -> T): WidgetSnapshot<T> {
    val version = WidgetSignals.version(kind)
    return WidgetSnapshot(version, WidgetLook.load(context), load())
}

/**
 * Keeps a live Glance session current: starts from the snapshot loaded in `provideGlance` and reloads (one round of
 * small queries) whenever [WidgetUpdater] bumps this kind's version. No database Flow is held open.
 */
@Composable
fun <T> rememberSnapshot(kind: String, initial: WidgetSnapshot<T>, load: suspend () -> T): WidgetSnapshot<T> {
    val context = LocalContext.current
    val version by WidgetSignals.flow(kind).collectAsState()
    var current by remember { mutableStateOf(initial) }
    LaunchedEffect(version) {
        if (version != current.version) current = loadSnapshot(context, kind, load)
    }
    return current
}

/** Rounded, tinted background: shape drawables work the same on every Android version a widget can run on. */
fun GlanceModifier.shapeBackground(@DrawableRes shape: Int, color: ColorProvider): GlanceModifier =
    background(ImageProvider(shape), colorFilter = ColorFilter.tint(color))

/** The widget's root container with the system widget radius on Android 12+ and a rounded shape before. */
@Composable
fun WidgetRoot(
    modifier: GlanceModifier = GlanceModifier,
    padding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = GlanceTheme.colors
    val base = GlanceModifier.fillMaxSize().appWidgetBackground()
    val shaped = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        base.background(colors.widgetBackground).cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        base.shapeBackground(R.drawable.widget_shape_root, colors.widgetBackground)
    }
    Column(modifier = shaped.padding(padding).then(modifier), content = content)
}

object WidgetTextStyles {
    @Composable fun title() = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    @Composable fun body() = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp)
    @Composable fun meta(color: ColorProvider = GlanceTheme.colors.onSurfaceVariant) = TextStyle(color = color, fontSize = 12.sp)
    @Composable fun label(color: ColorProvider = GlanceTheme.colors.primary) =
        TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    @Composable fun bigNumber() = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold)
}

/** A round icon button with an accessible label. [filled] = primary colour, otherwise a quieter tonal button. */
@Composable
fun RoundIconButton(
    @DrawableRes icon: Int,
    description: String,
    action: Action,
    filled: Boolean = true,
    size: Dp = 40.dp,
) {
    val colors = GlanceTheme.colors
    val background = if (filled) colors.primary else colors.secondaryContainer
    val tint = if (filled) colors.onPrimary else colors.onSecondaryContainer
    Box(
        modifier = GlanceModifier.size(size)
            .shapeBackground(R.drawable.widget_shape_circle, background)
            .clickable(action)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            modifier = GlanceModifier.size(size * 0.5f),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

/** A small tonal text button ("Open Today", "Open Calendar"). The visible text is its accessible label. */
@Composable
fun ChipButton(text: String, action: Action, emphasis: Boolean = false, modifier: GlanceModifier = GlanceModifier) {
    val colors = GlanceTheme.colors
    val background = if (emphasis) colors.errorContainer else colors.secondaryContainer
    val content = if (emphasis) colors.onErrorContainer else colors.onSecondaryContainer
    Box(
        modifier = modifier
            .shapeBackground(R.drawable.widget_shape_pill, background)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(color = content, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

/**
 * The check-off control of a row: a 40 dp touch target with a ring (open) or filled tick (done). [description] says
 * what a tap does ("Complete Buy milk").
 */
@Composable
fun CheckControl(checked: Boolean, description: String, action: Action, tint: ColorProvider = GlanceTheme.colors.primary) {
    Box(
        modifier = GlanceModifier.size(40.dp).clickable(action).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(if (checked) R.drawable.widget_ic_check_circle else R.drawable.widget_ic_circle),
            contentDescription = null,
            modifier = GlanceModifier.size(22.dp),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

@Composable
fun PriorityFlag(priority: Int) {
    val color = LocalWidgetAccents.current.priority(priority) ?: return
    val context = LocalContext.current
    val label = when (priority) {
        3 -> R.string.widget_priority_high
        2 -> R.string.widget_priority_medium
        else -> R.string.widget_priority_low
    }
    Image(
        provider = ImageProvider(R.drawable.widget_ic_flag),
        contentDescription = context.getString(label),
        modifier = GlanceModifier.size(16.dp),
        colorFilter = ColorFilter.tint(color),
    )
}

@Composable
fun ProgressBar(progress: Float, modifier: GlanceModifier = GlanceModifier) {
    LinearProgressIndicator(
        progress = progress.coerceIn(0f, 1f),
        modifier = modifier.height(6.dp),
        color = GlanceTheme.colors.primary,
        backgroundColor = GlanceTheme.colors.surfaceVariant,
    )
}

/** A rounded card row inside a list, with a gap below it. */
@Composable
fun CardRow(
    onClick: Action?,
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable androidx.glance.layout.RowScope.() -> Unit,
) {
    Box(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
        var row = GlanceModifier.fillMaxWidth()
            .shapeBackground(R.drawable.widget_shape_card, GlanceTheme.colors.surface)
            .padding(end = 10.dp)
        if (onClick != null) row = row.clickable(onClick)
        Row(modifier = row.then(modifier), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** Centered empty state with an icon, a headline, an optional explanation and an optional action. */
@Composable
fun EmptyState(
    title: String,
    body: String?,
    modifier: GlanceModifier = GlanceModifier,
    @DrawableRes icon: Int = R.drawable.widget_ic_check_circle,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            modifier = GlanceModifier.size(32.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
        )
        if (body != null) {
            Text(
                body,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center),
                maxLines = 3,
            )
        }
        if (action != null) {
            Spacer(GlanceModifier.height(8.dp))
            action()
        }
    }
}

/** A tappable notice ("the list was deleted · tap to choose another"). */
@Composable
fun NoticeBanner(text: String, action: Action) {
    Box(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth()
                .shapeBackground(R.drawable.widget_shape_card, GlanceTheme.colors.tertiaryContainer)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clickable(action),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_ic_info),
                contentDescription = null,
                modifier = GlanceModifier.size(16.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onTertiaryContainer),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(text, style = TextStyle(color = GlanceTheme.colors.onTertiaryContainer, fontSize = 12.sp), maxLines = 2)
        }
    }
}
