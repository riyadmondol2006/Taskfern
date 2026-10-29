package com.riyadmondol2006.taskfern.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. */
sealed interface Route {
    @Serializable data object Today : Route
    @Serializable data object TasksHome : Route
    @Serializable data class TaskList(val view: String, val listId: Long = 0) : Route
    @Serializable data class TaskEditor(val taskId: Long) : Route
    @Serializable data object Search : Route
    @Serializable data object Calendar : Route
    @Serializable data object ShoppingHome : Route
    @Serializable data class ShoppingList(val listId: Long) : Route
    @Serializable data object Routines : Route
    @Serializable data class RoutineDetail(val routineId: Long) : Route
    @Serializable data class RoutineEditor(val routineId: Long = 0) : Route
    @Serializable data object Settings : Route
    @Serializable data object Backup : Route
    @Serializable data object Templates : Route
    @Serializable data object Insights : Route
    @Serializable data class Focus(val taskId: Long = 0) : Route
    @Serializable data object About : Route
}

/** Names of the smart views a [Route.TaskList] can show. */
object SmartViewKey {
    const val LIST = "list"
    const val INBOX = "inbox"
    const val TODAY = "today"
    const val TOMORROW = "tomorrow"
    const val THIS_WEEK = "this_week"
    const val UPCOMING = "upcoming"
    const val OVERDUE = "overdue"
    const val IMPORTANT = "important"
    const val NO_DATE = "no_date"
    const val COMPLETED = "completed"
    const val RECENT = "recent"
    const val PLANS = "plans"
    const val ALL = "all"
    const val TRASH = "trash"
    const val ARCHIVED = "archived"
    const val TAG_PREFIX = "tag:"
}

/** Navigation actions available to every screen, so screens never touch the NavController directly. */
interface AppNavigator {
    fun back()
    fun openTask(taskId: Long)
    fun openTaskList(view: String, listId: Long = 0)
    fun openSearch()
    fun openCalendar()
    fun openShoppingList(listId: Long)
    fun openRoutine(routineId: Long)
    fun editRoutine(routineId: Long = 0)
    fun openSettings()
    fun openBackup()
    fun openTemplates()
    fun openInsights()
    fun openFocus(taskId: Long = 0)
    fun openAbout()
    /** Opens the Quick Add sheet with optional defaults. */
    fun quickAdd(listId: Long? = null, dueToday: Boolean = false, dueDate: java.time.LocalDate? = null)
}
