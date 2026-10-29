package com.riyadmondol2006.taskfern.ui.shopping

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.TemplateEntity
import com.riyadmondol2006.taskfern.domain.GroceryCategories
import com.riyadmondol2006.taskfern.ui.common.listColor
import com.riyadmondol2006.taskfern.ui.theme.LocalTaskfernColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@StringRes
fun categoryLabelRes(category: String): Int? = when (category) {
    GroceryCategories.PRODUCE -> R.string.shopping_cat_produce
    GroceryCategories.DAIRY -> R.string.shopping_cat_dairy
    GroceryCategories.BAKERY -> R.string.shopping_cat_bakery
    GroceryCategories.MEAT -> R.string.shopping_cat_meat
    GroceryCategories.PANTRY -> R.string.shopping_cat_pantry
    GroceryCategories.FROZEN -> R.string.shopping_cat_frozen
    GroceryCategories.DRINKS -> R.string.shopping_cat_drinks
    GroceryCategories.SNACKS -> R.string.shopping_cat_snacks
    GroceryCategories.HOUSEHOLD -> R.string.shopping_cat_household
    GroceryCategories.PERSONAL -> R.string.shopping_cat_personal
    GroceryCategories.BABY_PET -> R.string.shopping_cat_baby_pet
    GroceryCategories.OTHER -> R.string.shopping_cat_other
    else -> null
}

/** Localised aisle name; categories that are not built in (imported data) are shown as stored. */
fun categoryLabel(context: Context, category: String): String {
    val normalized = ShoppingLogic.normalizedCategory(category)
    return categoryLabelRes(normalized)?.let(context::getString) ?: normalized
}

@Composable
fun categoryLabel(category: String): String {
    val normalized = ShoppingLogic.normalizedCategory(category)
    return categoryLabelRes(normalized)?.let { stringResource(it) } ?: normalized
}

/** Number of colours offered by the colour picker (matches the theme's list palette). */
@Composable
fun paletteSize(): Int = LocalTaskfernColors.current.listColors.size.coerceAtLeast(1)

/** A row of colour dots, 48 dp touch targets, the chosen one ringed and ticked. */
@Composable
fun ColorDots(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val count = paletteSize()
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(count) { index ->
            val color = listColor(index)
            val isSelected = Math.floorMod(selected, count) == index
            val label = stringResource(R.string.shopping_color_option, index + 1)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { onSelect(index) }
                    .semantics {
                        role = Role.RadioButton
                        this.selected = isSelected
                        contentDescription = label
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (isSelected) 34.dp else 28.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.surface, CircleShape) else Modifier,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        androidx.compose.material3.Icon(
                            Icons.Outlined.Check, null, tint = contentColorOn(color), modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Black or white, whichever reads better on [color]. */
fun contentColorOn(color: Color): Color {
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return if (luminance > 0.55f) Color(0xFF14201A) else Color.White
}

/** Name + colour dialog used to create and edit a shopping list. */
@Composable
fun ListEditorDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialColor: Int,
    onConfirm: (name: String, color: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length)))
    }
    var color by rememberSaveable { mutableIntStateOf(initialColor) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { focus.requestFocus() }
    }
    val canSave = name.text.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.copy(text = it.text.replace("\n", " ").take(60)) },
                    label = { Text(stringResource(R.string.shopping_list_name)) },
                    placeholder = { Text(stringResource(R.string.shopping_list_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSave) { onConfirm(name.text.trim(), color); onDismiss() } }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.shopping_list_color), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                ColorDots(selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = { onConfirm(name.text.trim(), color); onDismiss() }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Bottom sheet listing templates of one kind; picking one creates a list or routine from it. */
@Composable
fun TemplatePickerSheet(
    title: String,
    emptyMessage: String,
    templates: List<TemplateEntity>,
    onPick: (TemplateEntity) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(8.dp))
            if (templates.isEmpty()) {
                Text(
                    emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(templates, key = { it.id }) { template ->
                        val lines = template.itemLines
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(template); onDismiss() }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(template.emoji.ifEmpty { "📋" }, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(template.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    lines.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            TextButton(onClick = { onDismiss(); onManage() }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.shopping_manage_templates))
            }
        }
    }
}

/** Snackbar with a custom action label (e.g. "Open"); runs [onAction] if it is pressed. */
fun CoroutineScope.showAction(host: SnackbarHostState, message: String, actionLabel: String, onAction: () -> Unit) {
    launch {
        host.currentSnackbarData?.dismiss()
        val result = host.showSnackbar(message, actionLabel = actionLabel, withDismissAction = true, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) onAction()
    }
}
