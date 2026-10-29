package com.riyadmondol2006.taskfern.ui.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.TaskListEntity
import com.riyadmondol2006.taskfern.data.db.TaskRow
import com.riyadmondol2006.taskfern.data.repo.CompletionUndo
import com.riyadmondol2006.taskfern.data.repo.TaskRepository
import com.riyadmondol2006.taskfern.ui.common.DatePickerSheet
import com.riyadmondol2006.taskfern.ui.common.Formatters
import com.riyadmondol2006.taskfern.ui.common.LocalSnackbarHost
import com.riyadmondol2006.taskfern.ui.common.priorityColor
import com.riyadmondol2006.taskfern.ui.common.priorityLabel
import com.riyadmondol2006.taskfern.ui.common.showUndo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One-off UI events from task actions, turned into snackbars by [TaskEventsEffect]. */
sealed interface TaskEvent {
    data class Completed(val title: String, val undo: CompletionUndo, val nextDue: LocalDate?) : TaskEvent
    data class CompletedMany(val undos: List<CompletionUndo>) : TaskEvent
    data class Deleted(val ids: List<Long>) : TaskEvent
    data class Skipped(val undo: CompletionUndo, val nextDue: LocalDate?) : TaskEvent
}

/**
 * Shared behaviour of every screen that shows tasks: completing with undo, deleting to the trash with undo, and
 * multi-select bulk actions.
 */
open class TaskActionsViewModel(protected val tasks: TaskRepository) : ViewModel() {
    private val _events = MutableSharedFlow<TaskEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<TaskEvent> = _events

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    val lists: StateFlow<List<TaskListEntity>> = tasks.observeLists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggle(row: TaskRow) {
        viewModelScope.launch {
            if (row.task.isCompleted) {
                tasks.reopen(row.task.id)
            } else {
                val undo = tasks.complete(row.task.id) ?: return@launch
                val next = tasks.get(row.task.id)?.takeIf { !it.isCompleted }?.dueDate
                _events.emit(TaskEvent.Completed(row.task.title, undo, if (row.task.isRecurring) next else null))
            }
        }
    }

    fun delete(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            tasks.moveToTrash(ids)
            _events.emit(TaskEvent.Deleted(ids.toList()))
        }
        clearSelection()
    }

    fun skip(id: Long) {
        viewModelScope.launch {
            val undo = tasks.skipOccurrence(id) ?: return@launch
            _events.emit(TaskEvent.Skipped(undo, tasks.get(id)?.dueDate))
        }
    }

    fun undoCompletion(undo: CompletionUndo) = viewModelScope.launch { tasks.undoCompletion(undo) }
    fun undoMany(undos: List<CompletionUndo>) = viewModelScope.launch { undos.asReversed().forEach { tasks.undoCompletion(it) } }
    fun restore(ids: List<Long>) = viewModelScope.launch { tasks.restoreFromTrash(ids) }

    // ---- Selection ---------------------------------------------------------------------------------------------------

    fun toggleSelection(id: Long) = _selection.update { if (id in it) it - id else it + id }
    fun selectAll(ids: Collection<Long>) = _selection.update { ids.toSet() }
    fun clearSelection() { _selection.value = emptySet() }

    fun completeSelected() {
        val ids = _selection.value
        clearSelection()
        viewModelScope.launch {
            val undos = tasks.completeMany(ids)
            if (undos.isNotEmpty()) _events.emit(TaskEvent.CompletedMany(undos))
        }
    }

    fun setPrioritySelected(priority: Int) = bulk { tasks.setPriority(it, priority) }
    fun rescheduleSelected(date: LocalDate?) = bulk { tasks.setDueDate(it, date) }
    fun moveSelected(listId: Long?) = bulk { tasks.moveToList(it, listId) }
    fun pinSelected(pinned: Boolean) = bulk { tasks.setPinned(it, pinned) }
    fun archiveSelected() = bulk { tasks.setArchived(it, true) }

    private fun bulk(action: suspend (Set<Long>) -> Unit) {
        val ids = _selection.value
        clearSelection()
        if (ids.isNotEmpty()) viewModelScope.launch { action(ids) }
    }
}

/** Shows the snackbars (with Undo) for [TaskActionsViewModel.events]. */
@Composable
fun TaskEventsEffect(vm: TaskActionsViewModel) {
    val host = LocalSnackbarHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val undo = stringResource(R.string.action_undo)
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is TaskEvent.Completed -> scope.showUndo(
                    host,
                    if (e.nextDue != null) context.getString(R.string.snack_completed_next, Formatters.relativeDay(context, e.nextDue))
                    else context.getString(R.string.snack_completed, e.title),
                    undo,
                ) { vm.undoCompletion(e.undo) }
                is TaskEvent.CompletedMany -> scope.showUndo(
                    host, context.resources.getQuantityString(R.plurals.snack_completed_many, e.undos.size, e.undos.size), undo,
                ) { vm.undoMany(e.undos) }
                is TaskEvent.Deleted -> scope.showUndo(
                    host,
                    if (e.ids.size == 1) context.getString(R.string.snack_deleted)
                    else context.resources.getQuantityString(R.plurals.snack_deleted_many, e.ids.size, e.ids.size),
                    undo,
                ) { vm.restore(e.ids) }
                is TaskEvent.Skipped -> scope.showUndo(
                    host,
                    context.getString(R.string.snack_skipped, e.nextDue?.let { Formatters.relativeDay(context, it) } ?: "—"),
                    undo,
                ) { vm.undoCompletion(e.undo) }
            }
        }
    }
}

/** Top bar shown while tasks are selected, with the bulk actions. */
@Composable
fun SelectionTopBar(
    vm: TaskActionsViewModel,
    selected: Set<Long>,
    lists: List<TaskListEntity>,
    visibleIds: List<Long>,
    selectedPinned: Boolean,
) {
    val context = LocalContext.current
    var priorityMenu by remember { mutableStateOf(false) }
    var moveMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        navigationIcon = {
            IconButton(onClick = vm::clearSelection) { Icon(Icons.Outlined.Close, stringResource(R.string.action_close)) }
        },
        title = { Text(context.resources.getQuantityString(R.plurals.selected_count, selected.size, selected.size)) },
        actions = {
            IconButton(onClick = vm::completeSelected) { Icon(Icons.Outlined.CheckCircle, stringResource(R.string.action_complete)) }
            IconButton(onClick = { datePicker = true }) { Icon(Icons.Outlined.Event, stringResource(R.string.action_reschedule)) }
            Column {
                IconButton(onClick = { priorityMenu = true }) { Icon(Icons.Outlined.Flag, stringResource(R.string.action_priority)) }
                DropdownMenu(priorityMenu, onDismissRequest = { priorityMenu = false }) {
                    listOf(3, 2, 1, 0).forEach { p ->
                        DropdownMenuItem(
                            text = { Text(priorityLabel(p)) },
                            leadingIcon = { Icon(Icons.Outlined.Flag, null, tint = priorityColor(p) ?: MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { priorityMenu = false; vm.setPrioritySelected(p) },
                        )
                    }
                }
            }
            IconButton(onClick = { vm.delete(selected) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
            Column {
                IconButton(onClick = { moreMenu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
                DropdownMenu(moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_select_all)) },
                        leadingIcon = { Icon(Icons.Outlined.SelectAll, null) },
                        onClick = { moreMenu = false; vm.selectAll(visibleIds) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_move)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
                        onClick = { moreMenu = false; moveMenu = true },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (selectedPinned) R.string.action_unpin else R.string.action_pin)) },
                        leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                        onClick = { moreMenu = false; vm.pinSelected(!selectedPinned) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_archive)) },
                        leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                        onClick = { moreMenu = false; vm.archiveSelected() },
                    )
                }
                DropdownMenu(moveMenu, onDismissRequest = { moveMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.view_inbox)) }, onClick = { moveMenu = false; vm.moveSelected(null) })
                    lists.forEach { l ->
                        DropdownMenuItem(text = { Text(l.name) }, onClick = { moveMenu = false; vm.moveSelected(l.id) })
                    }
                }
            }
        },
    )
    if (datePicker) {
        DatePickerSheet(initial = LocalDate.now(), onPick = { vm.rescheduleSelected(it) }, onDismiss = { datePicker = false })
    }
}
