package com.riyadmondol2006.taskfern.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.TaskListEntity
import com.riyadmondol2006.taskfern.data.db.TaskRow
import com.riyadmondol2006.taskfern.data.prefs.SettingsRepository
import com.riyadmondol2006.taskfern.data.prefs.TaskSort
import com.riyadmondol2006.taskfern.data.repo.TaskRepository
import com.riyadmondol2006.taskfern.ui.common.ConfirmDialog
import com.riyadmondol2006.taskfern.ui.common.EmptyState
import com.riyadmondol2006.taskfern.ui.common.Formatters
import com.riyadmondol2006.taskfern.ui.common.SectionHeader
import com.riyadmondol2006.taskfern.ui.common.TaskItem
import com.riyadmondol2006.taskfern.ui.common.priorityLabel
import com.riyadmondol2006.taskfern.ui.common.taskfernViewModel
import com.riyadmondol2006.taskfern.ui.navigation.AppNavigator
import com.riyadmondol2006.taskfern.ui.navigation.SmartViewKey
import com.riyadmondol2006.taskfern.ui.theme.LocalTaskfernColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What a section of the list is about; the UI turns it into a label. */
sealed interface SectionKey {
    data class Day(val date: LocalDate?) : SectionKey
    data class Bucket(val bucket: TaskFilters.Bucket) : SectionKey
}

/** Sections of a task list screen, ready to render. */
data class TaskListState(
    val loaded: Boolean = false,
    val sections: List<Pair<SectionKey?, List<TaskRow>>> = emptyList(),
    val list: TaskListEntity? = null,
    val sort: TaskSort = TaskSort.SMART,
    val refinement: TaskFilters.Refinement = TaskFilters.Refinement(),
    val tags: List<String> = emptyList(),
    val weekStartsMonday: Boolean = true,
) {
    val rows: List<TaskRow> get() = sections.flatMap { it.second }
}

/** Which flavour of list the screen shows. */
private enum class Mode { OPEN, COMPLETED, TRASH, ARCHIVED }

class TaskListViewModel(
    tasks: TaskRepository,
    private val settings: SettingsRepository,
    val view: String,
    val listId: Long,
) : TaskActionsViewModel(tasks) {

    private val mode = when (view) {
        SmartViewKey.COMPLETED -> Mode.COMPLETED
        SmartViewKey.TRASH -> Mode.TRASH
        SmartViewKey.ARCHIVED -> Mode.ARCHIVED
        else -> Mode.OPEN
    }

    val refinement = MutableStateFlow(TaskFilters.Refinement())
    val reorderMode = MutableStateFlow(false)

    private val source: Flow<List<TaskRow>> = when (mode) {
        Mode.OPEN -> tasks.observeOpenRows()
        Mode.COMPLETED -> tasks.observeCompleted(1000)
        Mode.TRASH -> tasks.observeTrash()
        Mode.ARCHIVED -> tasks.observeArchived()
    }

    private val listFlow: Flow<TaskListEntity?> =
        if (view == SmartViewKey.LIST) combine(tasks.observeLists(), flowOf(listId)) { l, id -> l.firstOrNull { it.id == id } } else flowOf(null)

    val state: StateFlow<TaskListState> = combine(source, settings.settings, refinement, listFlow, tasks.observeTags()) { rows, s, refine, list, tags ->
        val today = LocalDate.now()
        val sort = if (view == SmartViewKey.RECENT) TaskSort.CREATED else s.taskSort
        val sections: List<Pair<SectionKey?, List<TaskRow>>> = when (mode) {
            Mode.OPEN -> {
                val filtered = TaskFilters.refine(
                    TaskFilters.filter(view, rows, today, s.weekStartsMonday, listId, System.currentTimeMillis() - 7L * 24 * 3600 * 1000),
                    refine,
                )
                val sorted = TaskFilters.sort(filtered, sort)
                when {
                    view == SmartViewKey.UPCOMING || view == SmartViewKey.THIS_WEEK ->
                        TaskFilters.groupByDay(sorted).map { (d, r) -> SectionKey.Day(d) to r }
                    sort == TaskSort.SMART && view in setOf(SmartViewKey.LIST, SmartViewKey.INBOX, SmartViewKey.ALL, SmartViewKey.IMPORTANT) ||
                        view.startsWith(SmartViewKey.TAG_PREFIX) && sort == TaskSort.SMART ->
                        TaskFilters.groupByBucket(sorted, today, s.weekStartsMonday).map { (b, r) -> SectionKey.Bucket(b) to r }
                    else -> listOf(null to sorted)
                }
            }
            Mode.COMPLETED -> rows.groupBy {
                Instant.ofEpochMilli(it.task.completedAt ?: 0).atZone(ZoneId.systemDefault()).toLocalDate()
            }.map { (d, r) -> SectionKey.Day(d) to r }
            else -> listOf(null to rows)
        }
        TaskListState(true, sections.filter { it.second.isNotEmpty() }, list, sort, refine, tags, s.weekStartsMonday)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListState())

    fun setSort(sort: TaskSort) = viewModelScope.launch { settings.setTaskSort(sort) }

    fun move(row: TaskRow, delta: Int) {
        val ordered = state.value.rows.map { it.task.id }.toMutableList()
        val i = ordered.indexOf(row.task.id)
        val j = i + delta
        if (i < 0 || j !in ordered.indices) return
        ordered.add(j, ordered.removeAt(i))
        viewModelScope.launch {
            if (state.value.sort != TaskSort.MANUAL) settings.setTaskSort(TaskSort.MANUAL)
            tasks.reorder(ordered)
        }
    }

    fun restore(row: TaskRow) = viewModelScope.launch { tasks.restoreFromTrash(listOf(row.task.id)) }
    fun deleteForever(row: TaskRow) = viewModelScope.launch { tasks.deleteForever(listOf(row.task.id)) }
    fun emptyTrash() = viewModelScope.launch { tasks.emptyTrash() }
    fun unarchive(row: TaskRow) = viewModelScope.launch { tasks.setArchived(listOf(row.task.id), false) }
    fun renameList(name: String, color: Int) = state.value.list?.let { l -> viewModelScope.launch { tasks.updateList(l.copy(name = name, color = color)) } }
    fun deleteList(keepTasks: Boolean) = viewModelScope.launch { tasks.deleteList(listId, keepTasks) }

    val isOpenMode get() = mode == Mode.OPEN
    val isTrash get() = mode == Mode.TRASH
    val isArchived get() = mode == Mode.ARCHIVED
    val isCompleted get() = mode == Mode.COMPLETED
}

@Composable
fun TaskListScreen(nav: AppNavigator, view: String, listId: Long) {
    val vm = taskfernViewModel(key = "$view/$listId") { c, _ -> TaskListViewModel(c.tasks, c.settings, view, listId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val reorder by vm.reorderMode.collectAsStateWithLifecycle()
    TaskEventsEffect(vm)

    val title = when {
        view == SmartViewKey.LIST -> state.list?.name ?: ""
        view.startsWith(SmartViewKey.TAG_PREFIX) -> "#" + view.removePrefix(SmartViewKey.TAG_PREFIX)
        else -> smartViewLabel(view)?.let { stringResource(it) } ?: ""
    }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var filterSheet by rememberSaveable { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var editList by remember { mutableStateOf(false) }
    var deleteList by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    val selecting = selection.isNotEmpty()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (selecting) {
                SelectionTopBar(vm, selection, lists, state.rows.map { it.task.id }, state.rows.filter { it.task.id in selection }.all { it.task.pinned })
            } else {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        if (vm.isOpenMode) {
                            IconButton(onClick = { filterSheet = true }) {
                                Icon(
                                    Icons.Outlined.FilterList, stringResource(R.string.tasks_filter),
                                    tint = if (state.refinement.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (view != SmartViewKey.RECENT) {
                                Column {
                                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.tasks_sort)) }
                                    DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                                        TaskSort.entries.forEach { s ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(sortLabel(s))) },
                                                trailingIcon = { if (s == state.sort) Icon(Icons.Outlined.Check, null) },
                                                onClick = { sortMenu = false; vm.setSort(s) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (vm.isTrash && state.rows.isNotEmpty()) {
                            TextButton(onClick = { confirmEmpty = true }) { Text(stringResource(R.string.action_empty_trash)) }
                        }
                        if (vm.isOpenMode) {
                            Column {
                                IconButton(onClick = { moreMenu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
                                DropdownMenu(moreMenu, onDismissRequest = { moreMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(if (reorder) R.string.tasks_reorder_done else R.string.tasks_reorder)) },
                                        onClick = { moreMenu = false; vm.reorderMode.value = !reorder },
                                    )
                                    if (view == SmartViewKey.LIST && state.list != null) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.tasks_edit_list)) }, onClick = { moreMenu = false; editList = true })
                                        DropdownMenuItem(text = { Text(stringResource(R.string.tasks_delete_list)) }, onClick = { moreMenu = false; deleteList = true })
                                    }
                                }
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (vm.isOpenMode && !selecting) {
                FloatingActionButton(onClick = {
                    nav.quickAdd(
                        listId = if (view == SmartViewKey.LIST) listId else null,
                        dueToday = view == SmartViewKey.TODAY,
                    )
                }) { Icon(Icons.Filled.Add, stringResource(R.string.action_add_task)) }
            }
        },
    ) { padding ->
        if (state.loaded && state.rows.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = stringResource(
                        when {
                            vm.isTrash -> R.string.tasks_empty_trash_title
                            vm.isArchived -> R.string.tasks_empty_archive_title
                            vm.isCompleted -> R.string.tasks_empty_completed_title
                            state.refinement.isActive -> R.string.tasks_empty_filtered_title
                            else -> R.string.tasks_empty_title
                        },
                    ),
                    message = stringResource(
                        when {
                            vm.isTrash -> R.string.tasks_empty_trash_message
                            vm.isArchived -> R.string.tasks_empty_archive_message
                            vm.isCompleted -> R.string.tasks_empty_completed_message
                            state.refinement.isActive -> R.string.tasks_empty_filtered_message
                            else -> R.string.tasks_empty_message
                        },
                    ),
                )
            }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (vm.isTrash) {
                item(key = "trash-note") {
                    Text(
                        stringResource(R.string.tasks_trash_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            state.sections.forEach { (key, rows) ->
                if (key != null) {
                    item(key = "h-$key") {
                        val overdue = key == SectionKey.Bucket(TaskFilters.Bucket.OVERDUE) ||
                            (key is SectionKey.Day && key.date?.isBefore(LocalDate.now()) == true && vm.isOpenMode)
                        SectionHeader(
                            sectionLabel(key), count = rows.size,
                            color = if (overdue) LocalTaskfernColors.current.overdue else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                items(rows, key = { it.task.id }) { row ->
                    when {
                        vm.isTrash -> TrashRow(row, onRestore = { vm.restore(row) }, onDelete = { vm.deleteForever(row) })
                        vm.isArchived -> TrashRow(row, onRestore = { vm.unarchive(row) }, onDelete = null, restoreIsUnarchive = true, onOpen = { nav.openTask(row.task.id) })
                        reorder -> ReorderRow(row, onUp = { vm.move(row, -1) }, onDown = { vm.move(row, 1) })
                        else -> TaskItem(
                            row = row,
                            showList = view != SmartViewKey.LIST,
                            selected = row.task.id in selection,
                            selectionMode = selecting,
                            onToggle = { vm.toggle(row) },
                            onClick = { if (selecting) vm.toggleSelection(row.task.id) else nav.openTask(row.task.id) },
                            onLongClick = if (vm.isOpenMode) ({ vm.toggleSelection(row.task.id) }) else null,
                            onSwipeComplete = if (vm.isOpenMode) ({ vm.toggle(row) }) else null,
                            onSwipeDelete = { vm.delete(listOf(row.task.id)) },
                        )
                    }
                }
            }
        }
    }

    if (filterSheet) {
        FilterSheet(state.refinement, state.tags, onChange = { vm.refinement.value = it }, onDismiss = { filterSheet = false })
    }
    if (confirmEmpty) {
        ConfirmDialog(
            title = stringResource(R.string.action_empty_trash),
            message = pluralStringResource(R.plurals.tasks_empty_trash_confirm, state.rows.size, state.rows.size),
            confirmLabel = stringResource(R.string.action_delete_forever),
            onConfirm = vm::emptyTrash,
            onDismiss = { confirmEmpty = false },
        )
    }
    if (editList) state.list?.let { l -> ListEditDialog(l, onDismiss = { editList = false }, onSave = { n, c -> vm.renameList(n, c) }) }
    if (deleteList) {
        ConfirmDialog(
            title = stringResource(R.string.tasks_delete_list_title, state.list?.name ?: ""),
            message = stringResource(R.string.tasks_delete_list_moves),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { vm.deleteList(keepTasks = true); nav.back() },
            onDismiss = { deleteList = false },
        )
    }
}

@Composable
private fun sectionLabel(key: SectionKey): String = when (key) {
    is SectionKey.Day -> key.date?.let { Formatters.relativeDay(LocalContext.current, it) } ?: stringResource(R.string.no_date)
    is SectionKey.Bucket -> stringResource(
        when (key.bucket) {
            TaskFilters.Bucket.OVERDUE -> R.string.overdue
            TaskFilters.Bucket.TODAY -> R.string.today
            TaskFilters.Bucket.TOMORROW -> R.string.tomorrow
            TaskFilters.Bucket.THIS_WEEK -> R.string.tasks_bucket_this_week
            TaskFilters.Bucket.LATER -> R.string.tasks_bucket_later
            TaskFilters.Bucket.NO_DATE -> R.string.no_date
        },
    )
}

private fun sortLabel(s: TaskSort): Int = when (s) {
    TaskSort.SMART -> R.string.sort_smart
    TaskSort.DUE_DATE -> R.string.sort_due
    TaskSort.PRIORITY -> R.string.sort_priority
    TaskSort.CREATED -> R.string.sort_created
    TaskSort.TITLE -> R.string.sort_title
    TaskSort.MANUAL -> R.string.sort_manual
}

@Composable
private fun TrashRow(
    row: TaskRow,
    onRestore: () -> Unit,
    onDelete: (() -> Unit)?,
    restoreIsUnarchive: Boolean = false,
    onOpen: (() -> Unit)? = null,
) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        onClick = onOpen ?: {},
        enabled = onOpen != null,
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(row.task.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onRestore) {
                Icon(
                    if (restoreIsUnarchive) Icons.Outlined.Unarchive else Icons.Outlined.Restore,
                    stringResource(if (restoreIsUnarchive) R.string.action_unarchive else R.string.action_restore),
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteForever, stringResource(R.string.action_delete_forever)) }
            }
        }
    }
}

@Composable
private fun ReorderRow(row: TaskRow, onUp: () -> Unit, onDown: () -> Unit) {
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(row.task.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onUp) { Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.tasks_move_up)) }
            IconButton(onClick = onDown) { Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.tasks_move_down)) }
        }
    }
}

@Composable
private fun FilterSheet(
    current: TaskFilters.Refinement,
    tags: List<String>,
    onChange: (TaskFilters.Refinement) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tasks_filter), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (current.isActive) TextButton(onClick = { onChange(TaskFilters.Refinement()) }) { Text(stringResource(R.string.action_clear)) }
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.action_priority), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 2, 1, 0).forEach { p ->
                    FilterChip(
                        selected = p in current.priorities,
                        onClick = { onChange(current.copy(priorities = if (p in current.priorities) current.priorities - p else current.priorities + p)) },
                        label = { Text(priorityLabel(p)) },
                    )
                }
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.tasks_tags), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { t ->
                        FilterChip(
                            selected = t in current.tags,
                            onClick = { onChange(current.copy(tags = if (t in current.tags) current.tags - t else current.tags + t)) },
                            label = { Text("#$t") },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tasks_filter_reminder), modifier = Modifier.weight(1f))
                Switch(checked = current.onlyWithReminder, onCheckedChange = { onChange(current.copy(onlyWithReminder = it)) })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tasks_filter_recurring), modifier = Modifier.weight(1f))
                Switch(checked = current.onlyRecurring, onCheckedChange = { onChange(current.copy(onlyRecurring = it)) })
            }
        }
    }
}
