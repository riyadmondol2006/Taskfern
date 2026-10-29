package com.riyadmondol2006.taskfern.ui.routines

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.ui.common.Formatters
import com.riyadmondol2006.taskfern.ui.common.TimePickerDialog
import com.riyadmondol2006.taskfern.ui.common.taskfernViewModel
import com.riyadmondol2006.taskfern.ui.navigation.AppNavigator
import com.riyadmondol2006.taskfern.ui.shopping.ColorDots
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Emoji offered in the picker; anything else can be typed into the field next to them. */
private val EMOJI_CHOICES = listOf(
    "🌿", "🌱", "🍃", "🌸", "🌻", "🌞", "☀️", "🌙", "⭐", "🌊", "💧", "🍵",
    "☕", "🥗", "🍎", "🏃", "🧘", "💪", "🚲", "📚", "✍️", "🧹", "🛏️", "💊",
)

@Composable
fun RoutineEditorScreen(nav: AppNavigator, routineId: Long) {
    val vm = taskfernViewModel(key = "routine-editor-$routineId") { c, _ -> RoutineEditorViewModel(c.routines, c.settings, routineId) }
    val form by vm.form.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    val showErrors by vm.showErrors.collectAsStateWithLifecycle()
    val weekStartsMonday by vm.weekStartsMonday.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var left by remember { mutableStateOf(false) }
    fun leave() {
        if (!left) {
            left = true
            nav.back()
        }
    }
    LaunchedEffect(missing) { if (missing) leave() }

    val dirty = remember(form, loaded) { loaded && vm.hasChanges }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var newStep by rememberSaveable { mutableStateOf("") }
    val newStepFocus = remember { FocusRequester() }
    val nameFocus = remember { FocusRequester() }

    fun close() {
        if (dirty) confirmDiscard = true else leave()
    }
    BackHandler(enabled = dirty && !left) { confirmDiscard = true }

    fun commitNewStep() {
        if (newStep.isNotBlank()) {
            vm.addSteps(newStep)
            newStep = ""
        }
    }

    fun save() {
        commitNewStep()
        vm.save { id ->
            if (!left) {
                left = true
                nav.back()
                if (vm.isNew) nav.openRoutine(id)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (vm.isNew) {
            delay(150)
            runCatching { nameFocus.requestFocus() }
        }
    }

    // Reminders need the notification permission on Android 13+. Ask when a time is chosen, not before.
    var notificationsAllowed by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = ::close) { Icon(Icons.Outlined.Close, stringResource(R.string.action_close)) }
                },
                title = { Text(stringResource(if (vm.isNew) R.string.routines_new else R.string.routines_edit)) },
                actions = {
                    TextButton(onClick = ::save, enabled = loaded) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Button(
                    onClick = ::save,
                    enabled = loaded,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .height(52.dp),
                ) { Text(stringResource(if (vm.isNew) R.string.routines_create else R.string.action_save)) }
            }
        },
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding))
        } else Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Name
            OutlinedTextField(
                value = form.name,
                onValueChange = vm::setName,
                label = { Text(stringResource(R.string.routines_name)) },
                placeholder = { Text(stringResource(R.string.routines_name_hint)) },
                isError = showErrors && form.nameError,
                supportingText = if (showErrors && form.nameError) ({ Text(stringResource(R.string.routines_name_required)) }) else null,
                singleLine = true,
                leadingIcon = { Text(form.emoji.ifBlank { "🌿" }, style = MaterialTheme.typography.titleMedium) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { runCatching { newStepFocus.requestFocus() } }),
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
            )

            // Steps
            EditorSection(stringResource(R.string.routines_steps))
            if (showErrors && form.stepsError) {
                Text(
                    stringResource(R.string.routines_steps_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    form.steps.forEachIndexed { index, step ->
                        StepEditorRow(
                            index = index,
                            title = step.title,
                            canMoveUp = index > 0,
                            canMoveDown = index < form.steps.lastIndex,
                            onRename = { vm.renameStep(step.key, it) },
                            onMove = { vm.moveStep(step.key, it) },
                            onDelete = { vm.deleteStep(step.key) },
                        )
                    }
                    Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary)
                        TextField(
                            value = newStep,
                            onValueChange = { text ->
                                if (text.contains('\n')) {
                                    // A pasted list (or Enter on some keyboards) becomes one step per line.
                                    vm.addSteps(text)
                                    newStep = ""
                                } else newStep = text.take(120)
                            },
                            placeholder = { Text(stringResource(R.string.routines_add_step)) },
                            singleLine = false,
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { commitNewStep() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f).focusRequester(newStepFocus),
                        )
                        IconButton(onClick = ::commitNewStep, enabled = newStep.isNotBlank()) {
                            Icon(Icons.Outlined.Add, stringResource(R.string.routines_add_step))
                        }
                    }
                }
            }

            // Days
            EditorSection(stringResource(R.string.routines_days))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeekdayMask.Preset.entries.forEach { preset ->
                    FilterChip(
                        selected = WeekdayMask.preset(form.weekdays) == preset,
                        onClick = { vm.setWeekdays(preset.mask) },
                        label = {
                            Text(
                                stringResource(
                                    when (preset) {
                                        WeekdayMask.Preset.EVERY_DAY -> R.string.routines_every_day
                                        WeekdayMask.Preset.WEEKDAYS -> R.string.routines_weekdays
                                        WeekdayMask.Preset.WEEKENDS -> R.string.routines_weekends
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                WeekdayMask.week(weekStartsMonday).forEach { day ->
                    val on = WeekdayMask.contains(form.weekdays, day)
                    val name = dayFullName(day)
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { vm.setWeekdays(WeekdayMask.toggle(form.weekdays, day)) }
                            .semantics {
                                role = Role.Checkbox
                                selected = on
                                contentDescription = name
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            dayInitial(day),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (showErrors && form.daysError) {
                Text(
                    stringResource(R.string.routines_days_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }

            // Reminder time
            EditorSection(stringResource(R.string.routines_time))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                onClick = { showTimePicker = true },
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Alarm, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(
                            form.timeMinutes?.let { Formatters.minutesOfDay(context, it) } ?: stringResource(R.string.routines_no_time),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(if (form.timeMinutes == null) R.string.routines_time_help_off else R.string.routines_time_help_on),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (form.timeMinutes != null) {
                        IconButton(onClick = { vm.setTime(null) }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.routines_remove_time))
                        }
                    }
                }
            }
            if (form.timeMinutes != null && !notificationsAllowed) {
                Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.NotificationsOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.routines_notifications_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        TextButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text(stringResource(R.string.routines_allow))
                        }
                    }
                }
            }

            // Look
            EditorSection(stringResource(R.string.routines_icon))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EMOJI_CHOICES.forEach { e ->
                    val chosen = form.emoji == e
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (chosen) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .then(if (chosen) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                            .clickable { vm.setEmoji(e) }
                            .semantics {
                                role = Role.RadioButton
                                selected = chosen
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center) }
                }
            }
            Spacer(Modifier.height(8.dp))
            var custom by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(
                value = custom,
                onValueChange = { text ->
                    val g = firstGrapheme(text)
                    custom = g
                    if (g.isNotEmpty()) vm.setEmoji(g)
                },
                label = { Text(stringResource(R.string.routines_custom_emoji)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            EditorSection(stringResource(R.string.shopping_list_color))
            ColorDots(selected = form.color, onSelect = vm::setColor)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initial = form.timeMinutes?.let { LocalTime.of(it / 60, it % 60) },
            onPick = { t ->
                vm.setTime(t.hour * 60 + t.minute)
                if (!notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    scope.launch { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }
            },
            onDismiss = { showTimePicker = false },
            onClear = if (form.timeMinutes != null) ({ vm.setTime(null) }) else null,
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.routines_discard_title)) },
            text = { Text(stringResource(R.string.routines_discard_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; leave() }) {
                    Text(stringResource(R.string.routines_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.routines_keep_editing)) } },
        )
    }
}

@Composable
private fun EditorSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun StepEditorRow(
    index: Int,
    title: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRename: (String) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${index + 1}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(24.dp),
        )
        TextField(
            value = title,
            onValueChange = onRename,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.routines_step_blank)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
            Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.routines_step_up, title))
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
            Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.routines_step_down, title))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Close, stringResource(R.string.routines_step_delete, title))
        }
    }
}
