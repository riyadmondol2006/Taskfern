package com.riyadmondol2006.taskfern.ui.tasks

import com.riyadmondol2006.taskfern.domain.LogicalDay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Upcoming
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.TaskListEntity
import com.riyadmondol2006.taskfern.data.db.TaskListWithCount
import com.riyadmondol2006.taskfern.data.prefs.SettingsRepository
import com.riyadmondol2006.taskfern.data.repo.TaskRepository
import com.riyadmondol2006.taskfern.ui.common.listColor
import com.riyadmondol2006.taskfern.ui.common.taskfernViewModel
import com.riyadmondol2006.taskfern.ui.navigation.AppNavigator
import com.riyadmondol2006.taskfern.ui.navigation.SmartViewKey
import com.riyadmondol2006.taskfern.ui.theme.LocalTaskfernColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SmartViewInfo(val key: String, val label: Int, val icon: ImageVector)

val smartViews = listOf(
    SmartViewInfo(SmartViewKey.INBOX, R.string.view_inbox, Icons.Outlined.Inbox),
    SmartViewInfo(SmartViewKey.TODAY, R.string.view_today, Icons.Outlined.Today),
    SmartViewInfo(SmartViewKey.TOMORROW, R.string.view_tomorrow, Icons.Outlined.WbTwilight),
    SmartViewInfo(SmartViewKey.THIS_WEEK, R.string.view_this_week, Icons.Outlined.DateRange),
    SmartViewInfo(SmartViewKey.UPCOMING, R.string.view_upcoming, Icons.Outlined.Upcoming),
    SmartViewInfo(SmartViewKey.PLANS, R.string.view_plans, Icons.Outlined.CalendarViewMonth),
    SmartViewInfo(SmartViewKey.OVERDUE, R.string.view_overdue, Icons.Outlined.WarningAmber),
    SmartViewInfo(SmartViewKey.IMPORTANT, R.string.view_important, Icons.Outlined.Flag),
    SmartViewInfo(SmartViewKey.NO_DATE, R.string.view_no_date, Icons.Outlined.EventBusy),
    SmartViewInfo(SmartViewKey.RECENT, R.string.view_recent, Icons.Outlined.NewReleases),
    SmartViewInfo(SmartViewKey.ALL, R.string.view_all, Icons.Outlined.ViewAgenda),
)

fun smartViewLabel(key: String): Int? = when (key) {
    SmartViewKey.COMPLETED -> R.string.view_completed
    SmartViewKey.TRASH -> R.string.view_trash
    SmartViewKey.ARCHIVED -> R.string.view_archived
    else -> smartViews.firstOrNull { it.key == key }?.label
}

data class TasksHomeState(
    val counts: Map<String, Int> = emptyMap(),
    val lists: List<TaskListWithCount> = emptyList(),
    val tags: List<String> = emptyList(),
)

class TasksHomeViewModel(private val tasks: TaskRepository, settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<TasksHomeState> = combine(
        tasks.observeOpenRows(), tasks.observeListsWithCounts(), tasks.observeTags(), settings.settings,
    ) { open, lists, tags, s ->
        val today = LogicalDay.today()
        val recentSince = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        TasksHomeState(
            counts = smartViews.associate { v ->
                v.key to TaskFilters.filter(v.key, open, today, s.weekStartsMonday, recentSinceMillis = recentSince).size
            },
            lists = lists,
            tags = tags,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksHomeState())

    fun createList(name: String, color: Int) = viewModelScope.launch { tasks.createList(name, color) }
    fun updateList(list: TaskListEntity) = viewModelScope.launch { tasks.updateList(list) }
    fun deleteList(id: Long, keepTasks: Boolean) = viewModelScope.launch { tasks.deleteList(id, keepTasks) }
}

@Composable
fun TasksHomeScreen(nav: AppNavigator) {
    val vm = taskfernViewModel { c, _ -> TasksHomeViewModel(c.tasks, c.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editing by remember { mutableStateOf<TaskListEntity?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TaskListEntity?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.nav_tasks)) },
                actions = {
                    IconButton(onClick = nav::openSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.nav_search)) }
                    IconButton(onClick = nav::openSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.nav_settings)) }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.quickAdd() }) { Icon(Icons.Filled.Add, stringResource(R.string.action_add_task)) }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(smartViews, key = { it.key }) { v ->
                SmartViewTile(
                    label = stringResource(v.label),
                    icon = v.icon,
                    count = state.counts[v.key] ?: 0,
                    tint = if (v.key == SmartViewKey.OVERDUE && (state.counts[v.key] ?: 0) > 0) LocalTaskfernColors.current.overdue
                    else MaterialTheme.colorScheme.primary,
                    onClick = { nav.openTaskList(v.key) },
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }, key = "lists-h") {
                Row(Modifier.padding(top = 14.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tasks_my_lists), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { creating = true }) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.tasks_new_list))
                    }
                }
            }
            if (state.lists.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "lists-empty") {
                    Text(
                        stringResource(R.string.tasks_lists_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            items(state.lists, key = { "l${it.list.id}" }, span = { GridItemSpan(maxLineSpan) }) { l ->
                ListRow(
                    l,
                    onClick = { nav.openTaskList(SmartViewKey.LIST, l.list.id) },
                    onEdit = { editing = l.list },
                    onToggleFavorite = { vm.updateList(l.list.copy(favorite = !l.list.favorite)) },
                    onDelete = { deleting = l.list },
                )
            }

            if (state.tags.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "tags") {
                    Column(Modifier.padding(top = 10.dp)) {
                        Text(stringResource(R.string.tasks_tags), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.tags.forEach { tag ->
                                AssistChip(onClick = { nav.openTaskList(SmartViewKey.TAG_PREFIX + tag) }, label = { Text("#$tag") })
                            }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }, key = "more") {
                Column(Modifier.padding(top = 12.dp)) {
                    MoreRow(Icons.Outlined.CheckCircle, stringResource(R.string.view_completed)) { nav.openTaskList(SmartViewKey.COMPLETED) }
                    MoreRow(Icons.Outlined.History, stringResource(R.string.view_trash)) { nav.openTaskList(SmartViewKey.TRASH) }
                    MoreRow(Icons.Outlined.Archive, stringResource(R.string.view_archived)) { nav.openTaskList(SmartViewKey.ARCHIVED) }
                    MoreRow(Icons.AutoMirrored.Outlined.FormatListBulleted, stringResource(R.string.tasks_templates)) { nav.openTemplates() }
                    MoreRow(Icons.Outlined.CalendarToday, stringResource(R.string.nav_calendar)) { nav.openCalendar() }
                }
            }
        }
    }

    if (creating) {
        ListEditDialog(initial = null, onDismiss = { creating = false }, onSave = { name, color -> vm.createList(name, color) })
    }
    editing?.let { list ->
        ListEditDialog(initial = list, onDismiss = { editing = null }, onSave = { name, color -> vm.updateList(list.copy(name = name, color = color)) })
    }
    deleting?.let { list ->
        DeleteListDialog(list, onDismiss = { deleting = null }, onDelete = { keep -> vm.deleteList(list.id, keep) })
    }
}

@Composable
private fun SmartViewTile(label: String, icon: ImageVector, count: Int, tint: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label, $count" },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (count > 0) Text(count.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ListRow(l: TaskListWithCount, onClick: () -> Unit, onEdit: () -> Unit, onToggleFavorite: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(listColor(l.list.color)))
            Spacer(Modifier.width(14.dp))
            Text(l.list.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (l.list.favorite) Icon(Icons.Filled.Star, stringResource(R.string.tasks_favorite), tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
            if (l.openCount > 0) {
                Spacer(Modifier.width(8.dp))
                Text(l.openCount.toString(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(
                        text = { Text(stringResource(if (l.list.favorite) R.string.tasks_unfavorite else R.string.tasks_favorite)) },
                        leadingIcon = { Icon(if (l.list.favorite) Icons.Outlined.StarOutline else Icons.Filled.Star, null) },
                        onClick = { menu = false; onToggleFavorite() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ListEditDialog(initial: TaskListEntity?, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var color by rememberSaveable { mutableStateOf(initial?.color ?: 0) }
    val palette = LocalTaskfernColors.current.listColors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.tasks_new_list else R.string.tasks_edit_list)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60) }, singleLine = true,
                    label = { Text(stringResource(R.string.tasks_list_name)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    palette.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(c).clickable { color = i }
                                .semantics { contentDescription = "Colour ${i + 1}" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (i == color) Box(Modifier.size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), color); onDismiss() }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun DeleteListDialog(list: TaskListEntity, onDismiss: () -> Unit, onDelete: (keepTasks: Boolean) -> Unit) {
    var keep by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tasks_delete_list_title, list.name)) },
        text = {
            Column {
                Text(stringResource(R.string.tasks_delete_list_message))
                Row(Modifier.fillMaxWidth().clickable { keep = !keep }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = keep, onCheckedChange = { keep = it })
                    Text(stringResource(R.string.tasks_delete_list_keep))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(keep); onDismiss() }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
