package com.riyadmondol2006.taskfern.ui.routines

import com.riyadmondol2006.taskfern.domain.WeekdayMask
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.ui.common.fabLabel
import com.riyadmondol2006.taskfern.data.db.RoutineEntity
import com.riyadmondol2006.taskfern.data.db.RoutineStepEntity
import com.riyadmondol2006.taskfern.data.db.TemplateEntity
import com.riyadmondol2006.taskfern.data.db.TemplateKind
import com.riyadmondol2006.taskfern.data.prefs.SettingsRepository
import com.riyadmondol2006.taskfern.data.repo.RoutineRepository
import com.riyadmondol2006.taskfern.data.repo.TemplateRepository
import com.riyadmondol2006.taskfern.ui.common.EmptyState
import com.riyadmondol2006.taskfern.ui.common.Formatters
import com.riyadmondol2006.taskfern.ui.common.SectionHeader
import com.riyadmondol2006.taskfern.ui.common.TaskCheckbox
import com.riyadmondol2006.taskfern.ui.common.listColor
import com.riyadmondol2006.taskfern.ui.common.taskfernViewModel
import com.riyadmondol2006.taskfern.ui.navigation.AppNavigator
import com.riyadmondol2006.taskfern.ui.shopping.TemplatePickerSheet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class StepState(val step: RoutineStepEntity, val checked: Boolean)

data class RoutineCard(
    val routine: RoutineEntity,
    val steps: List<StepState>,
    val runsToday: Boolean,
    /** Fully completed scheduled days in a row. */
    val streak: Int = 0,
) {
    val total: Int get() = steps.size
    val done: Int get() = steps.count { it.checked }
    val complete: Boolean get() = total > 0 && done >= total
    val progress: Float get() = if (total == 0) 0f else done.toFloat() / total
}

data class RoutinesUiState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val todayCards: List<RoutineCard> = emptyList(),
    val otherCards: List<RoutineCard> = emptyList(),
    val pausedCards: List<RoutineCard> = emptyList(),
    val weekStartsMonday: Boolean = true,
) {
    val isEmpty: Boolean get() = todayCards.isEmpty() && otherCards.isEmpty() && pausedCards.isEmpty()
}

class RoutinesViewModel(
    private val routines: RoutineRepository,
    settings: SettingsRepository,
    private val templateRepo: TemplateRepository,
) : ViewModel() {
    private val date = currentDate().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDate.now())

    val state: StateFlow<RoutinesUiState> = combine(
        routines.observeRoutines(),
        routines.observeAllSteps(),
        date.flatMapLatest { d -> combine(routines.observeChecksOn(d), routines.observeRoutineStreaks(d)) { c, s -> Triple(d, c, s) } },
        settings.settings,
    ) { all, steps, (today, checks, streaks), prefs ->
        val checked = checks.map { it.stepId }.toSet()
        val stepsByRoutine = steps.groupBy { it.routineId }
        val cards = all.map { r ->
            RoutineCard(
                routine = r,
                steps = stepsByRoutine[r.id].orEmpty().map { StepState(it, it.id in checked) },
                runsToday = RoutineRepository.runsOn(r, today),
                streak = streaks[r.id]?.current ?: 0,
            )
        }
        RoutinesUiState(
            loaded = true,
            today = today,
            todayCards = cards.filter { !it.routine.paused && it.runsToday }.sortedWith(compareBy({ it.routine.timeMinutes ?: Int.MAX_VALUE }, { it.routine.position })),
            otherCards = cards.filter { !it.routine.paused && !it.runsToday },
            pausedCards = cards.filter { it.routine.paused },
            weekStartsMonday = prefs.weekStartsMonday,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutinesUiState())

    val templates: StateFlow<List<TemplateEntity>> = templateRepo.observeAll()
        .map { all -> all.filter { it.kind == TemplateKind.ROUTINE } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggle(step: RoutineStepEntity) {
        val today = state.value.today
        viewModelScope.launch { routines.toggleStep(step.id, step.routineId, today) }
    }

    fun checkAll(routineId: Long) {
        val today = state.value.today
        viewModelScope.launch { routines.checkAll(routineId, today) }
    }

    fun fromTemplate(template: TemplateEntity, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(templateRepo.apply(template)) }
    }
}

@Composable
fun RoutinesScreen(nav: AppNavigator) {
    val vm = taskfernViewModel { c, _ -> RoutinesViewModel(c.routines, c.settings, c.templates) }
    val state by vm.state.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showTemplates by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    // Ids whose card is open. Initialised once with today's unfinished routines, then left to the user.
    var expanded by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var expandedInitialised by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.loaded) {
        if (state.loaded && !expandedInitialised) {
            expanded = state.todayCards.filter { !it.complete }.map { it.routine.id }
            expandedInitialised = true
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.routines_title)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { showTemplates = true }) {
                        Icon(Icons.Outlined.LibraryAdd, stringResource(R.string.routines_from_template))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_manage_templates)) },
                                leadingIcon = { Icon(Icons.Outlined.ViewAgenda, null) },
                                onClick = { menu = false; nav.openTemplates() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.nav_settings)) },
                                leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                onClick = { menu = false; nav.openSettings() },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.loaded && !state.isEmpty) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.routines_new)) },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    expanded = fabExpanded,
                    onClick = { nav.editRoutine(0) },
                    // Explicit label: TalkBack does not always see the text of an extended FAB.
                    modifier = Modifier.fabLabel(R.string.routines_new),
                )
            }
        },
    ) { padding ->
        when {
            !state.loaded -> Box(Modifier.fillMaxSize().padding(padding))
            state.isEmpty -> Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = stringResource(R.string.routines_empty_title),
                    message = stringResource(R.string.routines_empty_message),
                    illustration = R.drawable.ic_routine,
                    modifier = Modifier.align(Alignment.Center),
                    action = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(onClick = { nav.editRoutine(0) }) {
                                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.routines_new))
                            }
                            if (templates.isNotEmpty()) {
                                Spacer(Modifier.padding(4.dp))
                                OutlinedButton(onClick = { showTemplates = true }) {
                                    Icon(Icons.Outlined.LibraryAdd, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.routines_from_template))
                                }
                            }
                        }
                    },
                )
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "summary") {
                    val doneCount = state.todayCards.count { it.complete }
                    Column(Modifier.padding(horizontal = 4.dp).semantics(mergeDescendants = true) {}) {
                        Text(Formatters.longDate(state.today), style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                state.todayCards.isEmpty() -> stringResource(R.string.routines_none_today)
                                doneCount == state.todayCards.size -> stringResource(R.string.routines_all_done_today)
                                else -> pluralStringResource(R.plurals.routines_today_progress, state.todayCards.size, doneCount, state.todayCards.size)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.todayCards.isNotEmpty()) {
                    item(key = "h-today") { SectionHeader(stringResource(R.string.routines_section_today), Modifier.animateItem()) }
                    items(state.todayCards, key = { it.routine.id }) { card ->
                        val open = card.routine.id in expanded
                        TodayRoutineCard(
                            card = card,
                            expanded = open,
                            weekStartsMonday = state.weekStartsMonday,
                            onToggleExpanded = {
                                expanded = if (open) expanded - card.routine.id else expanded + card.routine.id
                            },
                            onToggleStep = vm::toggle,
                            onCheckAll = { vm.checkAll(card.routine.id) },
                            onOpen = { nav.openRoutine(card.routine.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                if (state.otherCards.isNotEmpty()) {
                    item(key = "h-other") { SectionHeader(stringResource(R.string.routines_section_other), Modifier.animateItem()) }
                    items(state.otherCards, key = { it.routine.id }) { card ->
                        val next = WeekdayMask.nextRun(card.routine.weekdays, state.today)
                        val nextLabel = next?.let { stringResource(R.string.routines_next_run, Formatters.relativeDay(context, it, state.today)) }
                        CompactRoutineCard(
                            card = card,
                            subtitle = listOfNotNull(scheduleLine(context, card.routine, state.weekStartsMonday), nextLabel).joinToString(" · "),
                            onOpen = { nav.openRoutine(card.routine.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                if (state.pausedCards.isNotEmpty()) {
                    item(key = "h-paused") { SectionHeader(stringResource(R.string.paused), Modifier.animateItem()) }
                    items(state.pausedCards, key = { it.routine.id }) { card ->
                        CompactRoutineCard(
                            card = card,
                            subtitle = scheduleLine(context, card.routine, state.weekStartsMonday),
                            onOpen = { nav.openRoutine(card.routine.id) },
                            modifier = Modifier.animateItem().alpha(0.72f),
                        )
                    }
                }
            }
        }
    }

    if (showTemplates) {
        TemplatePickerSheet(
            title = stringResource(R.string.routines_from_template),
            emptyMessage = stringResource(R.string.routines_no_templates),
            templates = templates,
            onPick = { t -> vm.fromTemplate(t) { nav.openRoutine(it) } },
            onManage = { nav.openTemplates() },
            onDismiss = { showTemplates = false },
        )
    }
}

@Composable
private fun TodayRoutineCard(
    card: RoutineCard,
    expanded: Boolean,
    weekStartsMonday: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleStep: (RoutineStepEntity) -> Unit,
    onCheckAll: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val color = listColor(card.routine.color)
    val expandLabel = stringResource(if (expanded) R.string.routines_collapse else R.string.routines_expand)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = expandLabel, onClick = onToggleExpanded)
                    .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoutineBadge(card.routine.emoji, card.progress, color, size = 52.dp, stroke = 4.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            card.routine.name, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        if (card.streak >= 2) {
                            Spacer(Modifier.width(8.dp))
                            com.riyadmondol2006.taskfern.ui.common.StreakChip(card.streak)
                        }
                    }
                    val status = when {
                        card.total == 0 -> stringResource(R.string.routines_no_steps)
                        card.complete -> stringResource(R.string.routines_done_today)
                        else -> pluralStringResource(R.plurals.routines_steps_progress, card.total, card.done, card.total)
                    }
                    Text(
                        "$status · ${scheduleLine(context, card.routine, weekStartsMonday)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (card.complete) color else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
            AnimatedVisibility(expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(Modifier.padding(bottom = 6.dp)) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    card.steps.forEach { s -> StepRow(s, onToggle = { onToggleStep(s.step) }) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onOpen) {
                            Text(stringResource(R.string.routines_details))
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        if (!card.complete && card.total > 0) {
                            TextButton(onClick = onCheckAll) {
                                Icon(Icons.Outlined.DoneAll, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.routines_mark_all_done))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One step with its tick; the whole row is the touch target. */
@Composable
fun StepRow(state: StepState, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val stateText = stringResource(if (state.checked) R.string.state_done else R.string.state_not_done)
    val alphaAnim by animateFloatAsState(if (state.checked) 0.7f else 1f, label = "stepAlpha")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onToggle)
            .padding(start = 8.dp, end = 16.dp)
            .semantics(mergeDescendants = true) { stateDescription = stateText },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskCheckbox(checked = state.checked, onToggle = onToggle, priority = 0, contentDescription = state.step.title)
        Spacer(Modifier.width(4.dp))
        Text(
            state.step.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (state.checked) muted else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (state.checked) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f).alpha(alphaAnim),
        )
    }
}

@Composable
private fun CompactRoutineCard(card: RoutineCard, subtitle: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val color = listColor(card.routine.color)
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoutineBadge(card.routine.emoji, card.progress, color, size = 44.dp, stroke = 3.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        card.routine.name, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (card.streak >= 2) {
                        Spacer(Modifier.width(8.dp))
                        com.riyadmondol2006.taskfern.ui.common.StreakChip(card.streak)
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
