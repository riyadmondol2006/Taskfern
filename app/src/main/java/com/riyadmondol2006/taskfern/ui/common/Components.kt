package com.riyadmondol2006.taskfern.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.Priority
import com.riyadmondol2006.taskfern.data.db.TaskRow
import com.riyadmondol2006.taskfern.ui.theme.LocalTaskfernColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun priorityColor(priority: Int): Color? {
    val c = LocalTaskfernColors.current
    return when (priority) {
        Priority.HIGH -> c.priorityHigh
        Priority.MEDIUM -> c.priorityMedium
        Priority.LOW -> c.priorityLow
        else -> null
    }
}

@Composable
fun priorityLabel(priority: Int): String = stringResource(
    when (priority) {
        Priority.HIGH -> R.string.priority_high
        Priority.MEDIUM -> R.string.priority_medium
        Priority.LOW -> R.string.priority_low
        else -> R.string.priority_none
    },
)

@Composable
fun listColor(index: Int?): Color {
    val palette = LocalTaskfernColors.current.listColors
    if (index == null || palette.isEmpty()) return MaterialTheme.colorScheme.primary
    return palette[Math.floorMod(index, palette.size)]
}

/**
 * The round completion button. 48 dp touch target, ring tinted by priority, fills with a check when done.
 */
@Composable
fun TaskCheckbox(
    checked: Boolean,
    onToggle: () -> Unit,
    priority: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val ring = priorityColor(priority) ?: MaterialTheme.colorScheme.outline
    val fill by animateColorAsState(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent, label = "fill")
    val scale by animateFloatAsState(if (checked) 1f else 0.6f, label = "check")
    val haptic = LocalHapticFeedback.current
    val stateText = stringResource(if (checked) R.string.state_done else R.string.state_not_done)
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    onToggle()
                },
            )
            .semantics {
                role = Role.Checkbox
                this.contentDescription = contentDescription
                stateDescription = stateText
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(fill)
                .border(BorderStroke(2.dp, if (checked) MaterialTheme.colorScheme.primary else ring), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Outlined.Check, null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp).scale(scale),
                )
            }
        }
    }
}

/** Small icon + text used in a task's metadata line. */
@Composable
private fun MetaChip(icon: androidx.compose.ui.graphics.vector.ImageVector?, text: String?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        if (text != null) {
            if (icon != null) Spacer(Modifier.width(3.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
        }
    }
}

/**
 * A task row. Tap opens it, the circle completes it, long-press selects. Swiping right completes; swiping left
 * deletes (both undoable from the snackbar).
 */
@Composable
fun TaskItem(
    row: TaskRow,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    showList: Boolean = true,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onSwipeComplete: (() -> Unit)? = null,
    onSwipeDelete: (() -> Unit)? = null,
    /** Completed occurrences in a row for a repeating task; shown from 2 up. */
    streak: Int = 0,
) {
    val t = row.task
    val context = LocalContext.current
    val c = LocalTaskfernColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val overdue = !t.isCompleted && t.dueDate != null && (t.dueDate.isBefore(today) ||
        (t.dueDate == today && t.dueTime != null && t.dueTime.isBefore(LocalTime.now())))
    val paused = t.recurrence?.paused == true

    val content: @Composable () -> Unit = {
        Surface(
            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            Row(
                Modifier.padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskCheckbox(
                    checked = t.isCompleted || selected,
                    onToggle = if (selectionMode) onClick else onToggle,
                    priority = t.priority,
                    contentDescription = t.title,
                )
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        t.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (t.priority == Priority.HIGH && !t.isCompleted) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (t.isCompleted) muted else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (t.isCompleted) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val hasMeta = t.dueDate != null || t.isRecurring || row.subtaskTotal > 0 || row.reminderCount > 0 ||
                        (showList && row.listName != null) || t.notes.isNotBlank() || t.tags.isNotEmpty()
                    if (hasMeta) {
                        Spacer(Modifier.height(3.dp))
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (t.dueDate != null) {
                                val label = if (t.isCompleted && t.completedAt != null) {
                                    Formatters.relativeDay(context, Instant.ofEpochMilli(t.completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate(), today)
                                } else Formatters.dueLabel(context, t.dueDate, t.dueTime, today)
                                MetaChip(Icons.Outlined.Event, label, if (overdue) c.overdue else muted)
                            }
                            if (paused) MetaChip(Icons.Outlined.PauseCircle, stringResource(R.string.paused), muted)
                            else if (t.isRecurring) MetaChip(Icons.Outlined.Repeat, null, muted)
                            if (streak >= 2 && !t.isCompleted) StreakChip(streak)
                            if (row.reminderCount > 0 && !t.isCompleted) MetaChip(Icons.Outlined.Alarm, null, muted)
                            if (row.subtaskTotal > 0) MetaChip(Icons.Outlined.Checklist, "${row.subtaskDone}/${row.subtaskTotal}", muted)
                            if (t.notes.isNotBlank()) MetaChip(Icons.AutoMirrored.Outlined.Notes, null, muted)
                            if (showList && row.listName != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(listColor(row.listColor)))
                                    Spacer(Modifier.width(4.dp))
                                    Text(row.listName, style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1)
                                }
                            }
                            t.tagList.take(3).forEach { tag ->
                                Text("#$tag", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                            }
                        }
                    }
                }
                if (t.pinned) {
                    Icon(Icons.Outlined.PushPin, stringResource(R.string.pinned), tint = muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (onSwipeComplete == null && onSwipeDelete == null || selectionMode) {
        Box(modifier) { content() }
        return
    }
    val state = rememberSwipeToDismissBoxState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = onSwipeComplete != null,
        enableDismissFromEndToStart = onSwipeDelete != null,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onSwipeComplete?.invoke()
                SwipeToDismissBoxValue.EndToStart -> onSwipeDelete?.invoke()
                else -> {}
            }
            scope.launch { state.reset() }
        },
        backgroundContent = {
            val dir = state.dismissDirection
            val (bg, icon, align) = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(MaterialTheme.colorScheme.primaryContainer, Icons.Outlined.Check, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> Triple(MaterialTheme.colorScheme.errorContainer, Icons.Outlined.Delete, Alignment.CenterEnd)
                else -> Triple(Color.Transparent, null, Alignment.Center)
            }
            Box(
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(bg).padding(horizontal = 24.dp),
                contentAlignment = align,
            ) {
                if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface)
            }
        },
    ) { content() }
}

/** "🔥 6" — a run of completed days or occurrences. Read out as "6 in a row". */
@Composable
fun StreakChip(count: Int, modifier: Modifier = Modifier) {
    val label = pluralStringResource(R.plurals.streak_in_a_row, count, count)
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(2.dp))
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, maxLines = 1)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, count: Int? = null, color: Color = MaterialTheme.colorScheme.primary, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 16.dp, bottom = 6.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = color)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Friendly empty state with the Taskfern mark or a custom illustration. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    @DrawableRes illustration: Int = R.drawable.ic_taskfern_mark,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(illustration), null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondaryContainer),
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Material 3 date picker returning a [LocalDate] (the picker works in UTC midnight millis). */
@Composable
fun DatePickerSheet(
    initial: LocalDate?,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    allowPast: Boolean = true,
) {
    val today = remember { LocalDate.now() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: today).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                allowPast || !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) { DatePicker(state = state) }
}

@Composable
fun TimePickerDialog(
    initial: LocalTime?,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val start = initial ?: LocalTime.of(9, 0)
    val state = rememberTimePickerState(start.hour, start.minute, android.text.format.DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) {
                Text(stringResource(R.string.action_done))
            }
        },
        dismissButton = {
            Row {
                if (onClear != null) TextButton(onClick = { onClear(); onDismiss() }) { Text(stringResource(R.string.action_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
        text = { TimePicker(state = state) },
    )
}

/** Accessibility label for an extended FAB, whose text TalkBack does not reliably read on its own. */
@Composable
fun Modifier.fabLabel(@androidx.annotation.StringRes label: Int): Modifier {
    val text = stringResource(label)
    return this.semantics(mergeDescendants = true) { contentDescription = text }
}
