package com.riyadmondol2006.taskfern.ui.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.ui.calendar.CalendarScreen
import com.riyadmondol2006.taskfern.ui.common.LocalSnackbarHost
import com.riyadmondol2006.taskfern.ui.editor.TaskEditorScreen
import com.riyadmondol2006.taskfern.ui.focus.FocusScreen
import com.riyadmondol2006.taskfern.ui.quickadd.QuickAddDefaults
import com.riyadmondol2006.taskfern.ui.quickadd.QuickAddSheet
import com.riyadmondol2006.taskfern.ui.routines.RoutineDetailScreen
import com.riyadmondol2006.taskfern.ui.routines.RoutineEditorScreen
import com.riyadmondol2006.taskfern.ui.routines.RoutinesScreen
import com.riyadmondol2006.taskfern.ui.settings.AboutScreen
import com.riyadmondol2006.taskfern.ui.settings.BackupScreen
import com.riyadmondol2006.taskfern.ui.settings.InsightsScreen
import com.riyadmondol2006.taskfern.ui.settings.SettingsScreen
import com.riyadmondol2006.taskfern.ui.settings.TemplatesScreen
import com.riyadmondol2006.taskfern.ui.shopping.ShoppingHomeScreen
import com.riyadmondol2006.taskfern.ui.shopping.ShoppingListScreen
import com.riyadmondol2006.taskfern.ui.tasks.SearchScreen
import com.riyadmondol2006.taskfern.ui.tasks.TaskListScreen
import com.riyadmondol2006.taskfern.ui.tasks.TasksHomeScreen
import com.riyadmondol2006.taskfern.ui.today.TodayScreen
import kotlin.reflect.KClass

private data class TopLevel(
    val route: Route,
    val routeClass: KClass<out Route>,
    val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val topLevels = listOf(
    TopLevel(Route.Today, Route.Today::class, R.string.nav_today, Icons.Outlined.WbSunny, Icons.Filled.WbSunny),
    TopLevel(Route.TasksHome, Route.TasksHome::class, R.string.nav_tasks, Icons.AutoMirrored.Outlined.FormatListBulleted, Icons.AutoMirrored.Filled.FormatListBulleted),
    TopLevel(Route.Calendar, Route.Calendar::class, R.string.nav_calendar, Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    TopLevel(Route.ShoppingHome, Route.ShoppingHome::class, R.string.nav_shopping, Icons.Outlined.ShoppingCart, Icons.Filled.ShoppingCart),
    TopLevel(Route.Routines, Route.Routines::class, R.string.nav_routines, Icons.Outlined.Spa, Icons.Filled.Spa),
)

private class NavControllerNavigator(
    private val nav: NavHostController,
    private val onQuickAdd: (QuickAddDefaults) -> Unit,
) : AppNavigator {
    override fun back() { if (!nav.popBackStack()) Unit }
    override fun openTask(taskId: Long) = nav.navigate(Route.TaskEditor(taskId)) { launchSingleTop = true }
    override fun openTaskList(view: String, listId: Long) = nav.navigate(Route.TaskList(view, listId)) { launchSingleTop = true }
    override fun openSearch() = nav.navigate(Route.Search) { launchSingleTop = true }
    override fun openCalendar() = switchTab(Route.Calendar)
    override fun openShoppingList(listId: Long) = nav.navigate(Route.ShoppingList(listId)) { launchSingleTop = true }
    override fun openRoutine(routineId: Long) = nav.navigate(Route.RoutineDetail(routineId)) { launchSingleTop = true }
    override fun editRoutine(routineId: Long) = nav.navigate(Route.RoutineEditor(routineId)) { launchSingleTop = true }
    override fun openSettings() = nav.navigate(Route.Settings) { launchSingleTop = true }
    override fun openBackup() = nav.navigate(Route.Backup) { launchSingleTop = true }
    override fun openTemplates() = nav.navigate(Route.Templates) { launchSingleTop = true }
    override fun openInsights() = nav.navigate(Route.Insights) { launchSingleTop = true }
    override fun openFocus(taskId: Long) = nav.navigate(Route.Focus(taskId)) { launchSingleTop = true }
    override fun openAbout() = nav.navigate(Route.About) { launchSingleTop = true }
    override fun quickAdd(listId: Long?, dueToday: Boolean, dueDate: java.time.LocalDate?) =
        onQuickAdd(QuickAddDefaults(listId = listId, dueToday = dueToday, dueDate = dueDate))

    fun switchTab(route: Route) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Handles taskfern:// deep links from notifications, widgets and shortcuts. */
fun handleDeepLink(uri: Uri, navigator: AppNavigator, switchTab: (Route) -> Unit) {
    if (uri.scheme != "taskfern") return
    val segments = uri.pathSegments
    val id = segments.firstOrNull()?.toLongOrNull()
    when (uri.host) {
        "today" -> switchTab(Route.Today)
        "tasks" -> switchTab(Route.TasksHome)
        "calendar" -> switchTab(Route.Calendar)
        "task" -> if (id != null) navigator.openTask(id)
        "shopping" -> { switchTab(Route.ShoppingHome); if (id != null) navigator.openShoppingList(id) }
        "routines" -> switchTab(Route.Routines)
        "routine" -> { switchTab(Route.Routines); if (id != null) navigator.openRoutine(id) }
        "focus" -> navigator.openFocus(0)
        "quickadd" -> navigator.quickAdd()
    }
}

@Composable
fun TaskfernAppRoot(pendingDeepLink: Uri?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    var quickAdd by rememberSaveable(stateSaver = QuickAddDefaults.Saver) { mutableStateOf<QuickAddDefaults?>(null) }
    val navigator = remember(nav) { NavControllerNavigator(nav) { quickAdd = it } }

    LaunchedEffect(pendingDeepLink) {
        if (pendingDeepLink != null) {
            handleDeepLink(pendingDeepLink, navigator, navigator::switchTab)
            onDeepLinkHandled()
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val isTopLevel = topLevels.any { t -> destination?.hierarchy?.any { it.hasRoute(t.routeClass) } == true }

    CompositionLocalProvider(LocalSnackbarHost provides snackbar) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                topLevels.forEach { item ->
                    val selected = destination?.hierarchy?.any { it.hasRoute(item.routeClass) } == true
                    item(
                        selected = selected,
                        onClick = { navigator.switchTab(item.route) },
                        icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            },
            layoutType = if (isTopLevel || destination == null) {
                androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
                    androidx.compose.material3.adaptive.currentWindowAdaptiveInfo(),
                )
            } else {
                androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType.None
            },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                contentWindowInsets = WindowInsets(0),
                containerColor = MaterialTheme.colorScheme.surface,
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    TaskfernNavHost(nav, navigator)
                }
            }
        }
        quickAdd?.let { defaults ->
            QuickAddSheet(defaults = defaults, onDismiss = { quickAdd = null })
        }
    }
}

@Composable
private fun TaskfernNavHost(nav: NavHostController, navigator: AppNavigator) {
    NavHost(
        navController = nav,
        startDestination = Route.Today,
        enterTransition = { fadeIn(tween(180)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = { fadeOut(tween(120)) },
    ) {
        composable<Route.Today> { TodayScreen(navigator) }
        composable<Route.TasksHome> { TasksHomeScreen(navigator) }
        composable<Route.TaskList> { val r = it.toRoute<Route.TaskList>(); TaskListScreen(navigator, r.view, r.listId) }
        composable<Route.TaskEditor> { TaskEditorScreen(navigator, it.toRoute<Route.TaskEditor>().taskId) }
        composable<Route.Search> { SearchScreen(navigator) }
        composable<Route.Calendar> { CalendarScreen(navigator) }
        composable<Route.ShoppingHome> { ShoppingHomeScreen(navigator) }
        composable<Route.ShoppingList> { ShoppingListScreen(navigator, it.toRoute<Route.ShoppingList>().listId) }
        composable<Route.Routines> { RoutinesScreen(navigator) }
        composable<Route.RoutineDetail> { RoutineDetailScreen(navigator, it.toRoute<Route.RoutineDetail>().routineId) }
        composable<Route.RoutineEditor> { RoutineEditorScreen(navigator, it.toRoute<Route.RoutineEditor>().routineId) }
        composable<Route.Settings> { SettingsScreen(navigator) }
        composable<Route.Backup> { BackupScreen(navigator) }
        composable<Route.Templates> { TemplatesScreen(navigator) }
        composable<Route.Insights> { InsightsScreen(navigator) }
        composable<Route.Focus> { FocusScreen(navigator, it.toRoute<Route.Focus>().taskId) }
        composable<Route.About> { AboutScreen(navigator) }
    }
}
