package com.riyadmondol2006.taskfern.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.riyadmondol2006.taskfern.AppContainer
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.TemplateEntity
import com.riyadmondol2006.taskfern.data.db.TemplateKind
import com.riyadmondol2006.taskfern.ui.common.ConfirmDialog
import com.riyadmondol2006.taskfern.ui.common.EmptyState
import com.riyadmondol2006.taskfern.ui.common.LocalSnackbarHost
import com.riyadmondol2006.taskfern.ui.common.SectionHeader
import com.riyadmondol2006.taskfern.ui.common.showMessage
import com.riyadmondol2006.taskfern.ui.common.taskfernViewModel
import com.riyadmondol2006.taskfern.ui.navigation.AppNavigator
import com.riyadmondol2006.taskfern.ui.navigation.SmartViewKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TemplatesViewModel(private val c: AppContainer) : ViewModel() {
    val templates = c.templates.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(template: TemplateEntity) {
        c.appScope.launch { c.templates.save(template) }
    }

    fun delete(id: Long) {
        c.appScope.launch { c.templates.delete(id) }
    }

    /** Creates the list or routine and returns its id. */
    suspend fun use(template: TemplateEntity): Long = c.templates.apply(template)
}

private val KIND_ORDER = listOf(TemplateKind.TASKS, TemplateKind.SHOPPING, TemplateKind.ROUTINE)

private fun kindLabel(kind: String) = when (kind) {
    TemplateKind.SHOPPING -> R.string.templates_kind_shopping
    TemplateKind.ROUTINE -> R.string.templates_kind_routine
    else -> R.string.templates_kind_tasks
}

private fun kindDefaultEmoji(kind: String) = when (kind) {
    TemplateKind.SHOPPING -> "🛒"
    TemplateKind.ROUTINE -> "🌿"
    else -> "📝"
}

@Composable
fun TemplatesScreen(nav: AppNavigator) {
    val vm = taskfernViewModel { c, _ -> TemplatesViewModel(c) }
    val templates by vm.templates.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.templates_use_failed)

    var editing by remember { mutableStateOf<TemplateEntity?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TemplateEntity?>(null) }
    var applying by remember { mutableStateOf(false) }

    fun use(t: TemplateEntity) {
        if (applying) return
        applying = true
        scope.launch {
            try {
                val id = vm.use(t)
                when (t.kind) {
                    TemplateKind.SHOPPING -> nav.openShoppingList(id)
                    TemplateKind.ROUTINE -> nav.openRoutine(id)
                    else -> nav.openTaskList(SmartViewKey.LIST, id)
                }
            } catch (_: IllegalArgumentException) {
                scope.showMessage(snackbar, failed)
            } finally {
                applying = false
            }
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.templates_title),
        onBack = nav::back,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.templates_new)) },
            )
        },
    ) { padding ->
        val all = templates ?: return@SettingsScaffold
        if (all.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = stringResource(R.string.templates_empty_title),
                    message = stringResource(R.string.templates_empty_message),
                )
            }
            return@SettingsScaffold
        }
        val grouped = all.groupBy { if (it.kind in KIND_ORDER) it.kind else TemplateKind.TASKS }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.templates_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            KIND_ORDER.forEach { kind ->
                val list = grouped[kind].orEmpty()
                if (list.isEmpty()) return@forEach
                item(key = "header-$kind") { SectionHeader(stringResource(kindLabel(kind)), count = list.size) }
                items(list, key = { it.id }) { t ->
                    TemplateCard(
                        template = t,
                        enabled = !applying,
                        onUse = { use(t) },
                        onEdit = { editing = t },
                        onDelete = { deleting = t },
                    )
                }
            }
        }
    }

    if (creating) {
        TemplateEditor(initial = null, onSave = vm::save, onDismiss = { creating = false })
    }
    editing?.let { t -> TemplateEditor(initial = t, onSave = vm::save, onDismiss = { editing = null }) }
    deleting?.let { t ->
        ConfirmDialog(
            title = stringResource(R.string.templates_delete_title),
            message = stringResource(R.string.templates_delete_message, t.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { vm.delete(t.id) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun TemplateCard(
    template: TemplateEntity,
    enabled: Boolean,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val lines = template.itemLines
    var menu by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(template.emoji.ifEmpty { kindDefaultEmoji(template.kind) }, style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(template.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        pluralStringResource(R.plurals.templates_item_count, lines.size, lines.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = { menu = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
            if (lines.isNotEmpty()) {
                Text(
                    lines.take(4).joinToString(" · ") + if (lines.size > 4) " …" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp, end = 12.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 12.dp), horizontalArrangement = Arrangement.End) {
                FilledTonalButton(onClick = onUse, enabled = enabled && lines.isNotEmpty()) {
                    Text(stringResource(R.string.templates_use))
                }
            }
        }
    }
}

/** Create or edit a template: name, emoji, kind and one item per line. */
@Composable
private fun TemplateEditor(initial: TemplateEntity?, onSave: (TemplateEntity) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var emoji by rememberSaveable { mutableStateOf(initial?.emoji.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: TemplateKind.TASKS) }
    var items by rememberSaveable { mutableStateOf(initial?.itemLines?.joinToString("\n").orEmpty()) }
    val itemCount = items.lines().count { it.isNotBlank() }
    val valid = name.isNotBlank() && itemCount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.templates_new else R.string.templates_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.Top) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = firstGraphemes(it) },
                        label = { Text(stringResource(R.string.templates_emoji)) },
                        placeholder = { Text(kindDefaultEmoji(kind)) },
                        singleLine = true,
                        modifier = Modifier.width(88.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(60) },
                        label = { Text(stringResource(R.string.templates_name)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    stringResource(R.string.templates_kind),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    KIND_ORDER.forEachIndexed { i, k ->
                        SegmentedButton(
                            selected = kind == k,
                            onClick = { kind = k },
                            shape = SegmentedButtonDefaults.itemShape(i, KIND_ORDER.size),
                        ) { Text(stringResource(kindShortLabel(k)), maxLines = 1) }
                    }
                }
                OutlinedTextField(
                    value = items,
                    onValueChange = { items = it.take(10_000) },
                    label = { Text(stringResource(R.string.templates_items)) },
                    supportingText = { Text(stringResource(R.string.templates_items_hint)) },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val lines = items.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    val base = initial ?: TemplateEntity(kind = kind, name = "", items = "")
                    onSave(base.copy(kind = kind, name = name.trim(), emoji = emoji.trim(), items = lines.joinToString("\n")))
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun kindShortLabel(kind: String) = when (kind) {
    TemplateKind.SHOPPING -> R.string.templates_kind_shopping_short
    TemplateKind.ROUTINE -> R.string.templates_kind_routine_short
    else -> R.string.templates_kind_tasks_short
}

/** Keeps at most two user-perceived characters, so an emoji with modifiers or a flag stays intact. */
private fun firstGraphemes(text: String, max: Int = 2): String {
    val breaks = java.text.BreakIterator.getCharacterInstance()
    breaks.setText(text)
    var end = 0
    repeat(max) {
        val next = breaks.next()
        if (next == java.text.BreakIterator.DONE) return text
        end = next
    }
    return text.substring(0, end)
}
