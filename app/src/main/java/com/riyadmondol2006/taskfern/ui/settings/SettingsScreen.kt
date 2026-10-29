package com.riyadmondol2006.taskfern.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessAlarm
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.AlarmAdd
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.riyadmondol2006.taskfern.AppContainer
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.widget.WidgetPinning
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.AddToHomeScreen
import com.riyadmondol2006.taskfern.data.prefs.Accent
import com.riyadmondol2006.taskfern.data.prefs.SettingsRepository
import com.riyadmondol2006.taskfern.data.prefs.ThemeMode
import com.riyadmondol2006.taskfern.reminders.Notifications
import com.riyadmondol2006.taskfern.ui.common.Formatters
import com.riyadmondol2006.taskfern.ui.common.LocalSnackbarHost
import com.riyadmondol2006.taskfern.ui.common.TimePickerDialog
import com.riyadmondol2006.taskfern.ui.common.showMessage
import com.riyadmondol2006.taskfern.ui.common.taskfernViewModel
import com.riyadmondol2006.taskfern.ui.navigation.AppNavigator
import com.riyadmondol2006.taskfern.ui.navigation.SmartViewKey
import com.riyadmondol2006.taskfern.ui.theme.seed
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Writes run in the app scope so leaving the screen never cancels a half-applied change. */
    private fun write(block: suspend SettingsRepository.() -> Unit) {
        c.appScope.launch { c.settings.block() }
    }

    fun setThemeMode(mode: ThemeMode) = write { setThemeMode(mode) }
    fun setAmoledBlack(value: Boolean) = write { setAmoledBlack(value) }
    fun setDynamicColor(value: Boolean) = write { setDynamicColor(value) }
    fun setAccent(accent: Accent) = write { setAccent(accent) }
    fun setUserName(name: String) = write { setUserName(name) }
    fun setAutoReminder(value: Boolean) = write { setAutoReminder(value) }
    fun setWeekStartsMonday(value: Boolean) = write { setWeekStartsMonday(value) }
    fun setShowCompletedToday(value: Boolean) = write { setShowCompletedToday(value) }
    fun setTrashRetentionDays(days: Int) = write { setTrashRetentionDays(days) }
    fun setShowShoppingPrices(value: Boolean) = write { setShowShoppingPrices(value) }
    fun setCurrencySymbol(value: String) = write { setCurrencySymbol(value) }
    fun setFocusMinutes(value: Int) = write { setFocusMinutes(value) }

    /** Tasks without a time remind at this time, so every alarm is rebuilt. */
    fun setDefaultReminderMinutes(minutes: Int) = write {
        setDefaultReminderMinutes(minutes)
        c.reminders.rescheduleAll(catchUp = false)
    }

    fun setDailyAgendaEnabled(value: Boolean) = write {
        setDailyAgendaEnabled(value)
        c.scheduleDailyWork()
    }

    fun setDailyAgendaMinutes(minutes: Int) = write {
        setDailyAgendaMinutes(minutes)
        c.scheduleDailyWork()
    }

    fun canScheduleExact(): Boolean = c.reminders.canScheduleExact()
}

/** What Android currently allows; re-read every time the screen resumes. */
private data class SystemAccess(
    val notificationsAllowed: Boolean,
    /** Android 13+: the runtime permission was refused for good, so only system settings can turn it on. */
    val notificationsBlocked: Boolean,
    val exactAlarms: Boolean,
    val batteryUnrestricted: Boolean,
)

private const val UI_PREFS = "settings_screen"
private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"

private fun readAccess(context: Context, activity: android.app.Activity?, exact: Boolean): SystemAccess {
    val allowed = Notifications.canPost(context)
    val blocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val asked = context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED_NOTIFICATIONS, false)
        val rationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
        !granted && asked && !rationale
    } else {
        false
    }
    val power = context.getSystemService(PowerManager::class.java)
    return SystemAccess(
        notificationsAllowed = allowed,
        notificationsBlocked = blocked,
        exactAlarms = exact,
        batteryUnrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true,
    )
}

private enum class SettingsDialog { NAME, REMINDER_TIME, AGENDA_TIME, CURRENCY, FOCUS }

@Composable
fun SettingsScreen(nav: AppNavigator) {
    val vm = taskfernViewModel { c, _ -> SettingsViewModel(c) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = LocalActivity.current
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    var access by remember { mutableStateOf(readAccess(context, activity, vm.canScheduleExact())) }
    LifecycleResumeEffect(Unit) {
        access = readAccess(context, activity, vm.canScheduleExact())
        onPauseOrDispose { }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        access = readAccess(context, activity, vm.canScheduleExact())
        if (!granted) scope.showMessage(snackbar, resources.getString(R.string.settings_notifications_denied))
    }
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }

    SettingsScaffold(title = stringResource(R.string.nav_settings), onBack = nav::back) { padding ->
        val s = settings ?: return@SettingsScaffold
        val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val dynamicOn = dynamicAvailable && s.dynamicColor
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            // ---- Appearance ------------------------------------------------------------------------------------------
            item {
                SettingsGroup(stringResource(R.string.settings_section_appearance)) {
                    Text(
                        stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    )
                    val modes = listOf(
                        ThemeMode.SYSTEM to R.string.settings_theme_system,
                        ThemeMode.LIGHT to R.string.settings_theme_light,
                        ThemeMode.DARK to R.string.settings_theme_dark,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        modes.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = s.themeMode == mode,
                                onClick = { vm.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            ) { Text(stringResource(label)) }
                        }
                    }
                    SettingsSwitch(
                        title = stringResource(R.string.settings_amoled),
                        summary = stringResource(
                            if (s.themeMode == ThemeMode.LIGHT) R.string.settings_amoled_light_hint else R.string.settings_amoled_summary,
                        ),
                        icon = Icons.Outlined.DarkMode,
                        checked = s.amoledBlack,
                        enabled = s.themeMode != ThemeMode.LIGHT,
                        onCheckedChange = vm::setAmoledBlack,
                    )
                    if (dynamicAvailable) {
                        SettingsSwitch(
                            title = stringResource(R.string.settings_dynamic_color),
                            summary = stringResource(R.string.settings_dynamic_color_summary),
                            icon = Icons.Outlined.Wallpaper,
                            checked = s.dynamicColor,
                            onCheckedChange = vm::setDynamicColor,
                        )
                    } else {
                        SettingsItem(
                            title = stringResource(R.string.settings_dynamic_color),
                            summary = stringResource(R.string.settings_dynamic_color_unavailable),
                            icon = Icons.Outlined.Wallpaper,
                            enabled = false,
                        )
                    }
                    SettingsItem(
                        title = stringResource(R.string.settings_accent),
                        summary = stringResource(if (dynamicOn) R.string.settings_accent_dynamic_hint else R.string.settings_accent_summary),
                        icon = Icons.Outlined.Palette,
                        enabled = !dynamicOn,
                    )
                    AccentPicker(selected = s.accent, enabled = !dynamicOn, onSelect = vm::setAccent)
                    SettingsDivider()
                    SettingsItem(
                        title = stringResource(R.string.settings_name),
                        summary = s.userName.ifEmpty { stringResource(R.string.settings_name_empty) },
                        icon = Icons.Outlined.Badge,
                        onClick = { dialog = SettingsDialog.NAME },
                    )
                }
            }

            // ---- Reminders & notifications ---------------------------------------------------------------------------
            item {
                SettingsGroup(stringResource(R.string.settings_section_reminders)) {
                    PermissionRow(
                        icon = Icons.Outlined.NotificationsActive,
                        title = stringResource(R.string.settings_notifications),
                        ok = access.notificationsAllowed,
                        okText = stringResource(R.string.settings_status_allowed),
                        notOkText = stringResource(R.string.settings_status_off),
                        explanation = stringResource(
                            if (access.notificationsAllowed) R.string.settings_notifications_on_summary
                            else R.string.settings_notifications_off_summary,
                        ),
                        action = if (access.notificationsAllowed) null else {
                            val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !access.notificationsBlocked &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            if (canAsk) {
                                stringResource(R.string.settings_allow) to {
                                    context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ASKED_NOTIFICATIONS, true) }
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                stringResource(R.string.settings_open_settings) to { context.openNotificationSettings() }
                            }
                        },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingsDivider()
                        PermissionRow(
                            icon = Icons.Outlined.AccessAlarm,
                            title = stringResource(R.string.settings_exact_alarms),
                            ok = access.exactAlarms,
                            okText = stringResource(R.string.settings_status_allowed),
                            notOkText = stringResource(R.string.settings_status_not_allowed),
                            explanation = stringResource(
                                if (access.exactAlarms) R.string.settings_exact_alarms_on_summary
                                else R.string.settings_exact_alarms_off_summary,
                            ),
                            action = if (access.exactAlarms) null else stringResource(R.string.settings_allow) to {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
                                if (!context.startSafely(intent)) context.openAppDetails()
                            },
                        )
                    }
                    SettingsDivider()
                    PermissionRow(
                        icon = Icons.Outlined.BatterySaver,
                        title = stringResource(R.string.settings_battery),
                        ok = access.batteryUnrestricted,
                        okText = stringResource(R.string.settings_status_unrestricted),
                        notOkText = stringResource(R.string.settings_status_optimised),
                        explanation = stringResource(R.string.settings_battery_summary),
                        neutralWhenNotOk = true,
                        action = stringResource(R.string.settings_battery_open) to {
                            if (!context.startSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) context.openAppDetails()
                        },
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = stringResource(R.string.settings_default_reminder),
                        summary = stringResource(
                            R.string.settings_default_reminder_summary,
                            Formatters.minutesOfDay(context, s.defaultReminderMinutes),
                        ),
                        icon = Icons.Outlined.Schedule,
                        onClick = { dialog = SettingsDialog.REMINDER_TIME },
                    )
                    SettingsSwitch(
                        title = stringResource(R.string.settings_auto_reminder),
                        summary = stringResource(R.string.settings_auto_reminder_summary),
                        icon = Icons.Outlined.AlarmAdd,
                        checked = s.autoReminder,
                        onCheckedChange = vm::setAutoReminder,
                    )
                    SettingsDivider()
                    SettingsSwitch(
                        title = stringResource(R.string.settings_agenda),
                        summary = stringResource(R.string.settings_agenda_summary),
                        icon = Icons.Outlined.ViewAgenda,
                        checked = s.dailyAgendaEnabled,
                        onCheckedChange = vm::setDailyAgendaEnabled,
                    )
                    SettingsItem(
                        title = stringResource(R.string.settings_agenda_time),
                        summary = Formatters.minutesOfDay(context, s.dailyAgendaMinutes),
                        icon = Icons.Outlined.Today,
                        enabled = s.dailyAgendaEnabled,
                        onClick = { dialog = SettingsDialog.AGENDA_TIME },
                    )
                }
            }

            // ---- Tasks -----------------------------------------------------------------------------------------------
            item {
                SettingsGroup(stringResource(R.string.settings_section_tasks)) {
                    LabeledSegments(
                        label = stringResource(R.string.settings_week_start),
                        options = listOf(true to stringResource(R.string.settings_monday), false to stringResource(R.string.settings_sunday)),
                        selected = s.weekStartsMonday,
                        onSelect = vm::setWeekStartsMonday,
                    )
                    SettingsSwitch(
                        title = stringResource(R.string.settings_show_completed_today),
                        summary = stringResource(R.string.settings_show_completed_today_summary),
                        icon = Icons.Outlined.CheckCircle,
                        checked = s.showCompletedToday,
                        onCheckedChange = vm::setShowCompletedToday,
                    )
                    SettingsDivider()
                    LabeledSegments(
                        label = stringResource(R.string.settings_trash_retention),
                        supporting = stringResource(R.string.settings_trash_retention_summary),
                        options = listOf(7, 30, 90).map { it to resources.getQuantityString(R.plurals.settings_days, it, it) },
                        selected = s.trashRetentionDays,
                        onSelect = vm::setTrashRetentionDays,
                    )
                }
            }

            // ---- Shopping --------------------------------------------------------------------------------------------
            item {
                SettingsGroup(stringResource(R.string.settings_section_shopping)) {
                    SettingsSwitch(
                        title = stringResource(R.string.settings_prices),
                        summary = stringResource(R.string.settings_prices_summary),
                        icon = Icons.Outlined.Payments,
                        checked = s.showShoppingPrices,
                        onCheckedChange = vm::setShowShoppingPrices,
                    )
                    SettingsItem(
                        title = stringResource(R.string.settings_currency),
                        summary = s.currencySymbol.ifEmpty { stringResource(R.string.settings_currency_none) },
                        icon = Icons.Outlined.CurrencyExchange,
                        enabled = s.showShoppingPrices,
                        onClick = { dialog = SettingsDialog.CURRENCY },
                    )
                }
            }

            // ---- Focus -----------------------------------------------------------------------------------------------
            item {
                SettingsGroup(stringResource(R.string.settings_section_focus)) {
                    SettingsItem(
                        title = stringResource(R.string.settings_focus_length),
                        summary = resources.getQuantityString(R.plurals.settings_minutes, s.focusMinutes, s.focusMinutes),
                        icon = Icons.Outlined.Timer,
                        onClick = { dialog = SettingsDialog.FOCUS },
                    )
                }
            }

            // ---- Home-screen widgets ---------------------------------------------------------------------------------
            item {
                SettingsGroup(stringResource(R.string.settings_section_widgets)) {
                    val pinSupported = remember { WidgetPinning.isSupported(context) }
                    Text(
                        stringResource(if (pinSupported) R.string.settings_widgets_explain else R.string.settings_widgets_unsupported),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    WidgetPinning.entries.forEach { w ->
                        SettingsItem(
                            title = stringResource(w.label),
                            summary = stringResource(w.description),
                            icon = Icons.Outlined.Widgets,
                            enabled = pinSupported,
                            onClick = { WidgetPinning.requestPin(context, w.receiver) },
                            trailing = if (pinSupported) ({ Icon(Icons.Outlined.AddToHomeScreen, stringResource(R.string.settings_widgets_add)) }) else null,
                        )
                    }
                }
            }

            // ---- Data ------------------------------------------------------------------------------------------------
            item {
                SettingsGroup(stringResource(R.string.settings_section_data)) {
                    NavRow(Icons.Outlined.SettingsBackupRestore, stringResource(R.string.backup_title), stringResource(R.string.settings_backup_summary)) { nav.openBackup() }
                    NavRow(Icons.Outlined.Dashboard, stringResource(R.string.templates_title), stringResource(R.string.settings_templates_summary)) { nav.openTemplates() }
                    NavRow(Icons.Outlined.DeleteOutline, stringResource(R.string.view_trash), pluralStringResource(R.plurals.settings_trash_summary, s.trashRetentionDays, s.trashRetentionDays)) {
                        nav.openTaskList(SmartViewKey.TRASH)
                    }
                    NavRow(Icons.Outlined.Archive, stringResource(R.string.view_archived), stringResource(R.string.settings_archived_summary)) {
                        nav.openTaskList(SmartViewKey.ARCHIVED)
                    }
                }
            }

            // ---- More ------------------------------------------------------------------------------------------------
            item {
                SettingsGroup(stringResource(R.string.settings_section_more)) {
                    NavRow(Icons.Outlined.Insights, stringResource(R.string.insights_title), stringResource(R.string.settings_insights_summary)) { nav.openInsights() }
                    NavRow(Icons.Outlined.Timer, stringResource(R.string.focus_title), stringResource(R.string.settings_focus_summary)) { nav.openFocus(0) }
                    NavRow(Icons.Outlined.Info, stringResource(R.string.about_title), stringResource(R.string.settings_about_summary)) { nav.openAbout() }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        when (dialog) {
            SettingsDialog.NAME -> TextFieldDialog(
                title = stringResource(R.string.settings_name),
                initial = s.userName,
                label = stringResource(R.string.settings_name_label),
                supporting = stringResource(R.string.settings_name_hint),
                onConfirm = vm::setUserName,
                onDismiss = { dialog = null },
            )
            SettingsDialog.CURRENCY -> TextFieldDialog(
                title = stringResource(R.string.settings_currency),
                initial = s.currencySymbol,
                label = stringResource(R.string.settings_currency_label),
                supporting = stringResource(R.string.settings_currency_hint),
                maxLength = 4,
                onConfirm = vm::setCurrencySymbol,
                onDismiss = { dialog = null },
            )
            SettingsDialog.REMINDER_TIME -> TimePickerDialog(
                initial = LocalTime.of(s.defaultReminderMinutes / 60, s.defaultReminderMinutes % 60),
                onPick = { vm.setDefaultReminderMinutes(it.hour * 60 + it.minute) },
                onDismiss = { dialog = null },
            )
            SettingsDialog.AGENDA_TIME -> TimePickerDialog(
                initial = LocalTime.of(s.dailyAgendaMinutes / 60, s.dailyAgendaMinutes % 60),
                onPick = { vm.setDailyAgendaMinutes(it.hour * 60 + it.minute) },
                onDismiss = { dialog = null },
            )
            SettingsDialog.FOCUS -> {
                val choices = (FOCUS_CHOICES + s.focusMinutes).distinct().sorted()
                ChoiceDialog(
                    title = stringResource(R.string.settings_focus_length),
                    options = choices.map { it to resources.getQuantityString(R.plurals.settings_minutes, it, it) },
                    selected = s.focusMinutes,
                    onSelect = vm::setFocusMinutes,
                    onDismiss = { dialog = null },
                )
            }
            null -> {}
        }
    }
}

private val FOCUS_CHOICES = listOf(15, 20, 25, 30, 45, 50, 60, 90)

@Composable
private fun NavRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    SettingsItem(
        title = title,
        summary = summary,
        icon = icon,
        onClick = onClick,
        trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

@Composable
private fun <T> LabeledSegments(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    supporting: String? = null,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        if (supporting != null) {
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (value, text) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(text) }
            }
        }
    }
}

/** A permission or system state: status line, honest explanation and an optional button that fixes it. */
@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    ok: Boolean,
    okText: String,
    notOkText: String,
    explanation: String,
    action: Pair<String, () -> Unit>?,
    neutralWhenNotOk: Boolean = false,
) {
    val statusColor = when {
        ok -> MaterialTheme.colorScheme.primary
        neutralWhenNotOk -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(
                    if (ok) Icons.Outlined.Check else Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    if (ok) okText else notOkText,
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(
                explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (action != null) {
                FilledTonalButton(onClick = action.second, modifier = Modifier.padding(top = 10.dp)) { Text(action.first) }
            }
        }
    }
}

@Composable
private fun AccentPicker(selected: Accent, enabled: Boolean, onSelect: (Accent) -> Unit) {
    val names = mapOf(
        Accent.FERN to R.string.settings_accent_fern,
        Accent.MOSS to R.string.settings_accent_moss,
        Accent.OCEAN to R.string.settings_accent_ocean,
        Accent.DUSK to R.string.settings_accent_dusk,
        Accent.CLAY to R.string.settings_accent_clay,
    )
    Row(
        Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, bottom = 12.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Accent.entries.forEach { accent ->
            val isSelected = accent == selected
            val label = stringResource(names.getValue(accent))
            val ring = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent
            Box(
                Modifier
                    .size(44.dp)
                    .border(2.dp, if (enabled) ring else Color.Transparent, CircleShape)
                    .padding(4.dp)
                    .background(accent.seed().copy(alpha = if (enabled) 1f else 0.35f), CircleShape)
                    .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(accent) })
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}
