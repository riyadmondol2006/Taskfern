package com.riyadmondol2006.taskfern.ui.settings

import android.content.res.Resources
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadmondol2006.taskfern.AppContainer
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.backup.BackupCounts
import com.riyadmondol2006.taskfern.backup.BackupError
import com.riyadmondol2006.taskfern.backup.BackupFile
import com.riyadmondol2006.taskfern.backup.BackupReadResult
import com.riyadmondol2006.taskfern.backup.BackupService
import com.riyadmondol2006.taskfern.backup.IcsExporter
import com.riyadmondol2006.taskfern.backup.counts
import com.riyadmondol2006.taskfern.ui.common.LocalSnackbarHost
import com.riyadmondol2006.taskfern.ui.common.showMessage
import com.riyadmondol2006.taskfern.ui.common.taskfernViewModel
import com.riyadmondol2006.taskfern.ui.navigation.AppNavigator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the backup screen is showing. */
sealed interface BackupUiState {
    data object Idle : BackupUiState
    data class Working(val what: Work) : BackupUiState
    data class Preview(val file: BackupFile, val current: BackupCounts, val restoreSettings: Boolean) : BackupUiState
    data class ReadFailed(val error: BackupError) : BackupUiState
    data class WriteFailed(val detail: String) : BackupUiState
    data class ImportFailed(val detail: String) : BackupUiState
    data class Exported(val counts: BackupCounts) : BackupUiState
    data class Merged(val added: BackupCounts, val skipped: BackupCounts) : BackupUiState
    data class Replaced(val counts: BackupCounts) : BackupUiState

    enum class Work { EXPORTING, CALENDAR, READING, IMPORTING }
}

class BackupViewModel(private val container: AppContainer, private val service: BackupService) : ViewModel() {
    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** Work runs in the app scope: leaving the screen must not stop an import halfway through its follow-up steps. */
    private fun run(work: BackupUiState.Work, block: suspend () -> BackupUiState) {
        if (_state.value is BackupUiState.Working) return
        _state.value = BackupUiState.Working(work)
        container.appScope.launch {
            _state.value = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val detail = e.message ?: e.javaClass.simpleName
                when (work) {
                    BackupUiState.Work.EXPORTING, BackupUiState.Work.CALENDAR -> BackupUiState.WriteFailed(detail)
                    BackupUiState.Work.READING -> BackupUiState.ReadFailed(BackupError.Unreadable(detail))
                    BackupUiState.Work.IMPORTING -> BackupUiState.ImportFailed(detail)
                }
            }
        }
    }

    fun export(uri: Uri) = run(BackupUiState.Work.EXPORTING) { BackupUiState.Exported(service.export(uri)) }

    /** Calendar export reports through [onDone] (a count) instead of replacing the screen state. */
    fun exportCalendar(uri: Uri, onDone: (Int) -> Unit) = run(BackupUiState.Work.CALENDAR) {
        val n = service.exportCalendar(uri)
        onDone(n)
        BackupUiState.Idle
    }

    fun open(uri: Uri) = run(BackupUiState.Work.READING) {
        when (val result = service.read(uri)) {
            is BackupReadResult.Failure -> BackupUiState.ReadFailed(result.error)
            is BackupReadResult.Success -> BackupUiState.Preview(
                file = result.file,
                current = service.currentCounts(),
                restoreSettings = result.file.settings != null,
            )
        }
    }

    fun setRestoreSettings(value: Boolean) {
        val s = _state.value as? BackupUiState.Preview ?: return
        _state.value = s.copy(restoreSettings = value && s.file.settings != null)
    }

    fun merge() {
        val p = _state.value as? BackupUiState.Preview ?: return
        run(BackupUiState.Work.IMPORTING) {
            val plan = service.merge(p.file, p.restoreSettings)
            BackupUiState.Merged(plan.added, plan.skipped)
        }
    }

    fun replace() {
        val p = _state.value as? BackupUiState.Preview ?: return
        run(BackupUiState.Work.IMPORTING) { BackupUiState.Replaced(service.replace(p.file, p.restoreSettings)) }
    }

    fun dismiss() {
        if (_state.value !is BackupUiState.Working) _state.value = BackupUiState.Idle
    }
}

@Composable
fun BackupScreen(nav: AppNavigator) {
    val appContext = LocalContext.current.applicationContext
    val resources = LocalResources.current
    val vm = taskfernViewModel { c, _ -> BackupViewModel(c, BackupService(appContext, c)) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    var confirmReplace by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri)
    }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        if (uri != null) {
            vm.exportCalendar(uri) { n ->
                val message = if (n == 0) resources.getString(R.string.backup_calendar_empty)
                else resources.getQuantityString(R.plurals.backup_calendar_done, n, n)
                scope.showMessage(snackbar, message)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.open(uri)
    }

    val busy = state is BackupUiState.Working
    SettingsScaffold(title = stringResource(R.string.backup_title), onBack = nav::back) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(bottom = 24.dp),
        ) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                Text(
                    stringResource(R.string.backup_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            StatusCard(
                state = state,
                onDismiss = vm::dismiss,
                onToggleSettings = vm::setRestoreSettings,
                onMerge = vm::merge,
                onReplace = { confirmReplace = true },
            )

            SettingsGroup(stringResource(R.string.backup_section_export)) {
                ActionBlock(
                    icon = Icons.Outlined.FileDownload,
                    title = stringResource(R.string.backup_export),
                    text = stringResource(R.string.backup_export_summary),
                ) {
                    Button(onClick = { exportLauncher.launch(BackupService.fileName(LocalDate.now())) }, enabled = !busy) {
                        Text(stringResource(R.string.backup_export_button))
                    }
                }
                SettingsDivider()
                ActionBlock(
                    icon = Icons.Outlined.CalendarMonth,
                    title = stringResource(R.string.backup_calendar),
                    text = stringResource(R.string.backup_calendar_summary),
                ) {
                    OutlinedButton(onClick = { calendarLauncher.launch(IcsExporter.fileName(LocalDate.now())) }, enabled = !busy) {
                        Text(stringResource(R.string.backup_calendar_button))
                    }
                }
            }

            SettingsGroup(stringResource(R.string.backup_section_restore)) {
                ActionBlock(
                    icon = Icons.Outlined.FileUpload,
                    title = stringResource(R.string.backup_import),
                    text = stringResource(R.string.backup_import_summary),
                ) {
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                        enabled = !busy,
                    ) { Text(stringResource(R.string.backup_import_button)) }
                }
            }
        }
    }

    val preview = state as? BackupUiState.Preview
    if (confirmReplace && preview != null) {
        val current = preview.current
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            icon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.backup_replace_title)) },
            text = {
                Column {
                    Text(
                        if (current.isEmpty) stringResource(R.string.backup_replace_nothing)
                        else stringResource(R.string.backup_replace_message, countsSummary(resources, current, Int.MAX_VALUE)),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.backup_replace_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmReplace = false; vm.replace() }) {
                    Text(stringResource(R.string.backup_replace_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ActionBlock(icon: ImageVector, title: String, text: String, button: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(10.dp))
            button()
        }
    }
}

@Composable
private fun StatusCard(
    state: BackupUiState,
    onDismiss: () -> Unit,
    onToggleSettings: (Boolean) -> Unit,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
) {
    if (state is BackupUiState.Idle) return
    val resources = LocalResources.current
    val error = state is BackupUiState.ReadFailed || state is BackupUiState.WriteFailed || state is BackupUiState.ImportFailed
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        val onColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
        Column(Modifier.padding(20.dp)) {
            when (state) {
                is BackupUiState.Working -> {
                    Text(
                        stringResource(
                            when (state.what) {
                                BackupUiState.Work.EXPORTING -> R.string.backup_working_export
                                BackupUiState.Work.CALENDAR -> R.string.backup_working_calendar
                                BackupUiState.Work.READING -> R.string.backup_working_read
                                BackupUiState.Work.IMPORTING -> R.string.backup_working_import
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = onColor,
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                is BackupUiState.Preview -> {
                    val file = state.file
                    Header(Icons.Outlined.FileUpload, stringResource(R.string.backup_preview_title), onColor)
                    Text(
                        stringResource(R.string.backup_preview_from, formatInstant(file.exportedAt), file.appVersion.ifEmpty { "?" }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onColor,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    CountsList(file.counts, onColor)
                    if (file.settings != null) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .toggleable(value = state.restoreSettings, role = Role.Switch, onValueChange = onToggleSettings),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.backup_restore_settings), color = onColor, modifier = Modifier.weight(1f))
                            Switch(checked = state.restoreSettings, onCheckedChange = null)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.backup_merge_explain), style = MaterialTheme.typography.bodySmall, color = onColor)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.backup_replace_explain), style = MaterialTheme.typography.bodySmall, color = onColor)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = onMerge) { Text(stringResource(R.string.backup_merge)) }
                        OutlinedButton(
                            onClick = onReplace,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(R.string.backup_replace)) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = onColor) }
                    }
                }

                is BackupUiState.ReadFailed -> {
                    val (title, message) = when (val e = state.error) {
                        BackupError.NotJson -> stringResource(R.string.backup_error_not_json_title) to stringResource(R.string.backup_error_not_json)
                        BackupError.WrongFormat -> stringResource(R.string.backup_error_format_title) to stringResource(R.string.backup_error_format)
                        is BackupError.NewerVersion -> stringResource(R.string.backup_error_newer_title) to
                            stringResource(R.string.backup_error_newer, e.version, BackupFile.VERSION)
                        is BackupError.Invalid -> stringResource(R.string.backup_error_invalid_title) to stringResource(R.string.backup_error_invalid)
                        is BackupError.Unreadable -> stringResource(R.string.backup_error_unreadable_title) to stringResource(R.string.backup_error_unreadable)
                    }
                    Header(Icons.Outlined.ErrorOutline, title, onColor)
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = onColor, modifier = Modifier.padding(top = 6.dp))
                    val details = when (val e = state.error) {
                        is BackupError.Invalid -> e.problems
                        is BackupError.Unreadable -> listOf(e.detail).filter { it.isNotBlank() }
                        else -> emptyList()
                    }
                    Details(details, onColor)
                    DismissRow(onDismiss, onColor)
                }

                is BackupUiState.WriteFailed -> {
                    Header(Icons.Outlined.ErrorOutline, stringResource(R.string.backup_error_write_title), onColor)
                    Text(stringResource(R.string.backup_error_write), style = MaterialTheme.typography.bodyMedium, color = onColor, modifier = Modifier.padding(top = 6.dp))
                    Details(listOf(state.detail), onColor)
                    DismissRow(onDismiss, onColor)
                }

                is BackupUiState.ImportFailed -> {
                    Header(Icons.Outlined.ErrorOutline, stringResource(R.string.backup_error_import_title), onColor)
                    Text(stringResource(R.string.backup_error_import), style = MaterialTheme.typography.bodyMedium, color = onColor, modifier = Modifier.padding(top = 6.dp))
                    Details(listOf(state.detail), onColor)
                    DismissRow(onDismiss, onColor)
                }

                is BackupUiState.Exported -> {
                    Header(Icons.Outlined.CheckCircle, stringResource(R.string.backup_exported_title), onColor)
                    Text(
                        if (state.counts.isEmpty) stringResource(R.string.backup_exported_empty)
                        else stringResource(R.string.backup_exported, countsSummary(resources, state.counts, 4)),
                        style = MaterialTheme.typography.bodyMedium, color = onColor, modifier = Modifier.padding(top = 6.dp),
                    )
                    DismissRow(onDismiss, onColor)
                }

                is BackupUiState.Merged -> {
                    Header(Icons.Outlined.CheckCircle, stringResource(R.string.backup_merged_title), onColor)
                    Text(
                        if (state.added.isEmpty) stringResource(R.string.backup_merged_nothing)
                        else stringResource(R.string.backup_merged_added, countsSummary(resources, state.added, Int.MAX_VALUE)),
                        style = MaterialTheme.typography.bodyMedium, color = onColor, modifier = Modifier.padding(top = 6.dp),
                    )
                    if (!state.skipped.isEmpty) {
                        Text(
                            stringResource(R.string.backup_merged_skipped, countsSummary(resources, state.skipped, Int.MAX_VALUE)),
                            style = MaterialTheme.typography.bodyMedium, color = onColor, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    DismissRow(onDismiss, onColor)
                }

                is BackupUiState.Replaced -> {
                    Header(Icons.Outlined.CheckCircle, stringResource(R.string.backup_replaced_title), onColor)
                    Text(
                        if (state.counts.isEmpty) stringResource(R.string.backup_replaced_empty)
                        else stringResource(R.string.backup_replaced, countsSummary(resources, state.counts, Int.MAX_VALUE)),
                        style = MaterialTheme.typography.bodyMedium, color = onColor, modifier = Modifier.padding(top = 6.dp),
                    )
                    DismissRow(onDismiss, onColor)
                }

                BackupUiState.Idle -> {}
            }
        }
    }
}

@Composable
private fun Header(icon: ImageVector, title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun Details(lines: List<String>, color: Color) {
    if (lines.isEmpty()) return
    Column(Modifier.padding(top = 10.dp)) {
        lines.forEach {
            Text("• $it", style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun DismissRow(onDismiss: () -> Unit, color: Color) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = color) }
    }
}

@Composable
private fun CountsList(counts: BackupCounts, color: Color) {
    val lines = countLines(LocalResources.current, counts)
    if (lines.isEmpty()) {
        Text(stringResource(R.string.backup_preview_empty), style = MaterialTheme.typography.bodyMedium, color = color)
        return
    }
    Column {
        lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = color) }
    }
}

/** One localized line per non-empty kind of data, most important first. */
private fun countLines(r: Resources, c: BackupCounts): List<String> {
    fun line(id: Int, n: Int) = if (n > 0) r.getQuantityString(id, n, n) else null
    return listOfNotNull(
        line(R.plurals.backup_count_tasks, c.tasks),
        line(R.plurals.backup_count_lists, c.taskLists),
        line(R.plurals.backup_count_subtasks, c.subtasks),
        line(R.plurals.backup_count_reminders, c.reminders),
        line(R.plurals.backup_count_shopping_lists, c.shoppingLists),
        line(R.plurals.backup_count_shopping_items, c.shoppingItems),
        line(R.plurals.backup_count_routines, c.routines),
        line(R.plurals.backup_count_routine_steps, c.routineSteps),
        line(R.plurals.backup_count_templates, c.templates),
        line(R.plurals.backup_count_focus, c.focusSessions),
        line(R.plurals.backup_count_history, c.occurrenceLog + c.routineChecks),
        line(R.plurals.backup_count_suggestions, c.itemMemory),
    )
}

private fun countsSummary(resources: Resources, counts: BackupCounts, max: Int): String =
    countLines(resources, counts).take(max).joinToString(", ")

private fun formatInstant(millis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
