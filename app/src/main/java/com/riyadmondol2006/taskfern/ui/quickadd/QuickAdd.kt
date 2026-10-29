package com.riyadmondol2006.taskfern.ui.quickadd

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.TaskListEntity
import com.riyadmondol2006.taskfern.data.prefs.Settings
import com.riyadmondol2006.taskfern.data.prefs.SettingsRepository
import com.riyadmondol2006.taskfern.data.repo.TaskDraft
import com.riyadmondol2006.taskfern.data.repo.TaskRepository
import com.riyadmondol2006.taskfern.domain.QuickAddParser
import com.riyadmondol2006.taskfern.ui.common.DatePickerSheet
import com.riyadmondol2006.taskfern.ui.common.Formatters
import com.riyadmondol2006.taskfern.ui.common.TimePickerDialog
import com.riyadmondol2006.taskfern.ui.common.priorityColor
import com.riyadmondol2006.taskfern.ui.common.priorityLabel
import com.riyadmondol2006.taskfern.ui.common.rememberNotificationPermissionRequest
import com.riyadmondol2006.taskfern.ui.common.taskfernViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** Defaults for a Quick Add session (from a list screen, the Today screen, a widget or a share). */
data class QuickAddDefaults(
    val listId: Long? = null,
    val dueToday: Boolean = false,
    val dueTomorrow: Boolean = false,
    val text: String = "",
    /** A specific due date (e.g. the day selected in the calendar). Wins over [dueToday]/[dueTomorrow]. */
    val dueDate: LocalDate? = null,
) {
    companion object {
        val Saver: Saver<QuickAddDefaults?, Any> = Saver(
            save = { d ->
                d?.let { listOf(it.listId ?: -1L, it.dueToday, it.dueTomorrow, it.text, it.dueDate?.toEpochDay() ?: Long.MIN_VALUE) }
                    ?: listOf<Any>()
            },
            restore = { v ->
                val l = v as List<*>
                if (l.isEmpty()) null
                else QuickAddDefaults(
                    (l[0] as Long).takeIf { it >= 0 }, l[1] as Boolean, l[2] as Boolean, l[3] as String,
                    (l[4] as Long).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay),
                )
            },
        )
    }
}

class QuickAddViewModel(
    private val tasks: TaskRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {
    val lists: StateFlow<List<TaskListEntity>> = tasks.observeLists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<Settings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    fun add(draft: TaskDraft, onAdded: () -> Unit) {
        viewModelScope.launch {
            tasks.create(draft)
            settingsRepo.setLastQuickAddListId(draft.listId ?: 0)
            onAdded()
        }
    }
}

@Composable
fun QuickAddSheet(defaults: QuickAddDefaults, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        QuickAddContent(defaults, onDismiss, Modifier.navigationBarsPadding().imePadding())
    }
}

/**
 * The quick-entry form: title with keyboard focus, optional date / time / reminder / priority / list, Enter adds the
 * task and keeps the sheet open for the next one.
 */
@Composable
fun QuickAddContent(defaults: QuickAddDefaults, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val vm = taskfernViewModel { c, _ -> QuickAddViewModel(c.tasks, c.settings) }
    val lists by vm.lists.collectAsState()
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val today = remember { LocalDate.now() }

    var text by rememberSaveable { mutableStateOf(defaults.text) }
    // The screen's default date (Today tab, calendar day…) applies only when the user neither typed a date nor chose one.
    val defaultDate = remember(defaults) {
        when {
            defaults.dueDate != null -> defaults.dueDate
            defaults.dueToday -> today
            defaults.dueTomorrow -> today.plusDays(1)
            else -> null
        }
    }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var dateCleared by rememberSaveable { mutableStateOf(false) }
    var time by rememberSaveable { mutableStateOf<LocalTime?>(null) }
    var priority by rememberSaveable { mutableIntStateOf(0) }
    var listId by rememberSaveable { mutableStateOf(defaults.listId) }
    var reminderOn by rememberSaveable { mutableStateOf<Boolean?>(null) } // null = follow the setting
    var smartParsing by rememberSaveable { mutableStateOf(true) }
    var repeatDropped by rememberSaveable { mutableStateOf(false) }
    var addedCount by rememberSaveable { mutableIntStateOf(0) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var dateMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var listMenu by remember { mutableStateOf(false) }

    val parsed = remember(text, smartParsing) {
        if (smartParsing) QuickAddParser.parse(text, today) else QuickAddParser.Result(text.trim())
    }
    val effDate = if (dateCleared) null else date ?: parsed.date ?: defaultDate
    val effTime = time ?: parsed.time
    val effPriority = if (priority != 0) priority else parsed.priority ?: 0
    val effReminder = (reminderOn ?: settings.autoReminder) && effDate != null
    val effRepeat = if (repeatDropped) null else parsed.recurrence

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
    }
    val scope = rememberCoroutineScope()
    val askNotifications = rememberNotificationPermissionRequest()

    fun submit() {
        val title = parsed.title.trim()
        if (title.isEmpty()) return
        if (effReminder) askNotifications()
        vm.add(
            TaskDraft(
                title = title,
                listId = listId,
                priority = effPriority,
                dueDate = effDate,
                dueTime = effTime,
                tags = parsed.tags,
                recurrence = effRepeat,
                reminderOffsets = if (effReminder) listOf(0) else emptyList(),
            ),
        ) {
            addedCount++
        }
        text = ""
        repeatDropped = false
        time = null
        priority = 0
        reminderOn = null
        // Keep the chosen date and list for the next task; most batches share them.
        scope.launch { runCatching { focus.requestFocus() } }
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.quick_add_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            AnimatedVisibility(addedCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        context.resources.getQuantityString(R.plurals.quick_add_added, addedCount, addedCount),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.replace("\n", " ") },
                placeholder = { Text(stringResource(R.string.quick_add_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (parsed.title.isNotBlank()) submit() else onClose() }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                shape = MaterialTheme.shapes.large,
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = ::submit, enabled = parsed.title.isNotBlank(), modifier = Modifier.size(52.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.action_add_task))
            }
        }

        // What the smart parser understood, shown before saving. Tapping the chip turns parsing off for this entry.
        AnimatedVisibility(parsed.hasMatches || !smartParsing && text.isNotBlank()) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                InputChip(
                    selected = smartParsing,
                    onClick = { smartParsing = !smartParsing },
                    label = {
                        Text(
                            if (smartParsing) stringResource(R.string.quick_add_understood, parsed.recognised.joinToString(" · "))
                            else stringResource(R.string.quick_add_literal),
                            maxLines = 1,
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Date
            Column {
                FilterChip(
                    selected = effDate != null,
                    onClick = { dateMenu = true },
                    label = { Text(effDate?.let { Formatters.relativeDay(context, it, today) } ?: stringResource(R.string.quick_add_date)) },
                    leadingIcon = { Icon(Icons.Outlined.Event, null, Modifier.size(18.dp)) },
                )
                DropdownMenu(dateMenu, onDismissRequest = { dateMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.today)) }, onClick = { date = today; dateCleared = false; dateMenu = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.tomorrow)) }, onClick = { date = today.plusDays(1); dateCleared = false; dateMenu = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.quick_add_next_week)) }, onClick = {
                        date = today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)); dateCleared = false; dateMenu = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.quick_add_pick_date)) }, onClick = { dateMenu = false; showDatePicker = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.no_date)) }, onClick = {
                        date = null; time = null; dateCleared = true; dateMenu = false
                    })
                }
            }
            // Repeat (from phrases like "every monday"); tapping drops the rule for this entry.
            if (effRepeat != null || (repeatDropped && parsed.recurrence != null)) {
                FilterChip(
                    selected = effRepeat != null,
                    onClick = { repeatDropped = !repeatDropped },
                    label = {
                        Text(
                            parsed.recurrence?.let { com.riyadmondol2006.taskfern.ui.editor.RecurrenceText.describe(context, it, effDate) }
                                ?: stringResource(R.string.repeat_none),
                            maxLines = 1,
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.Repeat, null, Modifier.size(18.dp)) },
                )
            }
            // Time
            FilterChip(
                selected = effTime != null,
                onClick = { showTimePicker = true },
                label = { Text(effTime?.let { Formatters.time(context, it) } ?: stringResource(R.string.quick_add_time)) },
                leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)) },
            )
            // Reminder
            FilterChip(
                selected = effReminder,
                enabled = effDate != null,
                onClick = { reminderOn = !effReminder },
                label = { Text(stringResource(if (effReminder) R.string.quick_add_remind_on else R.string.quick_add_remind_off)) },
                leadingIcon = { Icon(if (effReminder) Icons.Outlined.Alarm else Icons.Outlined.AlarmOff, null, Modifier.size(18.dp)) },
            )
            // Priority
            Column {
                FilterChip(
                    selected = effPriority != 0,
                    onClick = { priorityMenu = true },
                    label = { Text(if (effPriority == 0) stringResource(R.string.action_priority) else priorityLabel(effPriority)) },
                    leadingIcon = { Icon(Icons.Outlined.Flag, null, Modifier.size(18.dp), tint = priorityColor(effPriority) ?: MaterialTheme.colorScheme.onSurfaceVariant) },
                )
                DropdownMenu(priorityMenu, onDismissRequest = { priorityMenu = false }) {
                    listOf(3, 2, 1, 0).forEach { p ->
                        DropdownMenuItem(
                            text = { Text(priorityLabel(p)) },
                            leadingIcon = { Icon(Icons.Outlined.Flag, null, tint = priorityColor(p) ?: MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { priority = p; priorityMenu = false },
                        )
                    }
                }
            }
            // List
            Column {
                val listName = lists.firstOrNull { it.id == listId }?.name ?: stringResource(R.string.view_inbox)
                AssistChip(
                    onClick = { listMenu = true },
                    label = { Text(listName, maxLines = 1) },
                    leadingIcon = { Icon(Icons.Outlined.Inbox, null, Modifier.size(18.dp)) },
                )
                DropdownMenu(listMenu, onDismissRequest = { listMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.view_inbox)) }, onClick = { listId = null; listMenu = false })
                    lists.forEach { l ->
                        DropdownMenuItem(text = { Text(l.name) }, onClick = { listId = l.id; listMenu = false })
                    }
                }
            }
        }
    }

    if (showDatePicker) DatePickerSheet(initial = effDate, onPick = { date = it; dateCleared = false }, onDismiss = { showDatePicker = false })
    if (showTimePicker) {
        TimePickerDialog(
            initial = effTime,
            onPick = { time = it; if (effDate == null) { date = today; dateCleared = false } },
            onDismiss = { showTimePicker = false },
            onClear = { time = null },
        )
    }
}
