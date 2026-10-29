package com.riyadmondol2006.taskfern.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.SubtaskEntity
import com.riyadmondol2006.taskfern.domain.SubtaskLogic
import com.riyadmondol2006.taskfern.domain.SubtaskProgress
import com.riyadmondol2006.taskfern.ui.common.LocalSnackbarHost
import com.riyadmondol2006.taskfern.ui.common.showUndo

/**
 * The task's subtasks, right under its title and notes: progress, an always-ready "Add subtask" field (Enter adds and
 * keeps the keyboard up; pasting several lines adds one subtask per line), tick, rename in place, delete with undo and
 * move up/down. When every subtask is ticked on an open task, offers to complete the task.
 */
@Composable
internal fun SubtasksSection(subtasks: List<SubtaskEntity>, taskOpen: Boolean, vm: TaskEditorViewModel) {
    val host = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val undoLabel = stringResource(R.string.action_undo)
    val progress = SubtaskProgress.of(subtasks.map { it.done })

    FieldRow(Icons.Outlined.Checklist) {
        Text(
            if (progress.total == 0) stringResource(R.string.subtasks_title)
            else stringResource(R.string.subtasks_progress, progress.done, progress.total),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 10.dp).semantics { heading() },
        )
        if (progress.total > 0) {
            val fraction by animateFloatAsState(progress.fraction, label = "subtasks")
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp).height(4.dp),
            )
        }
    }
    // Rows line their 48 dp checkboxes up under the section icon, so titles get the full width.
    Column(Modifier.fillMaxWidth().padding(start = 3.dp, end = 8.dp)) {
        subtasks.forEachIndexed { index, s ->
            key(s.id) {
                SubtaskEditRow(
                    subtask = s,
                    canMoveUp = index > 0,
                    canMoveDown = index < subtasks.lastIndex,
                    vm = vm,
                    onDelete = {
                        vm.deleteSubtask(s)
                        scope.showUndo(host, resources.getString(R.string.subtasks_deleted), undoLabel) { vm.restoreSubtask(s) }
                    },
                )
            }
        }
        AddSubtaskField(onAdd = vm::addSubtask, onAddMany = vm::addSubtasks)
        AnimatedVisibility(taskOpen && progress.allDone) {
            AllDoneHint(onComplete = vm::toggleComplete)
        }
    }
}

@Composable
private fun SubtaskEditRow(
    subtask: SubtaskEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    vm: TaskEditorViewModel,
    onDelete: () -> Unit,
) {
    var editing by remember(subtask.id, subtask.title) { mutableStateOf(subtask.title) }
    var hadFocus by remember(subtask.id) { mutableStateOf(false) }
    var moveMenu by remember(subtask.id) { mutableStateOf(false) }

    fun commit() {
        // A blank title is not saved; the field shows the stored title again.
        if (SubtaskLogic.cleanTitle(editing) == null) editing = subtask.title
        else if (editing != subtask.title) vm.renameSubtask(subtask, editing)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = subtask.done,
            onCheckedChange = { vm.setSubtaskDone(subtask, it) },
            modifier = Modifier.semantics { contentDescription = subtask.title },
        )
        TextField(
            value = editing,
            onValueChange = { v -> editing = v.replace('\n', ' ').replace('\r', ' ') },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (subtask.done) TextDecoration.LineThrough else null,
                color = if (subtask.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit(); defaultKeyboardAction(ImeAction.Done) }),
            colors = transparentFieldColors(),
            modifier = Modifier.weight(1f).onFocusChanged { f ->
                // Save a renamed subtask when focus leaves it.
                if (f.isFocused) hadFocus = true
                else if (hadFocus) {
                    hadFocus = false
                    commit()
                }
            },
        )
        if (canMoveUp || canMoveDown) {
            Box {
                IconButton(onClick = { moveMenu = true }) {
                    Icon(Icons.Outlined.SwapVert, stringResource(R.string.subtasks_reorder, subtask.title))
                }
                DropdownMenu(moveMenu, onDismissRequest = { moveMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tasks_move_up)) },
                        leadingIcon = { Icon(Icons.Outlined.KeyboardArrowUp, null) },
                        enabled = canMoveUp,
                        onClick = { moveMenu = false; vm.moveSubtask(subtask, -1) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tasks_move_down)) },
                        leadingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, null) },
                        enabled = canMoveDown,
                        onClick = { moveMenu = false; vm.moveSubtask(subtask, 1) },
                    )
                }
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Close, stringResource(R.string.subtasks_delete, subtask.title))
        }
    }
}

/** Enter adds the line and keeps the keyboard up for the next one; a paste with line breaks adds one per line. */
@Composable
private fun AddSubtaskField(onAdd: (String) -> Unit, onAddMany: (List<String>) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary)
        }
        TextField(
            value = input,
            onValueChange = { v ->
                val lines = SubtaskLogic.pastedLines(v)
                if (lines == null) {
                    input = v
                } else {
                    if (lines.isNotEmpty()) onAddMany(lines)
                    input = ""
                }
            },
            placeholder = { Text(stringResource(R.string.subtasks_add_hint)) },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            // Handling Done ourselves keeps the focus (and the keyboard) in the field for the next subtask.
            keyboardActions = KeyboardActions(onDone = {
                if (SubtaskLogic.cleanTitle(input) != null) onAdd(input)
                input = ""
            }),
            colors = transparentFieldColors(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AllDoneHint(onComplete: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(start = 13.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.DoneAll, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.subtasks_all_done),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            TextButton(onClick = onComplete) { Text(stringResource(R.string.subtasks_complete_task)) }
        }
    }
}
