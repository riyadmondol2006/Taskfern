package com.riyadmondol2006.taskfern.ui.settings

import com.riyadmondol2006.taskfern.domain.LogicalDay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.riyadmondol2006.taskfern.AppContainer
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.TaskRow
import com.riyadmondol2006.taskfern.ui.common.EmptyState
import com.riyadmondol2006.taskfern.ui.common.Formatters
import com.riyadmondol2006.taskfern.ui.common.taskfernViewModel
import com.riyadmondol2006.taskfern.ui.navigation.AppNavigator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class InsightsState(
    val today: LocalDate,
    val week: List<LocalDate>,
    val perDay: List<Int>,
    val completedToday: Int,
    val overdue: Int,
    val open: Int,
    val routines: RoutineWeek,
    val focusMinutes: Long,
    val recent: List<TaskRow>,
    val streaks: List<StreakEntry> = emptyList(),
)

/** A routine or repeating task with its streak, for the Streaks list. */
data class StreakEntry(val id: Long, val isRoutine: Boolean, val name: String, val emoji: String, val current: Int, val best: Int)

class InsightsViewModel(c: AppContainer) : ViewModel() {
    val state = c.settings.settings.map { it.weekStartsMonday }.distinctUntilChanged().flatMapLatest { monday ->
        val zone = ZoneId.systemDefault()
        val today = LogicalDay.today()
        val week = InsightsMath.weekDays(InsightsMath.weekStart(today, monday))
        val since = InsightsMath.startOfDayMillis(week.first(), zone)
        val routineWeek = combine(
            c.routines.observeRoutines(),
            c.routines.observeAllSteps(),
            c.routines.observeProgressBetween(week.first(), week.last()),
        ) { routines, steps, progress ->
            InsightsMath.routineWeek(routines, steps.groupingBy { it.routineId }.eachCount(), progress, week, today, zone)
        }
        val streaks = combine(c.routines.observeRoutines(), c.routines.observeRoutineStreaks(today), c.tasks.observeHabits()) { rs, rStreaks, habits ->
            val routineEntries = rs.filter { !it.paused }.mapNotNull { r ->
                val s = rStreaks[r.id] ?: return@mapNotNull null
                StreakEntry(r.id, true, r.name, r.emoji, s.current, s.best).takeIf { s.best > 0 }
            }
            val habitEntries = habits.map { (t, s) -> StreakEntry(t.id, false, t.title, "", s.current, s.best) }
            (routineEntries + habitEntries).sortedWith(compareByDescending<StreakEntry> { it.current }.thenByDescending { it.best }).take(10)
        }
        val counts = combine(c.tasks.observeOverdueCount(today), c.tasks.observeOpenCount(), streaks) { overdue, open, st -> Triple(overdue, open, st) }
        combine(
            c.tasks.observeCompletionTimesSince(since),
            counts,
            routineWeek,
            c.focusRepository.observeFocusedSecondsSince(since),
            c.tasks.observeCompleted(10),
        ) { times, (overdue, open, streakEntries), routines, focusSeconds, recent ->
            InsightsState(
                today = today,
                week = week,
                perDay = InsightsMath.completionsPerDay(times, week, zone),
                completedToday = InsightsMath.completionsOn(times, today, zone),
                overdue = overdue,
                open = open,
                routines = routines,
                focusMinutes = InsightsMath.focusMinutes(focusSeconds),
                recent = recent,
                streaks = streakEntries,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun InsightsScreen(nav: AppNavigator) {
    val vm = taskfernViewModel { c, _ -> InsightsViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()

    SettingsScaffold(title = stringResource(R.string.insights_title), onBack = nav::back) { padding ->
        val s = state ?: return@SettingsScaffold
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp, start = 16.dp, end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.insights_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(stringResource(R.string.insights_done_today), s.completedToday.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.insights_open), s.open.toString(), Modifier.weight(1f))
                    StatTile(
                        stringResource(R.string.insights_overdue),
                        s.overdue.toString(),
                        Modifier.weight(1f),
                    )
                }
            }
            item { WeekCard(s) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        label = stringResource(R.string.insights_routines),
                        value = if (s.routines.scheduledDays == 0) "–"
                        else stringResource(R.string.insights_fraction, s.routines.completedDays, s.routines.scheduledDays),
                        modifier = Modifier.weight(1f),
                        caption = if (s.routines.scheduledDays == 0) stringResource(R.string.insights_routines_none)
                        else stringResource(R.string.insights_routines_caption),
                    )
                    StatTile(
                        label = stringResource(R.string.insights_focus),
                        value = s.focusMinutes.toString(),
                        modifier = Modifier.weight(1f),
                        caption = pluralStringResource(R.plurals.insights_focus_caption, s.focusMinutes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.insights_streaks),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp),
                )
            }
            if (s.streaks.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.insights_streaks_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            } else {
                items(s.streaks, key = { (if (it.isRoutine) "r" else "t") + it.id }) { e ->
                    StreakRow(e, onClick = { if (e.isRoutine) nav.openRoutine(e.id) else nav.openTask(e.id) })
                }
            }
            item {
                Text(
                    stringResource(R.string.insights_recent),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp),
                )
            }
            if (s.recent.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.insights_recent_empty_title),
                        message = stringResource(R.string.insights_recent_empty),
                    )
                }
            } else {
                items(s.recent, key = { it.task.id }) { row -> RecentRow(row, s.today, onClick = { nav.openTask(row.task.id) }) }
            }
        }
    }
}

@Composable
private fun StreakRow(e: StreakEntry, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(e.emoji.ifEmpty { "🔁" }, style = MaterialTheme.typography.titleMedium)
            androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    stringResource(if (e.isRoutine) R.string.insights_streak_routine else R.string.insights_streak_habit) + " · " +
                        stringResource(R.string.streak_best, e.best),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (e.current > 0) com.riyadmondol2006.taskfern.ui.common.StreakChip(e.current)
            else Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, caption: String? = null) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        Column(Modifier.padding(16.dp).semantics(mergeDescendants = true) {}) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Completions per day this week: a small bar chart with a full text alternative for screen readers. */
@Composable
private fun WeekCard(s: InsightsState) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
    val names = s.week.map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) }
    val fullNames = s.week.map { it.dayOfWeek.getDisplayName(TextStyle.FULL, locale) }
    val total = s.perDay.sum()
    val description = stringResource(
        R.string.insights_week_description,
        s.week.indices.joinToString(", ") { "${fullNames[it]} ${s.perDay[it]}" },
    )
    val max = (s.perDay.maxOrNull() ?: 0).coerceAtLeast(1)
    val todayIndex = s.week.indexOf(s.today)
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.insights_this_week), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                pluralStringResource(R.plurals.insights_week_total, total, total),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            Column(Modifier.clearAndSetSemantics { contentDescription = description }) {
                Row(Modifier.fillMaxWidth()) {
                    s.perDay.forEach { n ->
                        Text(
                            if (n > 0) n.toString() else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Canvas(Modifier.fillMaxWidth().height(112.dp).padding(vertical = 4.dp)) {
                    val slot = size.width / 7f
                    val barWidth = (slot * 0.46f).coerceAtMost(28.dp.toPx())
                    val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    s.perDay.forEachIndexed { i, n ->
                        val left = slot * i + (slot - barWidth) / 2f
                        drawRoundRect(track, Offset(left, 0f), Size(barWidth, size.height), radius)
                        if (n > 0) {
                            val h = (size.height * n / max).coerceAtLeast(barWidth)
                            drawRoundRect(
                                if (i == todayIndex) primary else muted,
                                Offset(left, size.height - h),
                                Size(barWidth, h),
                                radius,
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    names.forEachIndexed { i, name ->
                        Text(
                            name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (i == todayIndex) FontWeight.Bold else FontWeight.Normal,
                            color = if (i == todayIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(row: TaskRow, today: LocalDate, onClick: () -> Unit) {
    val context = LocalContext.current
    val t = row.task
    val whenText = t.completedAt?.let {
        val at = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
        "${Formatters.relativeDay(context, at.toLocalDate(), today)} ${Formatters.time(context, at.toLocalTime())}"
    }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(whenText, row.listName).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}
