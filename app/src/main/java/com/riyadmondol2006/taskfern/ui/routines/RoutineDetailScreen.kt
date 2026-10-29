package com.riyadmondol2006.taskfern.ui.routines

import com.riyadmondol2006.taskfern.domain.WeekdayMask
import com.riyadmondol2006.taskfern.domain.HistoryDay
import com.riyadmondol2006.taskfern.domain.RoutineHistory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.RoutineEntity
import com.riyadmondol2006.taskfern.data.db.RoutineStepEntity
import com.riyadmondol2006.taskfern.data.prefs.SettingsRepository
import com.riyadmondol2006.taskfern.data.repo.RoutineRepository
import com.riyadmondol2006.taskfern.ui.common.ConfirmDialog
import com.riyadmondol2006.taskfern.ui.common.LocalSnackbarHost
import com.riyadmondol2006.taskfern.ui.common.SectionHeader
import com.riyadmondol2006.taskfern.ui.common.listColor
import com.riyadmondol2006.taskfern.ui.common.showUndo
import com.riyadmondol2006.taskfern.ui.common.taskfernViewModel
import com.riyadmondol2006.taskfern.ui.navigation.AppNavigator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

private const val HISTORY_WEEKS = 5
private const val STREAK_LOOKBACK_DAYS = 370L
private const val RATE_DAYS = 28L

data class RoutineDetailUiState(
    val loaded: Boolean = false,
    val routine: RoutineEntity? = null,
    val today: LocalDate = LocalDate.now(),
    val steps: List<StepState> = emptyList(),
    val historyRows: List<List<HistoryDay>> = emptyList(),
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val completionRate: Float? = null,
    val weekStartsMonday: Boolean = true,
) {
    val done: Int get() = steps.count { it.checked }
    val total: Int get() = steps.size
    val complete: Boolean get() = total > 0 && done >= total
}

class RoutineDetailViewModel(
    private val routines: RoutineRepository,
    settings: SettingsRepository,
    private val routineId: Long,
) : ViewModel() {
    private val date = currentDate().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDate.now())

    val state: StateFlow<RoutineDetailUiState> = combine(
        routines.observeRoutine(routineId),
        date.flatMapLatest { d -> routines.observeStepsWithChecks(routineId, d).map { d to it } },
        date.flatMapLatest { d ->
            routines.observeProgressBetween(d.minusDays(STREAK_LOOKBACK_DAYS), d)
                .map { rows -> rows.filter { it.routineId == routineId }.associate { it.date to it.done } }
        },
        settings.settings,
    ) { routine, (today, steps), doneByDate, prefs ->
        if (routine == null) return@combine RoutineDetailUiState(loaded = true, today = today)
        val stepStates = steps.map { StepState(it.step, it.checked) }
        // Today's ticks come from the live step query so the history matches the list exactly.
        val done = doneByDate + (today to stepStates.count { it.checked })
        val total = stepStates.size
        val start = routine.startDate()
        val rows = RoutineHistory.weekRows(today, HISTORY_WEEKS, prefs.weekStartsMonday)
            .map { RoutineHistory.history(it, today, routine.weekdays, done, total, start) }
        RoutineDetailUiState(
            loaded = true,
            routine = routine,
            today = today,
            steps = stepStates,
            historyRows = rows,
            currentStreak = RoutineHistory.currentStreak(today, routine.weekdays, done, total, start, STREAK_LOOKBACK_DAYS.toInt()),
            bestStreak = RoutineHistory.bestStreak(today.minusDays(STREAK_LOOKBACK_DAYS), today, routine.weekdays, done, total, start),
            completionRate = RoutineHistory.completionRate(today.minusDays(RATE_DAYS - 1), today, routine.weekdays, done, total, start),
            weekStartsMonday = prefs.weekStartsMonday,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineDetailUiState())

    fun toggle(step: RoutineStepEntity) {
        val today = state.value.today
        viewModelScope.launch { routines.toggleStep(step.id, routineId, today) }
    }

    fun checkAll() {
        val today = state.value.today
        viewModelScope.launch { routines.checkAll(routineId, today) }
    }

    /** Clears today's ticks; returns the ids that were ticked so the reset can be undone. */
    fun resetToday(onDone: (LocalDate, List<Long>) -> Unit) {
        val s = state.value
        val ticked = s.steps.filter { it.checked }.map { it.step.id }
        if (ticked.isEmpty()) return
        viewModelScope.launch {
            routines.resetDay(routineId, s.today)
            onDone(s.today, ticked)
        }
    }

    fun restoreTicks(date: LocalDate, stepIds: List<Long>) {
        viewModelScope.launch { stepIds.forEach { routines.setStepChecked(it, routineId, date, true) } }
    }

    fun setPaused(paused: Boolean) {
        viewModelScope.launch { routines.setPaused(routineId, paused) }
    }

    fun duplicate(onDone: (Long) -> Unit) {
        viewModelScope.launch { routines.duplicate(routineId)?.let(onDone) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            routines.delete(routineId)
            onDone()
        }
    }
}

@Composable
fun RoutineDetailScreen(nav: AppNavigator, routineId: Long) {
    val vm = taskfernViewModel(key = "routine-$routineId") { c, _ -> RoutineDetailViewModel(c.routines, c.settings, routineId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    var left by remember { mutableStateOf(false) }
    fun leave() {
        if (!left) {
            left = true
            nav.back()
        }
    }
    LaunchedEffect(state.loaded, state.routine) {
        if (state.loaded && state.routine == null) leave()
    }

    var menu by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {},
                navigationIcon = {
                    IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = { nav.editRoutine(routineId) }, enabled = state.routine != null) {
                        Icon(Icons.Outlined.Edit, stringResource(R.string.routines_edit))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
                        val paused = state.routine?.paused == true
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (paused) R.string.routines_resume else R.string.routines_pause)) },
                                leadingIcon = { Icon(if (paused) Icons.Outlined.PlayCircle else Icons.Outlined.PauseCircle, null) },
                                onClick = { menu = false; vm.setPaused(!paused) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_duplicate)) },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                onClick = {
                                    menu = false
                                    vm.duplicate { nav.openRoutine(it) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val routine = state.routine
        if (routine == null) {
            Box(Modifier.fillMaxSize().padding(padding))
        } else {
            val color = listColor(routine.color)
            val runsToday = WeekdayMask.runsOn(routine.weekdays, state.today)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
            ) {
                item(key = "header") {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        RoutineBadge(
                            routine.emoji,
                            if (state.total == 0) 0f else state.done.toFloat() / state.total,
                            color,
                            size = 120.dp,
                            stroke = 9.dp,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(routine.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            scheduleLine(context, routine, state.weekStartsMonday),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                state.total == 0 -> stringResource(R.string.routines_no_steps)
                                state.complete -> stringResource(R.string.routines_done_today)
                                else -> pluralStringResource(R.plurals.routines_steps_progress, state.total, state.done, state.total)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (state.complete) color else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (routine.paused) {
                    item(key = "paused") {
                        NoticeCard(
                            icon = { Icon(Icons.Outlined.PauseCircle, null) },
                            text = stringResource(R.string.routines_paused_notice),
                            action = { TextButton(onClick = { vm.setPaused(false) }) { Text(stringResource(R.string.routines_resume)) } },
                        )
                    }
                } else if (!runsToday) {
                    item(key = "not-today") {
                        NoticeCard(
                            icon = { Icon(Icons.Outlined.EventBusy, null) },
                            text = stringResource(R.string.routines_not_today_notice),
                        )
                    }
                }
                item(key = "actions") {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            onClick = vm::checkAll,
                            enabled = state.total > 0 && !state.complete,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.DoneAll, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.routines_mark_all_done))
                        }
                        OutlinedButton(
                            onClick = {
                                vm.resetToday { date, ids ->
                                    scope.showUndo(snackbar, resources.getString(R.string.routines_reset_done), resources.getString(R.string.action_undo)) {
                                        vm.restoreTicks(date, ids)
                                    }
                                }
                            },
                            enabled = state.done > 0,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.RestartAlt, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.routines_reset_today))
                        }
                    }
                }
                item(key = "steps-header") { SectionHeader(stringResource(R.string.routines_steps_today)) }
                item(key = "steps") {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            if (state.steps.isEmpty()) {
                                Text(
                                    stringResource(R.string.routines_no_steps_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                            state.steps.forEach { s -> StepRow(s, onToggle = { vm.toggle(s.step) }) }
                        }
                    }
                }
                item(key = "history-header") { SectionHeader(stringResource(R.string.routines_history)) }
                item(key = "stats") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            value = state.currentStreak.toString(),
                            label = stringResource(R.string.routines_streak_current),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = state.bestStreak.toString(),
                            label = stringResource(R.string.routines_streak_best),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = state.completionRate?.let { "${(it * 100).roundToInt()}%" } ?: "–",
                            label = stringResource(R.string.routines_rate_label),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item(key = "grid") {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        HistoryGrid(
                            rows = state.historyRows,
                            color = color,
                            weekStartsMonday = state.weekStartsMonday,
                            today = state.today,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                item(key = "streak-help") {
                    Text(
                        stringResource(R.string.routines_streak_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.routines_delete_title),
            message = stringResource(R.string.routines_delete_message, state.routine?.name.orEmpty()),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { vm.delete { leave() } },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun NoticeCard(icon: @Composable () -> Unit, text: String, action: (@Composable () -> Unit)? = null) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            action?.invoke()
        }
    }
}
