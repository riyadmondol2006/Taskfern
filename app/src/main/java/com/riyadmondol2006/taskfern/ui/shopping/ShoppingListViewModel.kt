package com.riyadmondol2006.taskfern.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadmondol2006.taskfern.data.db.ItemMemoryEntity
import com.riyadmondol2006.taskfern.data.db.ShoppingItemEntity
import com.riyadmondol2006.taskfern.data.db.ShoppingListEntity
import com.riyadmondol2006.taskfern.data.prefs.Settings
import com.riyadmondol2006.taskfern.data.prefs.SettingsRepository
import com.riyadmondol2006.taskfern.data.repo.AddItemResult
import com.riyadmondol2006.taskfern.data.repo.ItemInput
import com.riyadmondol2006.taskfern.data.repo.ShoppingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShoppingListUiState(
    val loaded: Boolean = false,
    val list: ShoppingListEntity? = null,
    val items: List<ShoppingItemEntity> = emptyList(),
    val grouped: GroupedItems = GroupedItems(emptyList(), emptyList()),
    val totals: PriceTotals = PriceTotals(),
    val hideChecked: Boolean = false,
    val showPrices: Boolean = false,
    val currency: String = "",
    /** Frequently bought items that are not already waiting on this list. */
    val buyAgain: List<ItemMemoryEntity> = emptyList(),
)

/** The item someone just tried to add twice, with the merged quantity if the amounts can be added up. */
data class DuplicatePrompt(
    val existing: ShoppingItemEntity,
    val input: ItemInput,
    val merged: Pair<String, String>?,
)

class ShoppingListViewModel(
    private val repo: ShoppingRepository,
    private val settingsRepo: SettingsRepository,
    val listId: Long,
) : ViewModel() {

    private data class Base(
        val list: ShoppingListEntity?,
        val items: List<ShoppingItemEntity>,
        val settings: Settings,
    )

    val state: StateFlow<ShoppingListUiState> = combine(
        combine(repo.observeList(listId), repo.observeItems(listId), settingsRepo.settings, ::Base),
        repo.observeFrequent(24),
    ) { base, frequent ->
        val waiting = base.items.asSequence().filter { !it.checked }.map { ShoppingRepository.nameKey(it.name) }.toSet()
        ShoppingListUiState(
            loaded = true,
            list = base.list,
            items = base.items,
            grouped = ShoppingLogic.group(base.items),
            totals = ShoppingLogic.totals(base.items),
            hideChecked = base.settings.hideCheckedShopping,
            showPrices = base.settings.showShoppingPrices,
            currency = base.settings.currencySymbol,
            buyAgain = frequent.filter { it.nameKey !in waiting }.take(12),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingListUiState())

    private val query = MutableStateFlow("")

    /** Item memory matching what is being typed, without items already waiting on the list. */
    val suggestions: StateFlow<List<ItemMemoryEntity>> = combine(
        query.debounce(90).map { ShoppingRepository.parseInput(it).name }.distinctUntilChanged(),
        state.map { s -> s.items.filter { !it.checked }.map { ShoppingRepository.nameKey(it.name) }.toSet() }.distinctUntilChanged(),
    ) { name, waiting -> name to waiting }
        .mapLatest { (name, waiting) ->
            if (name.isBlank()) emptyList()
            else repo.suggestions(name, limit = 8).filter { it.nameKey !in waiting && it.nameKey != ShoppingRepository.nameKey(name) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _duplicate = MutableStateFlow<DuplicatePrompt?>(null)
    val duplicate: StateFlow<DuplicatePrompt?> = _duplicate.asStateFlow()

    fun onQueryChange(text: String) {
        query.value = text
    }

    /** Adds typed text ("2 kg rice"). Calls [onAdded] with the new item's id, or raises a [duplicate] prompt instead. */
    fun add(text: String, onAdded: (Long) -> Unit) {
        val input = ShoppingRepository.parseInput(text)
        if (input.name.isBlank()) return
        addInput(input, onAdded)
    }

    /** Adds a remembered item, keeping any quantity typed in front of it ("2 to" + "Tomatoes" → 2 × Tomatoes). */
    fun addMemory(memory: ItemMemoryEntity, typed: String, onAdded: (Long) -> Unit) {
        val parsed = ShoppingRepository.parseInput(typed)
        val unit = parsed.unit.ifEmpty { memory.unit.takeIf { parsed.quantity.isNotEmpty() }.orEmpty() }
        addInput(ItemInput(memory.name, parsed.quantity, unit), onAdded)
    }

    private fun addInput(input: ItemInput, onAdded: (Long) -> Unit) {
        viewModelScope.launch {
            when (val result = repo.addItem(listId, input)) {
                is AddItemResult.Added -> {
                    _duplicate.value = null
                    onAdded(result.id)
                }
                is AddItemResult.AlreadyOnList -> {
                    val e = result.existing
                    _duplicate.value = DuplicatePrompt(e, input, ShoppingLogic.mergeQuantity(e.quantity, e.unit, input.quantity, input.unit))
                }
            }
        }
    }

    /** Bumps the existing item's quantity to the merged amount. */
    fun mergeDuplicate(onDone: (Long) -> Unit) {
        val prompt = _duplicate.value ?: return
        val merged = prompt.merged ?: return
        _duplicate.value = null
        viewModelScope.launch {
            val fresh = state.value.items.firstOrNull { it.id == prompt.existing.id } ?: prompt.existing
            repo.updateItem(fresh.copy(quantity = merged.first, unit = merged.second))
            onDone(fresh.id)
        }
    }

    /** Adds the duplicate as its own line after all. */
    fun addDuplicateAnyway(onAdded: (Long) -> Unit) {
        val prompt = _duplicate.value ?: return
        _duplicate.value = null
        viewModelScope.launch {
            val result = repo.addItem(listId, prompt.input, allowDuplicate = true)
            if (result is AddItemResult.Added) onAdded(result.id)
        }
    }

    fun dismissDuplicate() {
        _duplicate.value = null
    }

    fun setChecked(item: ShoppingItemEntity, checked: Boolean) {
        viewModelScope.launch { repo.setChecked(item.id, checked) }
    }

    fun save(item: ShoppingItemEntity) {
        viewModelScope.launch { repo.updateItem(item) }
    }

    fun delete(item: ShoppingItemEntity) {
        viewModelScope.launch { repo.deleteItem(item) }
    }

    fun restore(item: ShoppingItemEntity) {
        viewModelScope.launch { repo.restoreItem(item) }
    }

    fun move(item: ShoppingItemEntity, delta: Int) {
        val reordered = ShoppingLogic.moveWithinAisle(state.value.items, item.id, delta) ?: return
        viewModelScope.launch { repo.reorder(listId, reordered) }
    }

    fun clearChecked(onCleared: (List<ShoppingItemEntity>) -> Unit) {
        viewModelScope.launch {
            val removed = repo.clearChecked(listId)
            if (removed.isNotEmpty()) onCleared(removed)
        }
    }

    fun restoreItems(items: List<ShoppingItemEntity>) {
        viewModelScope.launch { repo.restoreItems(items) }
    }

    /** Unticks everything; returns the ids that were ticked so the change can be undone. */
    fun uncheckAll(onDone: (List<Long>) -> Unit) {
        val ticked = state.value.items.filter { it.checked }.map { it.id }
        if (ticked.isEmpty()) return
        viewModelScope.launch {
            repo.uncheckAll(listId)
            onDone(ticked)
        }
    }

    fun recheck(ids: List<Long>) {
        viewModelScope.launch { ids.forEach { repo.setChecked(it, true) } }
    }

    fun setHideChecked(hide: Boolean) {
        viewModelScope.launch { settingsRepo.setHideCheckedShopping(hide) }
    }

    fun setShowPrices(show: Boolean) {
        viewModelScope.launch { settingsRepo.setShowShoppingPrices(show) }
    }

    fun updateList(name: String, color: Int) {
        viewModelScope.launch { repo.updateList(listId, name, color) }
    }

    fun duplicateList(copyName: String, onDone: (Long) -> Unit) {
        viewModelScope.launch { repo.duplicateList(listId, copyName)?.let(onDone) }
    }

    fun deleteList(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteList(listId)
            onDone()
        }
    }
}
