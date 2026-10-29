package com.riyadmondol2006.taskfern.data.repo

import androidx.room.withTransaction
import com.riyadmondol2006.taskfern.data.db.ItemMemoryEntity
import com.riyadmondol2006.taskfern.data.db.ShoppingItemEntity
import com.riyadmondol2006.taskfern.data.db.ShoppingListEntity
import com.riyadmondol2006.taskfern.data.db.TaskfernDatabase
import com.riyadmondol2006.taskfern.domain.GroceryCategories
import java.time.Clock

/** Result of adding an item: either new, or merged into an unchecked item with the same name. */
sealed interface AddItemResult {
    data class Added(val id: Long) : AddItemResult
    data class AlreadyOnList(val existing: ShoppingItemEntity) : AddItemResult
}

/** Parsed "2 kg rice" style input. */
data class ItemInput(val name: String, val quantity: String = "", val unit: String = "")

class ShoppingRepository(
    private val db: TaskfernDatabase,
    private val events: DataEvents = DataEvents.None,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val dao = db.shoppingDao()
    private fun now() = clock.millis()

    fun observeLists() = dao.observeListsWithCounts()
    fun observeList(id: Long) = dao.observeList(id)
    fun observeItems(listId: Long) = dao.observeItems(listId)
    fun observeFrequent(limit: Int = 12) = dao.observeFrequent(limit)
    fun observeRecent(limit: Int = 12) = dao.observeRecent(limit)
    suspend fun getLists() = dao.getLists()
    suspend fun getList(id: Long) = dao.getList(id)
    suspend fun getItems(listId: Long) = dao.getItems(listId)

    suspend fun suggestions(prefix: String, limit: Int = 6): List<ItemMemoryEntity> {
        val key = nameKey(prefix)
        if (key.isEmpty()) return emptyList()
        val escaped = key.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return dao.suggestions(escaped, limit)
    }

    suspend fun createList(name: String, color: Int = 0): Long =
        dao.insertList(ShoppingListEntity(name = name.trim().ifEmpty { "Shopping" }, color = color, position = dao.nextListPosition(), createdAt = now()))
            .also { events.shoppingChanged(listOf(it)) }

    suspend fun renameList(id: Long, name: String) {
        val list = dao.getList(id) ?: return
        dao.updateList(list.copy(name = name.trim().ifEmpty { list.name }))
        events.shoppingChanged(listOf(id))
    }

    suspend fun deleteList(id: Long) {
        dao.deleteList(id)
        events.shoppingChanged(listOf(id))
    }

    /** Copies a list with all its items unchecked. */
    suspend fun duplicateList(id: Long, newName: String? = null): Long? {
        val newId = db.withTransaction {
            val list = dao.getList(id) ?: return@withTransaction null
            val copyId = dao.insertList(
                list.copy(id = 0, name = newName ?: "${list.name} (copy)", position = dao.nextListPosition(), createdAt = now()),
            )
            dao.insertItems(dao.getItems(id).map { it.copy(id = 0, listId = copyId, checked = false, checkedAt = null, createdAt = now()) })
            copyId
        }
        if (newId != null) events.shoppingChanged(listOf(newId))
        return newId
    }

    /**
     * Adds an item. If an unchecked item with the same name is already on the list nothing is added and the existing
     * item is returned, so the UI can offer to bump its quantity instead of creating a duplicate.
     */
    suspend fun addItem(listId: Long, input: ItemInput, category: String? = null, allowDuplicate: Boolean = false): AddItemResult {
        val name = input.name.trim()
        require(name.isNotEmpty()) { "An item needs a name" }
        val result = db.withTransaction {
            if (!allowDuplicate) {
                dao.findUncheckedByName(listId, nameKey(name))?.let { return@withTransaction AddItemResult.AlreadyOnList(it) }
            }
            val memory = dao.getMemory(nameKey(name))
            val cat = category ?: memory?.category?.takeIf { it.isNotEmpty() } ?: GroceryCategories.guess(name)
            val id = dao.insertItem(
                ShoppingItemEntity(
                    listId = listId, name = name, quantity = input.quantity.trim(),
                    unit = input.unit.trim().ifEmpty { memory?.unit.orEmpty() }, category = cat,
                    position = dao.nextItemPosition(listId), createdAt = now(),
                    priceCents = memory?.lastPriceCents,
                ),
            )
            remember(name, cat, input.unit.trim().ifEmpty { memory?.unit.orEmpty() }, memory?.lastPriceCents)
            AddItemResult.Added(id)
        }
        events.shoppingChanged(listOf(listId))
        return result
    }

    suspend fun updateItem(item: ShoppingItemEntity) {
        val clean = item.copy(name = item.name.trim().ifEmpty { "Item" }, quantity = item.quantity.trim(), unit = item.unit.trim())
        db.withTransaction {
            dao.updateItem(clean)
            remember(clean.name, clean.category, clean.unit, clean.priceCents, countUse = false)
        }
        events.shoppingChanged(listOf(item.listId))
    }

    /** Ticks an item. Idempotent: ticking twice is harmless, so fast double taps on a widget are safe. */
    suspend fun setChecked(itemId: Long, checked: Boolean) {
        val item = dao.getItem(itemId) ?: return
        if (item.checked == checked) return
        dao.setChecked(itemId, checked, if (checked) now() else null)
        events.shoppingChanged(listOf(item.listId))
    }

    suspend fun deleteItem(item: ShoppingItemEntity) {
        dao.deleteItem(item.id)
        events.shoppingChanged(listOf(item.listId))
    }

    suspend fun restoreItem(item: ShoppingItemEntity) {
        dao.insertItem(item)
        events.shoppingChanged(listOf(item.listId))
    }

    suspend fun clearChecked(listId: Long): List<ShoppingItemEntity> {
        val removed = db.withTransaction {
            val checked = dao.getItems(listId).filter { it.checked }
            dao.clearChecked(listId)
            checked
        }
        events.shoppingChanged(listOf(listId))
        return removed
    }

    suspend fun restoreItems(items: List<ShoppingItemEntity>) {
        if (items.isEmpty()) return
        dao.insertItems(items)
        events.shoppingChanged(items.map { it.listId }.distinct())
    }

    suspend fun uncheckAll(listId: Long) {
        dao.uncheckAll(listId)
        events.shoppingChanged(listOf(listId))
    }

    suspend fun reorder(listId: Long, ordered: List<ShoppingItemEntity>) {
        db.withTransaction { dao.updateItems(ordered.mapIndexed { i, it -> it.copy(position = i) }) }
        events.shoppingChanged(listOf(listId))
    }

    suspend fun forgetSuggestion(name: String) = dao.forgetMemory(nameKey(name))

    /** Adds many names at once (templates, "buy again"), skipping names already unchecked on the list. */
    suspend fun addAll(listId: Long, names: List<String>): Int {
        var added = 0
        names.forEach { line ->
            val parsed = parseInput(line)
            if (parsed.name.isNotEmpty() && addItem(listId, parsed) is AddItemResult.Added) added++
        }
        return added
    }

    private suspend fun remember(name: String, category: String, unit: String, priceCents: Long?, countUse: Boolean = true) {
        val key = nameKey(name)
        val old = dao.getMemory(key)
        dao.upsertMemory(
            ItemMemoryEntity(
                nameKey = key, name = name, category = category, unit = unit,
                useCount = (old?.useCount ?: 0) + if (countUse) 1 else 0,
                lastUsedAt = if (countUse) now() else old?.lastUsedAt ?: now(),
                lastPriceCents = priceCents ?: old?.lastPriceCents,
            ),
        )
    }

    companion object {
        fun nameKey(name: String) = name.trim().lowercase().replace(Regex("\\s+"), " ")

        private val units = setOf(
            "kg", "g", "gr", "lb", "lbs", "oz", "l", "ml", "pcs", "pc", "pack", "packs", "bag", "bags", "box", "boxes",
            "bottle", "bottles", "can", "cans", "dozen", "jar", "jars", "bunch", "loaf", "loaves",
        )

        /** "2 kg rice", "rice 2kg", "3x eggs", "eggs ×6" → name + quantity + unit. Falls back to the whole text as name. */
        fun parseInput(text: String): ItemInput {
            val t = text.trim().replace(Regex("\\s+"), " ")
            val lead = Regex("""^(\d+(?:[.,]\d+)?|½|¼|¾)\s*(?:x|×)?\s*([a-zA-Z]+)?\s+(.+)$""").find(t)
            if (lead != null) {
                val (qty, maybeUnit, rest) = lead.destructured
                return if (maybeUnit.lowercase() in units) ItemInput(rest, qty, maybeUnit.lowercase())
                else ItemInput((maybeUnit + " " + rest).trim(), qty)
            }
            val trail = Regex("""^(.+?)\s+(?:x|×)?\s*(\d+(?:[.,]\d+)?)\s*([a-zA-Z]+)?$""").find(t)
            if (trail != null) {
                val (rest, qty, maybeUnit) = trail.destructured
                if (maybeUnit.isEmpty() || maybeUnit.lowercase() in units) return ItemInput(rest, qty, maybeUnit.lowercase())
            }
            return ItemInput(t)
        }
    }
}
