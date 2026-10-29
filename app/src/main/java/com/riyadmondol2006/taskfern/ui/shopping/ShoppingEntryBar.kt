package com.riyadmondol2006.taskfern.ui.shopping

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riyadmondol2006.taskfern.R
import com.riyadmondol2006.taskfern.data.db.ItemMemoryEntity
import com.riyadmondol2006.taskfern.data.repo.ShoppingRepository

/**
 * The quick-entry bar pinned to the bottom of a shopping list, above the keyboard. Enter adds the item and keeps the
 * keyboard open for the next one. While the field is empty it offers "buy again" items; while typing it offers matches
 * from the item memory.
 */
@Composable
fun ShoppingEntryBar(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    suggestions: List<ItemMemoryEntity>,
    buyAgain: List<ItemMemoryEntity>,
    onPickMemory: (ItemMemoryEntity) -> Unit,
    duplicate: DuplicatePrompt?,
    onMerge: () -> Unit,
    onAddAnyway: () -> Unit,
    onDismissDuplicate: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val parsed = remember(text.text) { ShoppingRepository.parseInput(text.text) }
    val typing = text.text.isNotBlank()
    val chips = if (typing) suggestions else buyAgain

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(top = 10.dp, bottom = 10.dp)
                .animateContentSize(),
        ) {
            AnimatedVisibility(
                visible = duplicate != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                duplicate?.let {
                    DuplicateBanner(it, onMerge, onAddAnyway, onDismissDuplicate, Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp))
                }
            }

            if (chips.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                ) {
                    if (!typing) {
                        item(key = "label") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.shopping_buy_again),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    items(chips, key = { it.nameKey }) { memory ->
                        val label = if (typing && parsed.quantity.isNotEmpty()) {
                            ShoppingLogic.quantityLabel(parsed.quantity, parsed.unit.ifEmpty { memory.unit }) + " " + memory.name
                        } else memory.name
                        SuggestionChip(
                            onClick = { onPickMemory(memory) },
                            label = { Text(label, maxLines = 1) },
                            icon = { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = text,
                    onValueChange = { onTextChange(it.copy(text = it.text.replace("\n", " "))) },
                    placeholder = { Text(stringResource(R.string.shopping_entry_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    trailingIcon = if (typing && parsed.quantity.isNotEmpty()) {
                        {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Text(
                                    ShoppingLogic.quantityLabel(parsed.quantity, parsed.unit),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (typing) onSubmit() else focusManager.clearFocus() }),
                    shape = RoundedCornerShape(26.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = onSubmit, enabled = typing, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.shopping_add_item))
                }
            }
        }
    }
}

@Composable
private fun DuplicateBanner(
    prompt: DuplicatePrompt,
    onMerge: () -> Unit,
    onAddAnyway: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    Text(
                        stringResource(R.string.shopping_duplicate_title, prompt.existing.name),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    val qty = ShoppingLogic.quantityLabel(prompt.existing.quantity, prompt.existing.unit)
                    if (qty.isNotEmpty()) {
                        Text(
                            stringResource(R.string.shopping_duplicate_current, qty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.shopping_duplicate_keep), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAddAnyway) { Text(stringResource(R.string.shopping_duplicate_add_anyway)) }
                val merged = prompt.merged
                if (merged != null) {
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = onMerge) {
                        Text(stringResource(R.string.shopping_duplicate_merge, ShoppingLogic.quantityLabel(merged.first, merged.second)))
                    }
                } else {
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = onDismiss) { Text(stringResource(R.string.shopping_duplicate_keep)) }
                }
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}
