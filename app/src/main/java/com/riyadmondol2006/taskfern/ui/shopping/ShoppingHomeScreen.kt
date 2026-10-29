package com.riyadmondol2006.taskfern.ui.shopping

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.ui.common.fabLabel
import com.riyadmondol2006.taskfern.data.db.ShoppingListWithCounts
import com.riyadmondol2006.taskfern.data.db.TemplateEntity
import com.riyadmondol2006.taskfern.data.db.TemplateKind
import com.riyadmondol2006.taskfern.data.repo.ShoppingRepository
import com.riyadmondol2006.taskfern.data.repo.TemplateRepository
import com.riyadmondol2006.taskfern.ui.common.ConfirmDialog
import com.riyadmondol2006.taskfern.ui.common.EmptyState
import com.riyadmondol2006.taskfern.ui.common.LocalSnackbarHost
import com.riyadmondol2006.taskfern.ui.common.listColor
import com.riyadmondol2006.taskfern.ui.common.taskfernViewModel
import com.riyadmondol2006.taskfern.ui.navigation.AppNavigator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShoppingHomeViewModel(
    private val shopping: ShoppingRepository,
    private val templateRepo: TemplateRepository,
) : ViewModel() {
    /** Null while the first query is running, so the empty state never flashes. */
    val lists: StateFlow<List<ShoppingListWithCounts>?> =
        shopping.observeLists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val templates: StateFlow<List<TemplateEntity>> = templateRepo.observeAll()
        .map { all -> all.filter { it.kind == TemplateKind.SHOPPING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String, color: Int, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(shopping.createList(name, color)) }
    }

    fun update(id: Long, name: String, color: Int) {
        viewModelScope.launch { shopping.updateList(id, name, color) }
    }

    fun duplicate(id: Long, copyName: String, onDone: (Long) -> Unit) {
        viewModelScope.launch { shopping.duplicateList(id, copyName)?.let(onDone) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { shopping.deleteList(id) }
    }

    fun fromTemplate(template: TemplateEntity, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(templateRepo.apply(template)) }
    }
}

@Composable
fun ShoppingHomeScreen(nav: AppNavigator) {
    val vm = taskfernViewModel { c, _ -> ShoppingHomeViewModel(c.shopping, c.templates) }
    val lists by vm.lists.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    var creating by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showTemplates by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val fabExpanded by remember { androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.shopping_title)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { showTemplates = true }) {
                        Icon(Icons.Outlined.LibraryAdd, stringResource(R.string.shopping_from_template))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_manage_templates)) },
                                leadingIcon = { Icon(Icons.Outlined.ViewAgenda, null) },
                                onClick = { menu = false; nav.openTemplates() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.nav_settings)) },
                                leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                onClick = { menu = false; nav.openSettings() },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!lists.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.shopping_new_list)) },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    expanded = fabExpanded,
                    onClick = { creating = true },
                    // Explicit label: TalkBack does not always see the text of an extended FAB.
                    modifier = Modifier.fabLabel(R.string.shopping_new_list),
                )
            }
        },
    ) { padding ->
        val current = lists
        when {
            current == null -> Box(Modifier.fillMaxSize().padding(padding))
            current.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = stringResource(R.string.shopping_empty_title),
                    message = stringResource(R.string.shopping_empty_message),
                    illustration = R.drawable.ic_cart,
                    modifier = Modifier.align(Alignment.Center),
                    action = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(onClick = { creating = true }) {
                                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.shopping_new_list))
                            }
                            if (templates.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = { showTemplates = true }) {
                                    Icon(Icons.Outlined.LibraryAdd, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.shopping_from_template))
                                }
                            }
                        }
                    },
                )
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(current, key = { it.list.id }) { row ->
                    ShoppingListCard(
                        row = row,
                        onOpen = { nav.openShoppingList(row.list.id) },
                        onEdit = { editingId = row.list.id },
                        onDuplicate = {
                            vm.duplicate(row.list.id, resources.getString(R.string.shopping_copy_name, row.list.name)) { newId ->
                                scope.showAction(snackbar, resources.getString(R.string.shopping_duplicated), resources.getString(R.string.action_open)) {
                                    nav.openShoppingList(newId)
                                }
                            }
                        },
                        onDelete = { deletingId = row.list.id },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (creating) {
        ListEditorDialog(
            title = stringResource(R.string.shopping_new_list),
            confirmLabel = stringResource(R.string.shopping_create),
            initialName = "",
            initialColor = (lists?.size ?: 0) % paletteSize(),
            onConfirm = { name, color -> vm.create(name, color) { nav.openShoppingList(it) } },
            onDismiss = { creating = false },
        )
    }
    editingId?.let { id ->
        val list = lists?.firstOrNull { it.list.id == id }?.list
        if (list == null) {
            LaunchedEffect(id) { editingId = null }
        } else {
            ListEditorDialog(
                title = stringResource(R.string.shopping_edit_list),
                confirmLabel = stringResource(R.string.action_save),
                initialName = list.name,
                initialColor = list.color,
                onConfirm = { name, color -> vm.update(id, name, color) },
                onDismiss = { editingId = null },
            )
        }
    }
    deletingId?.let { id ->
        val name = lists?.firstOrNull { it.list.id == id }?.list?.name.orEmpty()
        ConfirmDialog(
            title = stringResource(R.string.shopping_delete_list_title),
            message = stringResource(R.string.shopping_delete_list_message, name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { vm.delete(id) },
            onDismiss = { deletingId = null },
        )
    }
    if (showTemplates) {
        TemplatePickerSheet(
            title = stringResource(R.string.shopping_from_template),
            emptyMessage = stringResource(R.string.shopping_no_templates),
            templates = templates,
            onPick = { t -> vm.fromTemplate(t) { nav.openShoppingList(it) } },
            onManage = { nav.openTemplates() },
            onDismiss = { showTemplates = false },
        )
    }
}

@Composable
private fun ShoppingListCard(
    row: ShoppingListWithCounts,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = listColor(row.list.color)
    val inCart = row.total - row.remaining
    val progress by animateFloatAsState(if (row.total == 0) 0f else inCart.toFloat() / row.total, label = "listProgress")
    val allDone = row.total > 0 && row.remaining == 0
    var menu by remember { mutableStateOf(false) }

    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 16.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (allDone) Icons.Outlined.CheckCircle else Icons.Outlined.ShoppingCart,
                        null, tint = color, modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text(
                        row.list.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            row.total == 0 -> stringResource(R.string.shopping_card_empty)
                            allDone -> stringResource(R.string.shopping_card_all_done)
                            else -> pluralStringResource(R.plurals.shopping_card_remaining, row.remaining, row.remaining, row.total)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (allDone) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, stringResource(R.string.shopping_list_options, row.list.name))
                    }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shopping_edit_list)) },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menu = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_duplicate)) },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                            onClick = { menu = false; onDuplicate() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
            if (row.total > 0) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = color,
                    trackColor = color.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp).height(6.dp),
                )
            }
        }
    }
}
