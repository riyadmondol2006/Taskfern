package com.riyadmondol2006.taskfern.ui.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.ShoppingItemEntity
import com.riyadmondol2006.taskfern.domain.GroceryCategories
import com.riyadmondol2006.taskfern.ui.common.Formatters
import kotlinx.coroutines.launch

/** The editable fields of an item; applied to the latest copy of the item when saved. */
data class ItemEdits(
    val name: String,
    val quantity: String,
    val unit: String,
    val category: String,
    val priceCents: Long?,
    val note: String,
)

/**
 * Item details in a bottom sheet: name, quantity, unit, aisle, optional price and a note, plus moving the item within
 * its aisle and deleting it. Closing the sheet (Done, swipe down or back) keeps valid changes.
 */
@Composable
fun ShoppingItemSheet(
    item: ShoppingItemEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    showPrices: Boolean,
    currency: String,
    onSave: (ItemEdits) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var name by rememberSaveable(item.id) { mutableStateOf(item.name) }
    var quantity by rememberSaveable(item.id) { mutableStateOf(item.quantity) }
    var unit by rememberSaveable(item.id) { mutableStateOf(item.unit) }
    var category by rememberSaveable(item.id) { mutableStateOf(ShoppingLogic.normalizedCategory(item.category)) }
    var price by rememberSaveable(item.id) { mutableStateOf(item.priceCents?.let(ShoppingLogic::formatPriceInput).orEmpty()) }
    var note by rememberSaveable(item.id) { mutableStateOf(item.note) }

    val parsedPrice = ShoppingLogic.parsePrice(price)
    val priceValid = parsedPrice !is PriceInput.Invalid
    val valid = name.isNotBlank() && priceValid

    fun edits() = ItemEdits(
        name = name.trim(),
        quantity = quantity.trim(),
        unit = unit.trim(),
        category = category,
        priceCents = when (parsedPrice) {
            is PriceInput.Valid -> parsedPrice.cents
            PriceInput.Empty -> null
            PriceInput.Invalid -> item.priceCents
        },
        note = note.trim(),
    )

    fun close(save: Boolean) {
        if (save && valid) onSave(edits())
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = { if (valid) onSave(edits()); onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(stringResource(R.string.shopping_item_details), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.replace("\n", " ").take(80) },
                label = { Text(stringResource(R.string.shopping_item_name)) },
                isError = name.isBlank(),
                supportingText = if (name.isBlank()) ({ Text(stringResource(R.string.shopping_item_name_required)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.replace("\n", "").take(12) },
                    label = { Text(stringResource(R.string.shopping_item_quantity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it.replace("\n", "").take(12) },
                    label = { Text(stringResource(R.string.shopping_item_unit)) },
                    placeholder = { Text(stringResource(R.string.shopping_item_unit_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
            }
            if (showPrices) {
                Spacer(Modifier.height(8.dp))
                val line = (parsedPrice as? PriceInput.Valid)?.let { ShoppingLogic.lineTotalCents(it.cents, quantity, unit) }
                val qtyNumber = ShoppingLogic.parseQuantity(quantity)
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.replace("\n", "").take(14) },
                    label = { Text(stringResource(R.string.shopping_item_price)) },
                    prefix = if (currency.isNotEmpty()) ({ Text(currency) }) else null,
                    isError = !priceValid,
                    supportingText = {
                        Text(
                            when {
                                !priceValid -> stringResource(R.string.shopping_item_price_invalid)
                                line != null && qtyNumber != null && qtyNumber != 1.0 ->
                                    stringResource(
                                        R.string.shopping_item_price_line,
                                        ShoppingLogic.quantityLabel(quantity, unit),
                                        Formatters.money(line, currency),
                                    )
                                else -> stringResource(R.string.shopping_item_price_help)
                            },
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(200) },
                label = { Text(stringResource(R.string.shopping_item_note)) },
                placeholder = { Text(stringResource(R.string.shopping_item_note_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.shopping_item_aisle), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            val choices = if (category in GroceryCategories.ordered) GroceryCategories.ordered else GroceryCategories.ordered + category
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { c ->
                    FilterChip(
                        selected = c == category,
                        onClick = { category = c },
                        label = { Text(categoryLabel(c)) },
                    )
                }
            }

            if (!item.checked && (canMoveUp || canMoveDown)) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.shopping_item_order), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onMove(-1) }, enabled = canMoveUp, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.ArrowUpward, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.shopping_move_up))
                    }
                    OutlinedButton(onClick = { onMove(1) }, enabled = canMoveDown, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.ArrowDownward, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.shopping_move_down))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        onDelete()
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_delete))
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { close(save = true) }, enabled = valid) {
                    Text(stringResource(R.string.action_done))
                }
            }
        }
    }
}
