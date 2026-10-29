package com.riyadmondol2006.taskfern.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.TaskfernApp
import com.riyadmondol2006.taskfern.data.db.OccurrenceAction
import com.riyadmondol2006.taskfern.data.db.SubtaskEntity
import com.riyadmondol2006.taskfern.ui.common.DatePickerSheet
import com.riyadmondol2006.taskfern.ui.common.Formatters
import com.riyadmondol2006.taskfern.ui.common.LocalSnackbarHost
import com.riyadmondol2006.taskfern.ui.common.TaskCheckbox
import com.riyadmondol2006.taskfern.ui.common.TimePickerDialog
import com.riyadmondol2006.taskfern.ui.common.priorityColor
import com.riyadmondol2006.taskfern.ui.common.priorityLabel
import com.riyadmondol2006.taskfern.ui.common.rememberNotificationPermissionRequest
import com.riyadmondol2006.taskfern.ui.common.showMessage
import com.riyadmondol2006.taskfern.ui.common.showUndo
import com.riyadmondol2006.taskfern.ui.common.taskfernViewModel
import com.riyadmondol2006.taskfern.ui.navigation.AppNavigator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

private val reminderChoices = listOf(0, 10, 30, 60, 120, 24 * 60)

@Composable
fun TaskEditorScreen(nav: AppNavigator, taskId: Long) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val vm = taskfernViewModel(key = "editor-$taskId") { c, _ ->
        TaskEditorViewModel(c.tasks, c.settings, c.reminders, c.appScope, taskId)
    }
    val draft by vm.draft.collectAsStateWithLifecycle()
    val task by vm.task.collectAsStateWithLifecycle()
    val subtasks by vm.subtasks.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val allTags by vm.allTags.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val host = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.action_undo)

    fun leave() {
        vm.save()
        nav.back()
    }
    BackHandler { leave() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.save() }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is EditorEvent.Completed -> scope.showUndo(
                    host,
                    if (e.nextDue != null) resources.getString(R.string.snack_completed_next, Formatters.relativeDay(context, e.nextDue))
                    else resources.getString(R.string.snack_completed, draft.title),
                    undoLabel,
                ) { vm.undoCompletion(e.undo) }
                is EditorEvent.Skipped -> scope.showUndo(
                    host, resources.getString(R.string.snack_skipped, e.nextDue?.let { Formatters.relativeDay(context, it) } ?: "—"), undoLabel,
                ) { vm.undoCompletion(e.undo) }
                EditorEvent.Deleted -> {
                    // Snackbar outlives this screen because the host belongs to the root scaffold.
                    val app = context.applicationContext as TaskfernApp
                    scope.showUndo(host, resources.getString(R.string.snack_deleted), undoLabel) {
                        app.container.tasks.restoreFromTrash(listOf(taskId))
                    }
                    nav.back()
                }
                is EditorEvent.Duplicated -> {
                    scope.showMessage(host, resources.getString(R.string.editor_duplicated))
                    nav.openTask(e.id)
                }
                EditorEvent.Missing -> nav.back()
            }
        }
    }

    var moreMenu by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showRepeat by rememberSaveable { mutableStateOf(false) }
    val completed = task?.isCompleted == true
    val inTrash = task?.deletedAt != null
    val archived = task?.archived == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = { vm.edit { it.copy(pinned = !it.pinned) } }) {
                        Icon(
                            Icons.Outlined.PushPin,
                            stringResource(if (draft.pinned) R.string.action_unpin else R.string.action_pin),
                            tint = if (draft.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.save(); nav.openFocus(taskId) }) { Icon(Icons.Outlined.Timer, stringResource(R.string.editor_focus)) }
                    IconButton(onClick = vm::delete) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                    Column {
                        IconButton(onClick = { moreMenu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
                        DropdownMenu(moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_duplicate)) },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                onClick = { moreMenu = false; vm.duplicate() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(if (archived) R.string.action_unarchive else R.string.action_archive)) },
                                leadingIcon = { Icon(if (archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, null) },
                                onClick = { moreMenu = false; vm.archive(!archived) },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        ) {
            if (inTrash) {
                Notice(stringResource(R.string.editor_in_trash), stringResource(R.string.action_restore)) { vm.restoreFromTrash() }
            }
            // Title with the completion circle.
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.Top) {
                TaskCheckbox(
                    checked = completed,
                    onToggle = vm::toggleComplete,
                    priority = draft.priority,
                    contentDescription = stringResource(if (completed) R.string.action_reopen else R.string.action_complete),
                    modifier = Modifier.padding(top = 4.dp),
                )
                TextField(
                    value = draft.title,
                    onValueChange = { v -> vm.edit { it.copy(title = v.replace("\n", " ")) } },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        textDecoration = if (completed) TextDecoration.LineThrough else null,
                    ),
                    placeholder = { Text(stringResource(R.string.editor_title_hint), style = MaterialTheme.typography.headlineSmall) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    colors = transparentFieldColors(),
                    modifier = Modifier.weight(1f),
                    isError = draft.title.isBlank(),
                )
            }
            if (draft.title.isBlank()) {
                Text(
                    stringResource(R.string.editor_title_required),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 64.dp),
                )
            }

            // Notes
            FieldRow(Icons.AutoMirrored.Outlined.Notes) {
                TextField(
                    value = draft.notes,
                    onValueChange = { v -> vm.edit { it.copy(notes = v) } },
                    placeholder = { Text(stringResource(R.string.editor_notes_hint)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = transparentFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            // Date & time
            FieldRow(Icons.Outlined.Event) {
                Column {
                    val today = LocalDate.now()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateChip(stringResource(R.string.today), draft.dueDate == today) { vm.setDueDate(today) }
                        DateChip(stringResource(R.string.tomorrow), draft.dueDate == today.plusDays(1)) { vm.setDueDate(today.plusDays(1)) }
                        val nextWeek = today.with(TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
                        DateChip(stringResource(R.string.editor_next_week), draft.dueDate == nextWeek) { vm.setDueDate(nextWeek) }
                        val other = draft.dueDate != null && draft.dueDate !in setOf(today, today.plusDays(1), nextWeek)
                        FilterChip(
                            selected = other,
                            onClick = { showDate = true },
                            label = { Text(if (other) Formatters.shortDate(draft.dueDate!!) else stringResource(R.string.editor_pick_date)) },
                        )
                        if (draft.dueDate != null && draft.recurrence == null) {
                            AssistChip(onClick = { vm.setDueDate(null) }, label = { Text(stringResource(R.string.no_date)) })
                        }
                    }
                    if (draft.dueDate != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { showTime = true }) {
                                Text(draft.dueTime?.let { Formatters.time(context, it) } ?: stringResource(R.string.editor_add_time))
                            }
                            if (draft.dueTime != null) {
                                IconButton(onClick = { vm.edit { it.copy(dueTime = null) } }) {
                                    Icon(Icons.Outlined.Close, stringResource(R.string.editor_remove_time), Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Reminders
            FieldRow(Icons.Outlined.Alarm) {
                Column {
                    Text(stringResource(R.string.editor_reminders), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    val askNotifications = rememberNotificationPermissionRequest()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        reminderChoices.forEach { offset ->
                            FilterChip(
                                selected = offset in draft.reminders,
                                onClick = {
                                    if (offset !in draft.reminders) askNotifications()
                                    vm.toggleReminder(offset)
                                },
                                label = { Text(Formatters.reminderOffset(context, offset)) },
                            )
                        }
                    }
                    if (draft.reminders.isNotEmpty() && draft.dueDate != null) {
                        val timeLabel = draft.dueTime?.let { Formatters.time(context, it) } ?: Formatters.minutesOfDay(context, settings.defaultReminderMinutes)
                        Text(
                            stringResource(if (draft.dueTime == null) R.string.editor_reminder_default_time else R.string.editor_reminder_due_time, timeLabel),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!vm.exactAlarms) {
                            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.editor_inexact_hint),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.clickable { nav.openSettings() },
                                )
                            }
                        }
                    }
                }
            }

            // Repeat
            FieldRow(Icons.Outlined.Repeat) {
                Column(Modifier.fillMaxWidth()) {
                    val rule = draft.recurrence
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            rule?.let { RecurrenceText.describe(context, it, task?.seriesAnchor ?: draft.dueDate) } ?: stringResource(R.string.repeat_none),
                            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showRepeat = true }) { Text(stringResource(if (rule == null) R.string.editor_set_repeat else R.string.action_edit)) }
                    }
                    if (rule != null && task?.isRecurring == true && !completed) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = vm::skip, label = { Text(stringResource(R.string.action_skip_occurrence)) },
                                leadingIcon = { Icon(Icons.Outlined.SkipNext, null, Modifier.size(18.dp)) })
                            AssistChip(
                                onClick = { vm.setPaused(!rule.paused) },
                                label = { Text(stringResource(if (rule.paused) R.string.action_resume_series else R.string.action_pause_series)) },
                            )
                            AssistChip(onClick = { vm.setRecurrence(null) }, label = { Text(stringResource(R.string.action_end_series)) })
                        }
                        Text(
                            stringResource(R.string.editor_repeat_explain),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            // List & priority
            FieldRow(Icons.Outlined.Inbox) {
                var listMenu by remember { mutableStateOf(false) }
                Column {
                    TextButton(onClick = { listMenu = true }) {
                        Text(lists.firstOrNull { it.id == draft.listId }?.name ?: stringResource(R.string.view_inbox))
                    }
                    DropdownMenu(listMenu, onDismissRequest = { listMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.view_inbox)) }, onClick = { listMenu = false; vm.edit { it.copy(listId = null) } })
                        lists.forEach { l -> DropdownMenuItem(text = { Text(l.name) }, onClick = { listMenu = false; vm.edit { it.copy(listId = l.id) } }) }
                    }
                }
            }
            FieldRow(Icons.Outlined.Flag, tint = priorityColor(draft.priority)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 1, 2, 3).forEach { p ->
                        FilterChip(selected = draft.priority == p, onClick = { vm.edit { it.copy(priority = p) } }, label = { Text(priorityLabel(p)) })
                    }
                }
            }

            // Tags
            FieldRow(Icons.AutoMirrored.Outlined.Label) {
                TagEditor(draft.tags, allTags) { tags -> vm.edit { it.copy(tags = tags) } }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            // Checklist
            FieldRow(Icons.Outlined.Checklist) {
                Checklist(subtasks, vm)
            }

            // History
            if (task != null) {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                FieldRow(Icons.Outlined.History) {
                    Column {
                        val zone = ZoneId.systemDefault()
                        fun stamp(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).let {
                            Formatters.shortDate(it.toLocalDate()) + " " + Formatters.time(context, it.toLocalTime())
                        }
                        Meta(stringResource(R.string.editor_created, stamp(task!!.createdAt)))
                        task!!.completedAt?.takeIf { completed }?.let { Meta(stringResource(R.string.editor_completed_at, stamp(it))) }
                        if (log.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(R.string.editor_series_history), style = MaterialTheme.typography.titleSmall)
                            log.take(12).forEach { entry ->
                                Meta(
                                    stringResource(
                                        if (entry.action == OccurrenceAction.SKIPPED) R.string.editor_log_skipped else R.string.editor_log_completed,
                                        Formatters.shortDate(entry.occurrenceDate),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDate) {
        DatePickerSheet(initial = draft.dueDate, onPick = { vm.setDueDate(it) }, onDismiss = { showDate = false })
    }
    if (showTime) {
        TimePickerDialog(
            initial = draft.dueTime,
            onPick = { t -> vm.edit { it.copy(dueTime = t, dueDate = it.dueDate ?: LocalDate.now()) } },
            onDismiss = { showTime = false },
            onClear = { vm.edit { it.copy(dueTime = null) } },
        )
    }
    if (showRepeat) {
        RepeatSheet(
            initial = draft.recurrence,
            anchor = draft.dueDate ?: LocalDate.now(),
            onSave = vm::setRecurrence,
            onDismiss = { showRepeat = false },
        )
    }
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
)

@Composable
private fun FieldRow(icon: ImageVector, tint: Color? = null, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp).size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(top = 4.dp)) { content() }
    }
}

@Composable
private fun DateChip(label: String, selected: Boolean, onClick: () -> Unit) =
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })

@Composable
private fun Meta(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 1.dp))

@Composable
private fun Notice(text: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun TagEditor(tags: List<String>, allTags: List<String>, onChange: (List<String>) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { t ->
                InputChip(
                    selected = true,
                    onClick = { onChange(tags - t) },
                    label = { Text("#$t") },
                    trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.editor_remove_tag, t), Modifier.size(16.dp)) },
                )
            }
        }
        fun commit() {
            val clean = input.trim().trimStart('#').lowercase().replace(Regex("\\s+"), "-")
            if (clean.isNotEmpty() && clean !in tags) onChange(tags + clean)
            input = ""
        }
        TextField(
            value = input,
            onValueChange = { v -> if (v.endsWith(" ") || v.endsWith(",")) { input = v.dropLast(1); commit() } else input = v },
            placeholder = { Text(stringResource(R.string.editor_add_tag)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            colors = transparentFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        val suggestions = allTags.filter { it !in tags && (input.isBlank() || it.startsWith(input.trim().trimStart('#').lowercase())) }.take(6)
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { s -> AssistChip(onClick = { onChange(tags + s); input = "" }, label = { Text("#$s") }) }
            }
        }
    }
}

@Composable
private fun Checklist(subtasks: List<SubtaskEntity>, vm: TaskEditorViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    Column {
        if (subtasks.isNotEmpty()) {
            Text(
                stringResource(R.string.editor_checklist_progress, subtasks.count { it.done }, subtasks.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        subtasks.forEachIndexed { index, s ->
            var editing by remember(s.id) { mutableStateOf(s.title) }
            var hadFocus by remember(s.id) { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = s.done, onCheckedChange = { vm.toggleSubtask(s) })
                TextField(
                    value = editing,
                    onValueChange = { editing = it.replace("\n", " ") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (s.done) TextDecoration.LineThrough else null),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.renameSubtask(s, editing) }),
                    colors = transparentFieldColors(),
                    modifier = Modifier.weight(1f).onFocusChanged { f ->
                        // Save a renamed step when focus leaves it.
                        if (f.isFocused) hadFocus = true
                        else if (hadFocus) {
                            hadFocus = false
                            if (editing != s.title) vm.renameSubtask(s, editing)
                        }
                    },
                )
                if (index > 0) IconButton(onClick = { vm.moveSubtask(s, -1) }) { Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.tasks_move_up)) }
                if (index < subtasks.lastIndex) IconButton(onClick = { vm.moveSubtask(s, 1) }) { Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.tasks_move_down)) }
                IconButton(onClick = { vm.deleteSubtask(s) }) { Icon(Icons.Outlined.Close, stringResource(R.string.editor_remove_step, s.title)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp))
            TextField(
                value = input,
                onValueChange = { input = it.replace("\n", " ") },
                placeholder = { Text(stringResource(R.string.editor_add_step)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (input.isNotBlank()) { vm.addSubtask(input); input = "" } }),
                colors = transparentFieldColors(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
