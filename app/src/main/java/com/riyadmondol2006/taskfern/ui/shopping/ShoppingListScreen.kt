package com.riyadmondol2006.taskfern.ui.shopping

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.ShoppingItemEntity
import com.riyadmondol2006.taskfern.ui.common.ConfirmDialog
import com.riyadmondol2006.taskfern.ui.common.EmptyState
import com.riyadmondol2006.taskfern.ui.common.Formatters
import com.riyadmondol2006.taskfern.ui.common.TaskCheckbox
import com.riyadmondol2006.taskfern.ui.common.listColor
import com.riyadmondol2006.taskfern.ui.common.showMessage
import com.riyadmondol2006.taskfern.ui.common.showUndo
import com.riyadmondol2006.taskfern.ui.common.taskfernViewModel
import com.riyadmondol2006.taskfern.ui.navigation.AppNavigator
import kotlinx.coroutines.launch

/** Flat rows of the list, so the screen can find and scroll to any item. */
private sealed interface ListRow {
    val key: Any

    data object Summary : ListRow { override val key: Any = "summary" }
    data class Aisle(val category: String, val count: Int) : ListRow { override val key: Any = "aisle:$category" }
    data class Item(val item: ShoppingItemEntity) : ListRow { override val key: Any = item.id }
    data class CartHeader(val count: Int, val expanded: Boolean) : ListRow { override val key: Any = "cart" }
    data object AllInCart : ListRow { override val key: Any = "all-in-cart" }
}

private fun buildRows(state: ShoppingListUiState): List<ListRow> {
    val g = state.grouped
    if (g.total == 0) return emptyList()
    return buildList {
        add(ListRow.Summary)
        if (g.remaining == 0) add(ListRow.AllInCart)
        g.sections.forEach { section ->
            add(ListRow.Aisle(section.category, section.items.size))
            section.items.forEach { add(ListRow.Item(it)) }
        }
        if (g.inCart.isNotEmpty()) {
            add(ListRow.CartHeader(g.inCart.size, expanded = !state.hideChecked))
            if (!state.hideChecked) g.inCart.forEach { add(ListRow.Item(it)) }
        }
    }
}

@Composable
fun ShoppingListScreen(nav: AppNavigator, listId: Long) {
    val vm = taskfernViewModel(key = "shopping-list-$listId") { c, _ -> ShoppingListViewModel(c.shopping, c.settings, listId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val duplicate by vm.duplicate.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    // Snackbars live in this screen's scaffold so they sit above the entry bar and the keyboard.
    val snackbar = remember { SnackbarHostState() }

    var left by remember { mutableStateOf(false) }
    fun leave() {
        if (!left) {
            left = true
            nav.back()
        }
    }
    LaunchedEffect(state.loaded, state.list) {
        if (state.loaded && state.list == null) leave()
    }

    var entry by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val entryFocus = remember { FocusRequester() }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var menu by remember { mutableStateOf(false) }
    var editingList by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var scrollTo by remember { mutableStateOf<Long?>(null) }

    val listState = rememberLazyListState()
    val rows = remember(state) { buildRows(state) }

    // Bring a just-added item into view if it landed off-screen.
    LaunchedEffect(scrollTo, rows) {
        val target = scrollTo ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it is ListRow.Item && it.item.id == target }
        if (index >= 0) {
            val visible = listState.layoutInfo.visibleItemsInfo
            val fullyVisible = visible.any { it.index == index && it.offset >= 0 && it.offset + it.size <= listState.layoutInfo.viewportEndOffset }
            if (!fullyVisible) listState.animateScrollToItem((index - 1).coerceAtLeast(0))
            scrollTo = null
        }
    }

    val onAdded: (Long) -> Unit = { id -> scrollTo = id }

    fun submitEntry() {
        val text = entry.text
        if (text.isBlank()) return
        vm.add(text, onAdded)
        entry = TextFieldValue("")
        vm.onQueryChange("")
    }

    fun deleteWithUndo(item: ShoppingItemEntity) {
        vm.delete(item)
        scope.showUndo(snackbar, resources.getString(R.string.shopping_item_deleted, item.name), resources.getString(R.string.action_undo)) {
            vm.restore(item)
        }
    }

    fun clearCart() {
        vm.clearChecked { removed ->
            scope.showUndo(
                snackbar,
                resources.getQuantityString(R.plurals.shopping_cleared, removed.size, removed.size),
                resources.getString(R.string.action_undo),
            ) { vm.restoreItems(removed) }
        }
    }

    fun share() {
        val list = state.list ?: return
        if (state.items.isEmpty()) {
            scope.showMessage(snackbar, resources.getString(R.string.shopping_share_empty))
            return
        }
        val text = ShoppingLogic.shareText(list.name, state.items) { categoryLabel(context, it) }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, list.name)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            context.startActivity(Intent.createChooser(send, resources.getString(R.string.shopping_share_title)))
        } catch (_: ActivityNotFoundException) {
            scope.showMessage(snackbar, resources.getString(R.string.shopping_share_unavailable))
        }
    }

    val listColorValue = listColor(state.list?.color)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(listColorValue))
                        Spacer(Modifier.width(10.dp))
                        Text(state.list?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    IconButton(onClick = ::share) { Icon(Icons.Outlined.Share, stringResource(R.string.action_share)) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
                        val hasChecked = state.grouped.inCart.isNotEmpty()
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_edit_list)) },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = { menu = false; editingList = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_duplicate_list)) },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                onClick = {
                                    menu = false
                                    val name = state.list?.name.orEmpty()
                                    vm.duplicateList(resources.getString(R.string.shopping_copy_name, name)) { newId ->
                                        scope.showAction(snackbar, resources.getString(R.string.shopping_duplicated), resources.getString(R.string.action_open)) {
                                            nav.openShoppingList(newId)
                                        }
                                    }
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(if (state.hideChecked) R.string.shopping_show_cart else R.string.shopping_hide_cart)) },
                                leadingIcon = { Icon(if (state.hideChecked) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, null) },
                                onClick = { menu = false; vm.setHideChecked(!state.hideChecked) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_uncheck_all)) },
                                leadingIcon = { Icon(Icons.Outlined.RemoveDone, null) },
                                enabled = hasChecked,
                                onClick = {
                                    menu = false
                                    vm.uncheckAll { ids ->
                                        scope.showUndo(snackbar, resources.getString(R.string.shopping_unchecked_all), resources.getString(R.string.action_undo)) {
                                            vm.recheck(ids)
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_clear_cart)) },
                                leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null) },
                                enabled = hasChecked,
                                onClick = { menu = false; clearCart() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(if (state.showPrices) R.string.shopping_hide_prices else R.string.shopping_show_prices)) },
                                leadingIcon = { Icon(Icons.Outlined.Payments, null) },
                                onClick = { menu = false; vm.setShowPrices(!state.showPrices) },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_delete_list)) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            ShoppingEntryBar(
                text = entry,
                onTextChange = { entry = it; vm.onQueryChange(it.text) },
                onSubmit = ::submitEntry,
                suggestions = suggestions,
                buyAgain = state.buyAgain,
                onPickMemory = { memory ->
                    vm.addMemory(memory, entry.text, onAdded)
                    entry = TextFieldValue("")
                    vm.onQueryChange("")
                },
                duplicate = duplicate,
                onMerge = { vm.mergeDuplicate(onAdded) },
                onAddAnyway = { vm.addDuplicateAnyway(onAdded) },
                onDismissDuplicate = vm::dismissDuplicate,
                focusRequester = entryFocus,
            )
        },
    ) { padding ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding))
        } else if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = stringResource(R.string.shopping_list_empty_title),
                    message = stringResource(R.string.shopping_list_empty_message),
                    illustration = R.drawable.ic_cart,
                )
            }
        } else LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(rows, key = { it.key }, contentType = { it::class }) { row ->
                when (row) {
                    ListRow.Summary -> SummaryCard(state, listColorValue, Modifier.animateItem())
                    ListRow.AllInCart -> AllInCartCard(onClear = ::clearCart, modifier = Modifier.animateItem())
                    is ListRow.Aisle -> AisleHeader(row.category, row.count, Modifier.animateItem())
                    is ListRow.CartHeader -> CartHeader(
                        count = row.count,
                        expanded = row.expanded,
                        onToggle = { vm.setHideChecked(row.expanded) },
                        onClear = ::clearCart,
                        modifier = Modifier.animateItem(),
                    )
                    is ListRow.Item -> ShoppingItemRow(
                        item = row.item,
                        showPrices = state.showPrices,
                        currency = state.currency,
                        onToggle = { vm.setChecked(row.item, !row.item.checked) },
                        onEdit = {
                            focusManager.clearFocus()
                            editingId = row.item.id
                        },
                        onDelete = { deleteWithUndo(row.item) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    editingId?.let { id ->
        val item = state.items.firstOrNull { it.id == id }
        if (item == null) {
            LaunchedEffect(id, state.loaded) { if (state.loaded) editingId = null }
        } else {
            ShoppingItemSheet(
                item = item,
                canMoveUp = ShoppingLogic.canMove(state.items, id, -1),
                canMoveDown = ShoppingLogic.canMove(state.items, id, 1),
                showPrices = state.showPrices,
                currency = state.currency,
                onSave = { e ->
                    val fresh = vm.state.value.items.firstOrNull { it.id == id } ?: return@ShoppingItemSheet
                    val updated = fresh.copy(
                        name = e.name, quantity = e.quantity, unit = e.unit, category = e.category,
                        priceCents = e.priceCents, note = e.note,
                    )
                    if (updated != fresh) vm.save(updated)
                },
                onMove = { delta -> vm.state.value.items.firstOrNull { it.id == id }?.let { vm.move(it, delta) } },
                onDelete = { deleteWithUndo(item) },
                onDismiss = { editingId = null },
            )
        }
    }

    if (editingList) {
        state.list?.let { list ->
            ListEditorDialog(
                title = stringResource(R.string.shopping_edit_list),
                confirmLabel = stringResource(R.string.action_save),
                initialName = list.name,
                initialColor = list.color,
                onConfirm = { name, color -> vm.updateList(name, color) },
                onDismiss = { editingList = false },
            )
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.shopping_delete_list_title),
            message = stringResource(R.string.shopping_delete_list_message, state.list?.name.orEmpty()),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { vm.deleteList { leave() } },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SummaryCard(state: ShoppingListUiState, color: Color, modifier: Modifier = Modifier) {
    val g = state.grouped
    val inCart = g.inCart.size
    val progress by animateFloatAsState(if (g.total == 0) 0f else inCart.toFloat() / g.total, label = "cartProgress")
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                pluralStringResource(R.plurals.shopping_progress, g.total, inCart, g.total),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (g.remaining > 0) {
                Text(
                    pluralStringResource(R.plurals.shopping_left, g.remaining, g.remaining),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            color = color,
            trackColor = color.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
        if (state.showPrices) {
            Spacer(Modifier.height(10.dp))
            val t = state.totals
            if (t.hasAnyPrice) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.shopping_total_estimate, Formatters.money(t.totalCents, state.currency)),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.shopping_total_in_cart, Formatters.money(t.inCartCents, state.currency)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (t.unpricedItems > 0) {
                    Text(
                        pluralStringResource(R.plurals.shopping_total_unpriced, t.unpricedItems, t.unpricedItems),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    stringResource(R.string.shopping_total_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AllInCartCard(onClear: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.shopping_all_in_cart),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) { Text(stringResource(R.string.shopping_clear_cart_short)) }
        }
    }
}

@Composable
private fun AisleHeader(category: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 2.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(categoryLabel(category), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CartHeader(count: Int, expanded: Boolean, onToggle: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val toggleLabel = stringResource(if (expanded) R.string.shopping_hide_cart else R.string.shopping_show_cart)
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = toggleLabel, onClick = onToggle)
            .heightIn(min = 48.dp)
            .padding(start = 8.dp, end = 4.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.shopping_in_cart), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onClear) { Text(stringResource(R.string.shopping_clear_cart_short)) }
    }
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingItemEntity,
    showPrices: Boolean,
    currency: String,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val qty = ShoppingLogic.quantityLabel(item.quantity, item.unit)
    val line = if (showPrices) ShoppingLogic.lineTotalCents(item.priceCents, item.quantity, item.unit) else null
    val editLabel = stringResource(R.string.shopping_edit_item, item.name)
    val deleteLabel = stringResource(R.string.action_delete)

    val content: @Composable () -> Unit = {
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                onToggle()
            },
            shape = MaterialTheme.shapes.medium,
            color = if (item.checked) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(editLabel) { onEdit(); true },
                        CustomAccessibilityAction(deleteLabel) { onDelete(); true },
                    )
                },
        ) {
            Row(
                Modifier.heightIn(min = 56.dp).padding(start = 4.dp, end = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskCheckbox(checked = item.checked, onToggle = onToggle, priority = 0, contentDescription = item.name)
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (item.checked) muted else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.note.isNotBlank()) {
                        Text(item.note, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (qty.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = if (item.checked) Color.Transparent else MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            qty,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (item.checked) muted else MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                if (line != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(Formatters.money(line, currency), style = MaterialTheme.typography.labelLarge, color = muted, maxLines = 1)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, editLabel, tint = muted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    val swipe = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = swipe,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
            scope.launch { swipe.reset() }
        },
        backgroundContent = {
            val active = swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (active) MaterialTheme.colorScheme.errorContainer else Color.Transparent)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (active) Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { content() }
}
