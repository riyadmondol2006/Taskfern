package com.riyadmondol2006.taskfern.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.SubtaskEntity
import kotlinx.coroutines.flow.Flow

/** What a task row needs to show its subtasks inline and tick them from the list. */
interface SubtaskRowActions {
    fun observeSubtasks(taskId: Long): Flow<List<SubtaskEntity>>
    fun toggleSubtask(subtask: SubtaskEntity)
}

/**
 * The "☑ 1/3 ⌄" chip on a task row. Small to look at, 48 dp to touch; read out as "Show 3 subtasks" or
 * "Hide subtasks", with the progress as its state.
 */
@Composable
internal fun SubtaskChip(done: Int, total: Int, expanded: Boolean, onClick: () -> Unit) {
    val label = if (expanded) stringResource(R.string.subtasks_hide) else pluralStringResource(R.plurals.subtasks_show, total, total)
    val state = pluralStringResource(R.plurals.subtasks_done_of, total, done, total)
    val tint = if (expanded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (expanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.semantics {
            contentDescription = label
            stateDescription = state
        },
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp).clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Checklist, null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(3.dp))
            Text("$done/$total", style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * A task's subtasks inside its row, each with a checkbox. The small checkboxes sit under the task's circle and the
 * titles line up with the task's title. Each line is one 48 dp toggle labelled with the subtask's title.
 */
@Composable
internal fun InlineSubtasks(taskId: Long, actions: SubtaskRowActions, modifier: Modifier = Modifier) {
    val flow = remember(taskId, actions) { actions.observeSubtasks(taskId) }
    val subtasks by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, bottom = 6.dp)) {
        subtasks.forEach { s ->
            key(s.id) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .toggleable(value = s.done, role = Role.Checkbox, onValueChange = { actions.toggleSubtask(s) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = s.done, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        s.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (s.done) muted else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (s.done) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}
