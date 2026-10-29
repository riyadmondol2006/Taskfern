package com.riyadmondol2006.taskfern.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.TaskRow
import com.riyadmondol2006.taskfern.reminders.Notifications
import com.riyadmondol2006.taskfern.ui.common.EmptyState
import com.riyadmondol2006.taskfern.ui.common.fabLabel
import com.riyadmondol2006.taskfern.ui.common.Formatters
import com.riyadmondol2006.taskfern.ui.common.SectionHeader
import com.riyadmondol2006.taskfern.ui.common.TaskItem
import com.riyadmondol2006.taskfern.ui.common.taskfernViewModel
import com.riyadmondol2006.taskfern.ui.navigation.AppNavigator
import com.riyadmondol2006.taskfern.ui.navigation.SmartViewKey
import com.riyadmondol2006.taskfern.ui.tasks.SelectionTopBar
import com.riyadmondol2006.taskfern.ui.tasks.TaskEventsEffect
import com.riyadmondol2006.taskfern.ui.theme.LocalTaskfernColors
import java.time.LocalTime

@Composable
fun TodayScreen(nav: AppNavigator) {
    val vm = taskfernViewModel { c, _ -> TodayViewModel(c.tasks, c.routines, c.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val streaks by vm.streaks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    TaskEventsEffect(vm)

    var notificationsAllowed by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = Notifications.canPost(context)
        vm.refreshDay()
        onPauseOrDispose {}
    }

    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val selecting = selection.isNotEmpty()
    val allRows = state.overdue + state.dueToday + state.important

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (selecting) {
                SelectionTopBar(vm, selection, lists, allRows.map { it.task.id }, allRows.filter { it.task.id in selection }.all { it.task.pinned })
            } else {
                TopAppBar(
                    title = {},
                    actions = {
                        IconButton(onClick = nav::openSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.nav_search)) }
                        IconButton(onClick = nav::openSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.nav_settings)) }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                val addLabel = stringResource(R.string.action_add_task)
                ExtendedFloatingActionButton(
                    onClick = { nav.quickAdd(dueToday = true) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text(addLabel) },
                    expanded = !listState.canScrollBackward,
                    // Explicit label so TalkBack (and UI tests) always find the button, also when it is collapsed.
                    modifier = Modifier.fabLabel(R.string.action_add_task),
                )
            }
        },
    ) { padding ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { TodayHeader(state) }

            if (!notificationsAllowed && state.hasReminders) {
                item(key = "notif-banner") {
                    NoticeCard(
                        text = stringResource(R.string.today_notifications_off),
                        action = stringResource(R.string.today_notifications_fix),
                        onAction = nav::openSettings,
                    )
                }
            }

            if (state.overdue.isNotEmpty()) {
                item(key = "overdue-h") {
                    SectionHeader(
                        stringResource(R.string.overdue), count = state.overdue.size, color = LocalTaskfernColors.current.overdue,
                        trailing = {
                            TextButton(onClick = vm::moveOverdueToToday) { Text(stringResource(R.string.today_move_to_today)) }
                        },
                    )
                }
                items(state.overdue, key = { "o${it.task.id}" }) { row -> TodayTask(row, vm, nav, selection, streaks[row.task.id]?.current ?: 0) }
            }

            item(key = "today-h") {
                SectionHeader(stringResource(R.string.today), count = state.dueToday.size.takeIf { it > 0 })
            }
            if (state.dueToday.isEmpty() && state.overdue.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(if (state.completedToday.isEmpty()) R.string.today_empty_title else R.string.today_all_done_title),
                        message = stringResource(if (state.completedToday.isEmpty()) R.string.today_empty_message else R.string.today_all_done_message),
                    )
                }
            } else if (state.dueToday.isEmpty()) {
                item(key = "today-none") {
                    Text(
                        stringResource(R.string.today_nothing_else),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            items(state.dueToday, key = { "t${it.task.id}" }) { row -> TodayTask(row, vm, nav, selection, streaks[row.task.id]?.current ?: 0) }

            if (state.routines.isNotEmpty()) {
                item(key = "routines-h") {
                    SectionHeader(stringResource(R.string.nav_routines))
                }
                item(key = "routines") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                        items(state.routines, key = { it.id }) { r -> RoutineChip(r, onClick = { nav.openRoutine(r.id) }) }
                    }
                }
            }

            if (state.upcomingReminders.isNotEmpty()) {
                item(key = "rem-h") { SectionHeader(stringResource(R.string.today_upcoming_reminders)) }
                items(state.upcomingReminders, key = { "r${it.task.id}" }) { row ->
                    ReminderRow(row, onClick = { nav.openTask(row.task.id) })
                }
            }

            if (state.important.isNotEmpty()) {
                item(key = "imp-h") {
                    SectionHeader(stringResource(R.string.view_important), trailing = {
                        TextButton(onClick = { nav.openTaskList(SmartViewKey.IMPORTANT) }) { Text(stringResource(R.string.today_see_all)) }
                    })
                }
                items(state.important, key = { "i${it.task.id}" }) { row -> TodayTask(row, vm, nav, selection, streaks[row.task.id]?.current ?: 0) }
            }

            if (state.showCompleted && state.completedToday.isNotEmpty()) {
                item(key = "done-h") {
                    var expanded by rememberSaveable { mutableStateOf(false) }
                    Column(Modifier.animateContentSize()) {
                        SectionHeader(
                            stringResource(R.string.today_completed_today), count = state.completedToday.size,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            trailing = {
                                IconButton(onClick = { expanded = !expanded }) {
                                    Icon(
                                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                        stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                                    )
                                }
                            },
                        )
                        AnimatedVisibility(expanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.completedToday.forEach { row ->
                                    TaskItem(row, onToggle = { vm.toggle(row) }, onClick = { nav.openTask(row.task.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayTask(row: TaskRow, vm: TodayViewModel, nav: AppNavigator, selection: Set<Long>, streak: Int) {
    val selecting = selection.isNotEmpty()
    TaskItem(
        row = row,
        selected = row.task.id in selection,
        selectionMode = selecting,
        onToggle = { vm.toggle(row) },
        onClick = { if (selecting) vm.toggleSelection(row.task.id) else nav.openTask(row.task.id) },
        onLongClick = { vm.toggleSelection(row.task.id) },
        onSwipeComplete = { vm.toggle(row) },
        onSwipeDelete = { vm.delete(listOf(row.task.id)) },
        streak = streak,
    )
}

@Composable
private fun TodayHeader(state: TodayState) {
    val context = LocalContext.current
    val hour = remember(state.date) { LocalTime.now().hour }
    val greeting = stringResource(
        when (hour) {
            in 5..11 -> R.string.greeting_morning
            in 12..17 -> R.string.greeting_afternoon
            in 18..22 -> R.string.greeting_evening
            else -> R.string.greeting_night
        },
    )
    val total = state.remaining + state.completedToday.size
    val progress by animateFloatAsState(if (total == 0) 0f else state.completedToday.size.toFloat() / total, label = "progress")
    Column(Modifier.padding(horizontal = 8.dp).padding(top = 4.dp, bottom = 8.dp)) {
        Text(
            if (state.userName.isNotBlank()) "$greeting, ${state.userName}" else greeting,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            Formatters.longDate(state.date),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = MaterialTheme.shapes.large,
        ) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(58.dp),
                        strokeWidth = 6.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    )
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.remaining == 0) stringResource(R.string.today_summary_clear)
                        else context.resources.getQuantityString(R.plurals.today_summary_left, state.remaining, state.remaining),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    val parts = buildList {
                        if (state.completedToday.isNotEmpty()) add(context.resources.getQuantityString(R.plurals.today_summary_done, state.completedToday.size, state.completedToday.size))
                        if (state.overdue.isNotEmpty()) add(context.resources.getQuantityString(R.plurals.agenda_overdue, state.overdue.size, state.overdue.size))
                        if (state.routinesLeft > 0) add(context.resources.getQuantityString(R.plurals.today_routines_left, state.routinesLeft, state.routinesLeft))
                    }
                    if (parts.isNotEmpty()) {
                        Text(
                            parts.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineChip(r: RoutineSummary, onClick: () -> Unit) {
    val streakText = pluralStringResource(R.plurals.streak_days, r.streak, r.streak)
    val fraction = if (r.total == 0) 0f else r.done.toFloat() / r.total
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.widthIn(min = 150.dp, max = 220.dp).semantics(mergeDescendants = true) {
            contentDescription = "${r.name}, ${r.done} / ${r.total}" + if (r.streak >= 2) ", $streakText" else ""
        },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (r.done == r.total && r.total > 0) stringResource(R.string.today_routine_done) else "${r.done} / ${r.total}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (r.streak >= 2) com.riyadmondol2006.taskfern.ui.common.StreakChip(r.streak)
            }
            // A quiet nudge, only when there is a streak worth keeping and steps left today.
            val left = r.total - r.done
            if (left > 0 && r.streak >= 2) {
                Text(
                    pluralStringResource(R.plurals.today_keep_streak, left, left, r.streak),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun ReminderRow(row: TaskRow, onClick: () -> Unit) {
    val context = LocalContext.current
    val at = row.task.nextReminderAt ?: return
    val time = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Alarm, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(row.task.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            Formatters.dueLabel(context, time.toLocalDate(), time.toLocalTime()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoticeCard(text: String, action: String, onAction: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.NotificationsOff, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.weight(1f))
            TextButton(
                onClick = onAction,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
            ) { Text(action, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
        }
    }
}
