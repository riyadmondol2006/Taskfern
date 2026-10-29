package com.riyadmondol2006.taskfern.ui.calendar

import com.riyadmondol2006.taskfern.domain.LogicalDay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.TaskEntity
import com.riyadmondol2006.taskfern.data.db.TaskRow
import com.riyadmondol2006.taskfern.data.prefs.SettingsRepository
import com.riyadmondol2006.taskfern.data.repo.TaskRepository
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceEngine
import com.riyadmondol2006.taskfern.ui.common.DatePickerSheet
import com.riyadmondol2006.taskfern.ui.common.EmptyState
import com.riyadmondol2006.taskfern.ui.common.Formatters
import com.riyadmondol2006.taskfern.ui.common.SectionHeader
import com.riyadmondol2006.taskfern.ui.common.TaskItem
import com.riyadmondol2006.taskfern.ui.common.currentLocale
import com.riyadmondol2006.taskfern.ui.common.taskfernViewModel
import com.riyadmondol2006.taskfern.ui.navigation.AppNavigator
import com.riyadmondol2006.taskfern.ui.tasks.TaskActionsViewModel
import com.riyadmondol2006.taskfern.ui.tasks.TaskEventsEffect
import com.riyadmondol2006.taskfern.ui.theme.LocalTaskfernColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

/** A repeating task's future occurrence shown on the calendar (not a real row until it comes due). */
data class Projection(val task: TaskEntity, val date: LocalDate)

data class DayMarks(val due: Int, val projected: Int)

data class CalendarState(
    val month: YearMonth = YearMonth.now(),
    val selected: LocalDate = LogicalDay.today(),
    val weekStartsMonday: Boolean = true,
    val marks: Map<LocalDate, DayMarks> = emptyMap(),
    val dayRows: List<TaskRow> = emptyList(),
    val dayProjections: List<Projection> = emptyList(),
)

enum class CalendarMode { MONTH, WEEK }

class CalendarViewModel(tasks: TaskRepository, settings: SettingsRepository) : TaskActionsViewModel(tasks) {
    val month = MutableStateFlow(YearMonth.now())
    val selected = MutableStateFlow(LogicalDay.today())
    val mode = MutableStateFlow(CalendarMode.MONTH)

    private val range = combine(month, selected, mode, settings.settings) { m, sel, md, s ->
        if (md == CalendarMode.WEEK) {
            val start = sel.with(TemporalAdjusters.previousOrSame(if (s.weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY))
            start to start.plusDays(6)
        } else gridRange(m, s.weekStartsMonday)
    }

    val state: StateFlow<CalendarState> = range.flatMapLatest { (from, to) ->
        combine(
            tasks.observeDueCounts(from, to),
            tasks.observeOpenRecurringDueBefore(to),
            selected.flatMapLatest { tasks.observeDueBetween(it, it) },
            combine(month, selected, settings.settings) { m, s, st -> Triple(m, s, st.weekStartsMonday) },
        ) { counts, recurring, dayRows, (m, sel, monday) ->
            val projections = recurring.flatMap { t ->
                val rule = t.recurrence ?: return@flatMap emptyList()
                val anchor = t.seriesAnchor ?: t.dueDate ?: return@flatMap emptyList()
                val first = t.dueDate ?: return@flatMap emptyList()
                // Future occurrences after the current due date (the current one is a real row already).
                val remaining = rule.maxOccurrences?.let { it - t.occurrenceCount - 1 } ?: Int.MAX_VALUE
                if (remaining <= 0) return@flatMap emptyList()
                RecurrenceEngine.occurrencesBetween(rule, anchor, first, from, to, limit = 62)
                    .filter { it.isAfter(first) }
                    .take(remaining)
                    .map { Projection(t, it) }
            }
            val countMap = counts.associate { it.date to it.count }
            val projMap = projections.groupingBy { it.date }.eachCount()
            val marks = (countMap.keys + projMap.keys).associateWith { DayMarks(countMap[it] ?: 0, projMap[it] ?: 0) }
            CalendarState(m, sel, monday, marks, dayRows, projections.filter { it.date == sel }.sortedBy { it.task.dueTime })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState())

    fun select(date: LocalDate) {
        selected.value = date
        if (YearMonth.from(date) != month.value) month.value = YearMonth.from(date)
    }

    fun shift(delta: Long) {
        if (mode.value == CalendarMode.WEEK) select(selected.value.plusWeeks(delta))
        else {
            val m = month.value.plusMonths(delta)
            month.value = m
            selected.value = if (YearMonth.now() == m) LogicalDay.today() else m.atDay(1)
        }
    }

    fun goToday() = select(LogicalDay.today())

    fun reschedule(row: TaskRow, date: LocalDate) = viewModelScope.launch { tasks.setDueDate(listOf(row.task.id), date) }

    companion object {
        fun gridRange(month: YearMonth, weekStartsMonday: Boolean): Pair<LocalDate, LocalDate> {
            val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(if (weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY))
            return first to first.plusDays(41)
        }
    }
}

@Composable
fun CalendarScreen(nav: AppNavigator) {
    val vm = taskfernViewModel { c, _ -> CalendarViewModel(c.tasks, c.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val streaks by vm.streaks.collectAsStateWithLifecycle()
    var rescheduling by remember { mutableStateOf<TaskRow?>(null) }
    TaskEventsEffect(vm)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Formatters.monthYear(state.month.atDay(1))) },
                actions = {
                    TextButton(onClick = vm::goToday) { Text(stringResource(R.string.today)) }
                    IconButton(onClick = { vm.shift(-1) }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(if (mode == CalendarMode.WEEK) R.string.calendar_prev_week else R.string.calendar_prev_month))
                    }
                    IconButton(onClick = { vm.shift(1) }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(if (mode == CalendarMode.WEEK) R.string.calendar_next_week else R.string.calendar_next_month))
                    }
                    IconButton(onClick = nav::openSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.nav_search)) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.quickAdd(dueDate = state.selected) }) {
                Icon(Icons.Filled.Add, stringResource(R.string.calendar_add_on_day))
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "mode") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                    SegmentedButton(selected = mode == CalendarMode.MONTH, onClick = { vm.mode.value = CalendarMode.MONTH }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text(stringResource(R.string.calendar_month))
                    }
                    SegmentedButton(selected = mode == CalendarMode.WEEK, onClick = { vm.mode.value = CalendarMode.WEEK }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text(stringResource(R.string.calendar_week))
                    }
                }
            }
            item(key = "grid") {
                Box(
                    Modifier.pointerInput(mode) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total > 120) vm.shift(-1) else if (total < -120) vm.shift(1) },
                            onHorizontalDrag = { _, dx -> total += dx },
                        )
                    },
                ) {
                    if (mode == CalendarMode.MONTH) MonthGrid(state, vm::select) else WeekStrip(state, vm::select)
                }
            }
            item(key = "day-h") {
                SectionHeader(Formatters.longDate(state.selected), count = (state.dayRows.size + state.dayProjections.size).takeIf { it > 0 })
            }
            if (state.dayRows.isEmpty() && state.dayProjections.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.calendar_day_empty_title),
                        message = stringResource(R.string.calendar_day_empty_message),
                    )
                }
            }
            items(state.dayRows, key = { it.task.id }) { row ->
                TaskItem(
                    row,
                    showList = true,
                    onToggle = { vm.toggle(row) },
                    onClick = { nav.openTask(row.task.id) },
                    onLongClick = { rescheduling = row },
                    onSwipeComplete = { vm.toggle(row) },
                    onSwipeDelete = { vm.delete(listOf(row.task.id)) },
                    streak = streaks[row.task.id]?.current ?: 0,
                )
            }
            items(state.dayProjections, key = { "p${it.task.id}-${it.date}" }) { p ->
                ProjectionRow(p, onClick = { nav.openTask(p.task.id) })
            }
            if (state.dayRows.isNotEmpty()) {
                item(key = "hint") {
                    Text(
                        stringResource(R.string.calendar_reschedule_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }

    rescheduling?.let { row ->
        DatePickerSheet(initial = row.task.dueDate, onPick = { vm.reschedule(row, it) }, onDismiss = { rescheduling = null })
    }
}

private fun weekdayHeaders(monday: Boolean): List<DayOfWeek> {
    val start = if (monday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    return (0L until 7L).map { start.plus(it) }
}

@Composable
private fun MonthGrid(state: CalendarState, onSelect: (LocalDate) -> Unit) {
    val (first, _) = CalendarViewModel.gridRange(state.month, state.weekStartsMonday)
    val today = LogicalDay.today()
    // Square cells on phones; capped height on wide screens so the whole month stays visible.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
    val cellHeight = (maxWidth / 7).coerceAtMost(56.dp)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            weekdayHeaders(state.weekStartsMonday).forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.NARROW, currentLocale()),
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        for (week in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (d in 0 until 7) {
                    val date = first.plusDays((week * 7 + d).toLong())
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == state.month,
                        isToday = date == today,
                        selected = date == state.selected,
                        marks = state.marks[date],
                        modifier = Modifier.weight(1f).height(cellHeight),
                        onClick = { onSelect(date) },
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun WeekStrip(state: CalendarState, onSelect: (LocalDate) -> Unit) {
    val start = state.selected.with(TemporalAdjusters.previousOrSame(if (state.weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY))
    val today = LogicalDay.today()
    Row(Modifier.fillMaxWidth()) {
        for (i in 0 until 7) {
            val date = start.plusDays(i.toLong())
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.SHORT, currentLocale()),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DayCell(date, true, date == today, date == state.selected, state.marks[date], Modifier.fillMaxWidth(), onClick = { onSelect(date) }, tall = true)
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    selected: Boolean,
    marks: DayMarks?,
    modifier: Modifier,
    onClick: () -> Unit,
    tall: Boolean = false,
) {
    val primary = MaterialTheme.colorScheme.primary
    val past = date.isBefore(LogicalDay.today())
    val dueCount = marks?.due ?: 0
    val dueText = pluralStringResource(R.plurals.calendar_tasks_due, dueCount, dueCount)
    val repeatsText = stringResource(R.string.calendar_repeats_here)
    val desc = buildString {
        append(Formatters.longDate(date))
        if (dueCount > 0) append(", ").append(dueText)
        if ((marks?.projected ?: 0) > 0) append(", ").append(repeatsText)
    }
    Box(
        modifier
            .then(if (tall) Modifier.heightIn(min = 60.dp) else Modifier)
            .padding(2.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .then(if (isToday && !selected) Modifier.border(1.5.dp, primary, MaterialTheme.shapes.medium) else Modifier)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = desc; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    selected -> MaterialTheme.colorScheme.onPrimaryContainer
                    !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(6.dp)) {
                val due = marks?.due ?: 0
                val dotColor = if (past) LocalTaskfernColors.current.overdue else primary
                repeat(due.coerceAtMost(3)) { Box(Modifier.size(5.dp).clip(CircleShape).background(dotColor)) }
                if ((marks?.projected ?: 0) > 0 && due < 3) {
                    Box(Modifier.size(5.dp).clip(CircleShape).border(1.dp, primary, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun ProjectionRow(p: Projection, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Repeat, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.task.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(stringResource(R.string.calendar_upcoming_repeat), p.task.dueTime?.let { Formatters.time(context, it) }).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
