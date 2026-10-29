package com.riyadmondol2006.taskfern.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.domain.recurrence.Frequency
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceEngine
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceRule
import com.riyadmondol2006.taskfern.ui.common.DatePickerSheet
import com.riyadmondol2006.taskfern.ui.common.Formatters
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private enum class Preset { DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY, CUSTOM }
private enum class EndMode { NEVER, DATE, COUNT }

/**
 * Edits a repeat rule. Presets cover the common cases in one tap; "Custom" exposes interval, unit and weekdays.
 * A preview lists the next dates so the user can see exactly what the rule does.
 */
@Composable
fun RepeatSheet(
    initial: RecurrenceRule?,
    anchor: LocalDate,
    onSave: (RecurrenceRule?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initialPreset = when {
        initial == null -> Preset.DAILY
        initial.frequency == Frequency.DAILY && initial.interval == 1 -> Preset.DAILY
        initial.frequency == Frequency.WEEKLY && initial.interval == 1 && initial.weekdays == RecurrenceRule.WEEKDAYS_MASK -> Preset.WEEKDAYS
        initial.frequency == Frequency.WEEKLY && initial.interval == 1 && initial.weekdaySet.size <= 1 -> Preset.WEEKLY
        initial.frequency == Frequency.MONTHLY && initial.interval == 1 -> Preset.MONTHLY
        initial.frequency == Frequency.YEARLY && initial.interval == 1 -> Preset.YEARLY
        else -> Preset.CUSTOM
    }
    var preset by rememberSaveable { mutableStateOf(initialPreset) }
    var unit by rememberSaveable { mutableStateOf(initial?.frequency ?: Frequency.WEEKLY) }
    var intervalText by rememberSaveable { mutableStateOf((initial?.interval ?: 1).toString()) }
    var weekdays by rememberSaveable {
        mutableIntStateOf(initial?.weekdays?.takeIf { it != 0 } ?: RecurrenceRule.weekdayMask(anchor.dayOfWeek))
    }
    var fromCompletion by rememberSaveable { mutableStateOf(initial?.fromCompletion ?: false) }
    var endMode by rememberSaveable {
        mutableStateOf(
            when {
                initial?.endDate != null -> EndMode.DATE
                initial?.maxOccurrences != null -> EndMode.COUNT
                else -> EndMode.NEVER
            },
        )
    }
    var endDate by rememberSaveable { mutableStateOf(initial?.endDate ?: anchor.plusMonths(3)) }
    var countText by rememberSaveable { mutableStateOf((initial?.maxOccurrences ?: 10).toString()) }
    var pickEnd by remember { mutableStateOf(false) }

    val interval = intervalText.toIntOrNull()?.coerceIn(1, 999)
    val count = countText.toIntOrNull()?.coerceIn(1, 9999)
    val rule: RecurrenceRule? = runCatching {
        val base = when (preset) {
            Preset.DAILY -> RecurrenceRule.daily()
            Preset.WEEKDAYS -> RecurrenceRule.weekdays()
            Preset.WEEKLY -> RecurrenceRule(Frequency.WEEKLY, 1, RecurrenceRule.weekdayMask(anchor.dayOfWeek))
            Preset.MONTHLY -> RecurrenceRule.monthly()
            Preset.YEARLY -> RecurrenceRule.yearly()
            Preset.CUSTOM -> RecurrenceRule(
                unit, interval ?: return@runCatching null,
                if (unit == Frequency.WEEKLY && !fromCompletion) weekdays else 0,
            )
        }
        base.copy(
            // Weekday sets only make sense counted from the due date.
            fromCompletion = fromCompletion && preset != Preset.WEEKDAYS,
            endDate = if (endMode == EndMode.DATE) endDate else null,
            maxOccurrences = if (endMode == EndMode.COUNT) count ?: return@runCatching null else null,
            paused = initial?.paused ?: false,
        )
    }.getOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
        ) {
            Text(stringResource(R.string.repeat_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetChip(stringResource(R.string.repeat_every_day), preset == Preset.DAILY) { preset = Preset.DAILY }
                PresetChip(stringResource(R.string.repeat_weekdays), preset == Preset.WEEKDAYS) { preset = Preset.WEEKDAYS; fromCompletion = false }
                PresetChip(
                    stringResource(R.string.repeat_weekly_on, anchor.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())),
                    preset == Preset.WEEKLY,
                ) { preset = Preset.WEEKLY }
                PresetChip(stringResource(R.string.repeat_monthly_on, anchor.dayOfMonth), preset == Preset.MONTHLY) { preset = Preset.MONTHLY }
                PresetChip(stringResource(R.string.repeat_every_year), preset == Preset.YEARLY) { preset = Preset.YEARLY }
                PresetChip(stringResource(R.string.repeat_custom), preset == Preset.CUSTOM) { preset = Preset.CUSTOM }
            }

            if (preset == Preset.CUSTOM) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.repeat_every))
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { intervalText = it.filter(Char::isDigit).take(3) },
                        singleLine = true,
                        isError = interval == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    val n = interval ?: 2
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Frequency.entries.forEach { f ->
                            FilterChip(
                                selected = unit == f,
                                onClick = { unit = f },
                                label = {
                                    Text(
                                        context.resources.getQuantityString(
                                            when (f) {
                                                Frequency.DAILY -> R.plurals.unit_days
                                                Frequency.WEEKLY -> R.plurals.unit_weeks
                                                Frequency.MONTHLY -> R.plurals.unit_months
                                                Frequency.YEARLY -> R.plurals.unit_years
                                            },
                                            n,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
                if (unit == Frequency.WEEKLY && !fromCompletion) {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.repeat_on), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { d ->
                            val bit = 1 shl (d.value - 1)
                            FilterChip(
                                selected = weekdays and bit != 0,
                                onClick = {
                                    val next = weekdays xor bit
                                    if (next != 0) weekdays = next // at least one day
                                },
                                label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                            )
                        }
                    }
                }
            }

            if (preset != Preset.WEEKDAYS) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.repeat_from), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !fromCompletion, onClick = { fromCompletion = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text(stringResource(R.string.repeat_from_due))
                    }
                    SegmentedButton(selected = fromCompletion, onClick = { fromCompletion = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text(stringResource(R.string.repeat_from_completion))
                    }
                }
                Text(
                    stringResource(if (fromCompletion) R.string.repeat_from_completion_explain else R.string.repeat_from_due_explain),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.repeat_ends), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                FilterChip(selected = endMode == EndMode.NEVER, onClick = { endMode = EndMode.NEVER }, label = { Text(stringResource(R.string.repeat_end_never)) })
                FilterChip(
                    selected = endMode == EndMode.DATE,
                    onClick = { endMode = EndMode.DATE; pickEnd = true },
                    label = { Text(if (endMode == EndMode.DATE) stringResource(R.string.repeat_until, Formatters.shortDate(endDate)) else stringResource(R.string.repeat_end_on_date)) },
                )
                FilterChip(selected = endMode == EndMode.COUNT, onClick = { endMode = EndMode.COUNT }, label = { Text(stringResource(R.string.repeat_end_after)) })
            }
            if (endMode == EndMode.COUNT) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { countText = it.filter(Char::isDigit).take(4) },
                        singleLine = true,
                        isError = count == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(context.resources.getQuantityString(R.plurals.repeat_occurrences, count ?: 2))
                }
            }

            // Preview of what the rule produces.
            if (rule != null) {
                Spacer(Modifier.height(16.dp))
                val next = remember(rule, anchor) {
                    val first = if (RecurrenceEngine.matches(rule, anchor, anchor)) anchor else RecurrenceEngine.occurrenceAfter(rule, anchor, anchor)
                    if (first == null) emptyList() else RecurrenceEngine.occurrencesBetween(rule, anchor, first, anchor, anchor.plusYears(5), limit = 4)
                }
                Text(stringResource(R.string.repeat_preview), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (next.isEmpty()) stringResource(R.string.repeat_preview_none)
                    else next.joinToString("  ·  ") { Formatters.shortDate(it) } +
                        if (rule.fromCompletion) "  " + stringResource(R.string.repeat_preview_if_on_time) else "",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (initial != null) {
                    TextButton(onClick = { onSave(null); onDismiss() }) { Text(stringResource(R.string.repeat_remove)) }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(enabled = rule != null, onClick = { onSave(rule); onDismiss() }) { Text(stringResource(R.string.action_save)) }
            }
        }
    }

    if (pickEnd) {
        DatePickerSheet(initial = endDate, onPick = { endDate = if (it.isBefore(anchor)) anchor else it }, onDismiss = { pickEnd = false }, allowPast = false)
    }
}

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) =
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
