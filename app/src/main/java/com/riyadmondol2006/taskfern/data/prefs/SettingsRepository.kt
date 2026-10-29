package com.riyadmondol2006.taskfern.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Accent palettes used when dynamic colour is off (or unavailable before Android 12). */
enum class Accent { FERN, MOSS, OCEAN, DUSK, CLAY }

enum class TaskSort { SMART, DUE_DATE, PRIORITY, CREATED, TITLE, MANUAL }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledBlack: Boolean = false,
    val dynamicColor: Boolean = true,
    val accent: Accent = Accent.FERN,
    val userName: String = "",
    /** Time used for reminders on tasks that have a date but no time, minutes after midnight. */
    val defaultReminderMinutes: Int = 9 * 60,
    /** Whether a reminder is added automatically when a task gets a due date. */
    val autoReminder: Boolean = true,
    val dailyAgendaEnabled: Boolean = false,
    val dailyAgendaMinutes: Int = 8 * 60,
    val weekStartsMonday: Boolean = true,
    val showCompletedToday: Boolean = true,
    val taskSort: TaskSort = TaskSort.SMART,
    val hideCheckedShopping: Boolean = false,
    val showShoppingPrices: Boolean = false,
    val currencySymbol: String = "",
    val focusMinutes: Int = 25,
    val trashRetentionDays: Int = 30,
    val onboardingDone: Boolean = false,
    val templatesSeeded: Boolean = false,
    val lastQuickAddListId: Long = 0,
    /** When a new day begins, minutes after midnight (0 = midnight). See LogicalDay. */
    val dayStartMinutes: Int = 0,
) {
    val defaultReminderTime: LocalTime get() = LocalTime.of(defaultReminderMinutes / 60, defaultReminderMinutes % 60)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** User preferences in DataStore. Every write is atomic; reads are a [Flow]. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val amoled = booleanPreferencesKey("amoled_black")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val accent = stringPreferencesKey("accent")
        val userName = stringPreferencesKey("user_name")
        val defaultReminder = intPreferencesKey("default_reminder_minutes")
        val autoReminder = booleanPreferencesKey("auto_reminder")
        val agendaEnabled = booleanPreferencesKey("daily_agenda_enabled")
        val agendaMinutes = intPreferencesKey("daily_agenda_minutes")
        val weekStartsMonday = booleanPreferencesKey("week_starts_monday")
        val showCompletedToday = booleanPreferencesKey("show_completed_today")
        val taskSort = stringPreferencesKey("task_sort")
        val hideChecked = booleanPreferencesKey("hide_checked_shopping")
        val showPrices = booleanPreferencesKey("show_shopping_prices")
        val currency = stringPreferencesKey("currency_symbol")
        val focusMinutes = intPreferencesKey("focus_minutes")
        val trashDays = intPreferencesKey("trash_retention_days")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val templatesSeeded = booleanPreferencesKey("templates_seeded")
        val lastQuickAddList = longPreferencesKey("last_quick_add_list")
        val dayStart = intPreferencesKey("day_start_minutes")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            themeMode = p[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            amoledBlack = p[Keys.amoled] ?: d.amoledBlack,
            dynamicColor = p[Keys.dynamic] ?: d.dynamicColor,
            accent = p[Keys.accent]?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: d.accent,
            userName = p[Keys.userName] ?: d.userName,
            defaultReminderMinutes = p[Keys.defaultReminder] ?: d.defaultReminderMinutes,
            autoReminder = p[Keys.autoReminder] ?: d.autoReminder,
            dailyAgendaEnabled = p[Keys.agendaEnabled] ?: d.dailyAgendaEnabled,
            dailyAgendaMinutes = p[Keys.agendaMinutes] ?: d.dailyAgendaMinutes,
            weekStartsMonday = p[Keys.weekStartsMonday] ?: d.weekStartsMonday,
            showCompletedToday = p[Keys.showCompletedToday] ?: d.showCompletedToday,
            taskSort = p[Keys.taskSort]?.let { runCatching { TaskSort.valueOf(it) }.getOrNull() } ?: d.taskSort,
            hideCheckedShopping = p[Keys.hideChecked] ?: d.hideCheckedShopping,
            showShoppingPrices = p[Keys.showPrices] ?: d.showShoppingPrices,
            currencySymbol = p[Keys.currency] ?: d.currencySymbol,
            focusMinutes = p[Keys.focusMinutes] ?: d.focusMinutes,
            trashRetentionDays = p[Keys.trashDays] ?: d.trashRetentionDays,
            onboardingDone = p[Keys.onboarding] ?: d.onboardingDone,
            templatesSeeded = p[Keys.templatesSeeded] ?: d.templatesSeeded,
            lastQuickAddListId = p[Keys.lastQuickAddList] ?: d.lastQuickAddListId,
            dayStartMinutes = p[Keys.dayStart] ?: d.dayStartMinutes,
        )
    }.distinctUntilChanged()

    suspend fun current(): Settings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.themeMode] = mode.name }
    suspend fun setAmoledBlack(value: Boolean) = context.dataStore.edit { it[Keys.amoled] = value }
    suspend fun setDynamicColor(value: Boolean) = context.dataStore.edit { it[Keys.dynamic] = value }
    suspend fun setAccent(accent: Accent) = context.dataStore.edit { it[Keys.accent] = accent.name }
    suspend fun setUserName(name: String) = context.dataStore.edit { it[Keys.userName] = name.trim().take(40) }
    suspend fun setDefaultReminderMinutes(minutes: Int) =
        context.dataStore.edit { it[Keys.defaultReminder] = minutes.coerceIn(0, 24 * 60 - 1) }
    suspend fun setAutoReminder(value: Boolean) = context.dataStore.edit { it[Keys.autoReminder] = value }
    suspend fun setDailyAgendaEnabled(value: Boolean) = context.dataStore.edit { it[Keys.agendaEnabled] = value }
    suspend fun setDailyAgendaMinutes(minutes: Int) =
        context.dataStore.edit { it[Keys.agendaMinutes] = minutes.coerceIn(0, 24 * 60 - 1) }
    suspend fun setWeekStartsMonday(value: Boolean) = context.dataStore.edit { it[Keys.weekStartsMonday] = value }
    suspend fun setShowCompletedToday(value: Boolean) = context.dataStore.edit { it[Keys.showCompletedToday] = value }
    suspend fun setTaskSort(sort: TaskSort) = context.dataStore.edit { it[Keys.taskSort] = sort.name }
    suspend fun setHideCheckedShopping(value: Boolean) = context.dataStore.edit { it[Keys.hideChecked] = value }
    suspend fun setShowShoppingPrices(value: Boolean) = context.dataStore.edit { it[Keys.showPrices] = value }
    suspend fun setCurrencySymbol(value: String) = context.dataStore.edit { it[Keys.currency] = value.trim().take(4) }
    suspend fun setFocusMinutes(value: Int) = context.dataStore.edit { it[Keys.focusMinutes] = value.coerceIn(1, 180) }
    suspend fun setTrashRetentionDays(value: Int) = context.dataStore.edit { it[Keys.trashDays] = value.coerceIn(1, 365) }
    suspend fun setOnboardingDone(value: Boolean) = context.dataStore.edit { it[Keys.onboarding] = value }
    suspend fun setTemplatesSeeded(value: Boolean) = context.dataStore.edit { it[Keys.templatesSeeded] = value }
    suspend fun setLastQuickAddListId(id: Long) = context.dataStore.edit { it[Keys.lastQuickAddList] = id }
    suspend fun setDayStartMinutes(minutes: Int) =
        context.dataStore.edit { it[Keys.dayStart] = minutes.coerceIn(0, com.riyadmondol2006.taskfern.domain.LogicalDay.MAX_START_MINUTES) }
}
