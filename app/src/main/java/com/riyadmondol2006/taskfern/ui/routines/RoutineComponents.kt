package com.riyadmondol2006.taskfern.ui.routines

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.RoutineEntity
import com.riyadmondol2006.taskfern.ui.common.Formatters
import com.riyadmondol2006.taskfern.ui.theme.LocalTaskfernColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Today's date, re-emitted when the day changes while the screen is open. */
fun currentDate(): Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now())
        delay(30_000)
    }
}.distinctUntilChanged()

/** The day a routine was created, in the device's zone (history before it is not counted as missed). */
fun RoutineEntity.startDate(): LocalDate = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()

/** A round progress ring with [content] (usually the routine's emoji) in the middle. */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    stroke: Dp = 4.dp,
    content: @Composable () -> Unit,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(450), label = "ring")
    val track = color.copy(alpha = 0.16f)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** "Every day", "Weekdays", "Weekends" or "Mon, Wed, Fri". */
fun scheduleLabel(context: Context, mask: Int, weekStartsMonday: Boolean): String = when (WeekdayMask.preset(mask)) {
    WeekdayMask.Preset.EVERY_DAY -> context.getString(R.string.routines_every_day)
    WeekdayMask.Preset.WEEKDAYS -> context.getString(R.string.routines_weekdays)
    WeekdayMask.Preset.WEEKENDS -> context.getString(R.string.routines_weekends)
    null -> WeekdayMask.days(mask, weekStartsMonday).joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

/** Schedule plus reminder time, e.g. "Weekdays · 7:00". */
fun scheduleLine(context: Context, routine: RoutineEntity, weekStartsMonday: Boolean): String {
    val days = scheduleLabel(context, routine.weekdays, weekStartsMonday)
    val time = routine.timeMinutes?.let { Formatters.minutesOfDay(context, it) }
    return if (time == null) days else "$days · $time"
}

fun dayInitial(day: DayOfWeek): String = day.getDisplayName(TextStyle.NARROW, Locale.getDefault())

fun dayFullName(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, Locale.getDefault())

/**
 * A small calendar heat map: one row per week, one rounded square per day. Completed days are filled with the routine's
 * colour, partly done days are tinted, missed days are outlined in the error colour, unscheduled days stay faint.
 */
@Composable
fun HistoryGrid(
    rows: List<List<HistoryDay>>,
    color: Color,
    weekStartsMonday: Boolean,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val missed = LocalTaskfernColors.current.overdue
    val faint = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WeekdayMask.week(weekStartsMonday).forEach { d ->
                Text(
                    dayInitial(d),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        rows.forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { day ->
                    val description = historyDescription(context, day)
                    val shape = RoundedCornerShape(8.dp)
                    val base = Modifier.weight(1f).aspectRatio(1f).clip(shape).semantics { contentDescription = description }
                    val cell = when (day.state) {
                        DayState.DONE -> base.background(color)
                        DayState.PARTIAL -> base.background(color.copy(alpha = 0.18f + 0.45f * day.ratio))
                        DayState.TODAY_OPEN -> base
                            .background(if (day.done > 0) color.copy(alpha = 0.18f + 0.45f * day.ratio) else faint)
                            .border(2.dp, color, shape)
                        DayState.MISSED -> base.background(missed.copy(alpha = 0.10f)).border(1.5.dp, missed.copy(alpha = 0.55f), shape)
                        DayState.NOT_SCHEDULED, DayState.BEFORE_START -> base.background(faint.copy(alpha = 0.55f))
                        DayState.FUTURE -> base.border(1.dp, faint, shape)
                    }
                    Box(cell, contentAlignment = Alignment.Center) {
                        if (day.date == today || day.date.dayOfMonth == 1) {
                            Text(
                                day.date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (day.state == DayState.DONE) contentOn(color) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(color, stringResource(R.string.routines_legend_done))
            LegendDot(color.copy(alpha = 0.35f), stringResource(R.string.routines_legend_partial))
            LegendDot(missed.copy(alpha = 0.10f), stringResource(R.string.routines_legend_missed), outline = missed.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun LegendDot(fill: Color, label: String, outline: Color? = null) {
    val shape = RoundedCornerShape(4.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(12.dp).clip(shape).background(fill)
                .then(if (outline != null) Modifier.border(1.5.dp, outline, shape) else Modifier),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun historyDescription(context: Context, day: HistoryDay): String {
    val date = Formatters.mediumDate(day.date)
    val state = when (day.state) {
        DayState.DONE -> context.getString(R.string.routines_day_done)
        DayState.PARTIAL, DayState.TODAY_OPEN -> context.resources.getQuantityString(R.plurals.routines_day_partial, day.total, day.done.coerceAtMost(day.total), day.total)
        DayState.MISSED -> context.getString(R.string.routines_day_missed)
        DayState.NOT_SCHEDULED -> context.getString(R.string.routines_day_not_scheduled)
        DayState.BEFORE_START, DayState.FUTURE -> ""
    }
    return if (state.isEmpty()) date else "$date: $state"
}

/** Black or white, whichever reads better on [color]. */
fun contentOn(color: Color): Color {
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return if (luminance > 0.55f) Color(0xFF14201A) else Color.White
}

/** A big emoji inside the routine's ring, used in cards and the detail header. */
@Composable
fun RoutineBadge(emoji: String, progress: Float, color: Color, size: Dp, stroke: Dp, modifier: Modifier = Modifier) {
    ProgressRing(progress = progress, color = color, size = size, stroke = stroke, modifier = modifier) {
        Box(
            Modifier.size(size - stroke * 2 - 6.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                emoji.ifEmpty { "🌿" },
                style = if (size >= 80.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** Fills the remaining width; used for stat tiles. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
    }
}
