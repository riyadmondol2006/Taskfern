package com.riyadmondol2006.taskfern.ui.shopping

import com.riyadmondol2006.taskfern.data.db.ShoppingItemEntity
import com.riyadmondol2006.taskfern.domain.GroceryCategories
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.roundToLong

/** One aisle of unchecked items. */
data class ShoppingSection(val category: String, val items: List<ShoppingItemEntity>)

/** A list split into aisles of things still to buy, plus the items already in the cart. */
data class GroupedItems(val sections: List<ShoppingSection>, val inCart: List<ShoppingItemEntity>) {
    val remaining: Int get() = sections.sumOf { it.items.size }
    val total: Int get() = remaining + inCart.size
}

/** Estimated money totals. Items without a price are counted so the UI can mark the total as partial. */
data class PriceTotals(
    val totalCents: Long = 0,
    val remainingCents: Long = 0,
    val inCartCents: Long = 0,
    val pricedItems: Int = 0,
    val unpricedItems: Int = 0,
) {
    val hasAnyPrice: Boolean get() = pricedItems > 0
}

/** What the user typed into a price field. */
sealed interface PriceInput {
    data object Empty : PriceInput
    data class Valid(val cents: Long) : PriceInput
    data object Invalid : PriceInput
}

/** Pure list logic for the shopping screens: grouping, quantities, prices, ordering and sharing. */
object ShoppingLogic {

    fun normalizedCategory(category: String): String = category.trim().ifEmpty { GroceryCategories.OTHER }

    /**
     * Aisle order: the known aisles in [GroceryCategories.ordered]; any unknown (older or imported) category sits just
     * before "Other", alphabetically.
     */
    val categoryComparator: Comparator<String> = Comparator { a, b ->
        val ra = rank(a)
        val rb = rank(b)
        if (ra != rb) ra.compareTo(rb) else a.compareTo(b, ignoreCase = true)
    }

    private fun rank(category: String): Int {
        val index = GroceryCategories.ordered.indexOf(category)
        return if (index >= 0) index * 2 else GroceryCategories.ordered.indexOf(GroceryCategories.OTHER) * 2 - 1
    }

    private val byPosition = compareBy<ShoppingItemEntity>({ it.position }, { it.id })

    /** Unchecked items grouped by aisle in store order; checked items most recently ticked first. */
    fun group(items: List<ShoppingItemEntity>): GroupedItems {
        val sections = items.asSequence()
            .filter { !it.checked }
            .groupBy { normalizedCategory(it.category) }
            .toSortedMap(categoryComparator)
            .map { (category, list) -> ShoppingSection(category, list.sortedWith(byPosition)) }
        val inCart = items.filter { it.checked }
            .sortedWith(compareByDescending<ShoppingItemEntity> { it.checkedAt ?: 0L }.then(byPosition))
        return GroupedItems(sections, inCart)
    }

    /** "2", "1.5", "1,5", "½", "1½", "1/2" → number; blank or unreadable → null. */
    fun parseQuantity(text: String): Double? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return null
        val fractions = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75)
        val last = t.last()
        val value = when {
            last in fractions -> {
                val whole = t.dropLast(1).trim()
                val base = if (whole.isEmpty()) 0.0 else whole.toDoubleOrNull() ?: return null
                base + fractions.getValue(last)
            }
            '/' in t -> {
                val parts = t.split('/')
                if (parts.size != 2) return null
                val num = parts[0].trim().toDoubleOrNull() ?: return null
                val den = parts[1].trim().toDoubleOrNull() ?: return null
                if (den == 0.0) return null
                num / den
            }
            else -> t.toDoubleOrNull()
        } ?: return null
        return value.takeIf { it.isFinite() && it > 0 }
    }

    /** 3.0 → "3", 1.5 → "1.5", 0.333 → "0.33". */
    fun formatQuantity(value: Double): String =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    /** "2 kg", "×3", "kg" or "" for display next to the item name. */
    fun quantityLabel(quantity: String, unit: String): String {
        val q = quantity.trim()
        val u = unit.trim()
        return when {
            q.isEmpty() && u.isEmpty() -> ""
            q.isEmpty() -> u
            u.isEmpty() -> "×$q"
            else -> "$q $u"
        }
    }

    /**
     * New quantity when the same item is added again: "1 kg" + "2 kg" → "3 kg", "rice" + "rice" → "2", "2 kg" + "rice"
     * → "3 kg". Returns null when the amounts cannot be added up (different units, unreadable numbers).
     */
    fun mergeQuantity(existingQuantity: String, existingUnit: String, addedQuantity: String, addedUnit: String): Pair<String, String>? {
        val eu = existingUnit.trim()
        val au = addedUnit.trim()
        val existing = if (existingQuantity.isBlank()) 1.0 else parseQuantity(existingQuantity) ?: return null
        val added = if (addedQuantity.isBlank()) 1.0 else parseQuantity(addedQuantity) ?: return null
        if (au.isNotEmpty() && !au.equals(eu, ignoreCase = true)) return null
        return formatQuantity(existing + added) to eu
    }

    /** Weights and volumes: "500 g" at €2.49 means the 500 g cost €2.49, not 500 × €2.49. */
    private val measureUnits = setOf("g", "gr", "kg", "mg", "lb", "lbs", "oz", "l", "ml", "cl", "dl")

    /**
     * Price of one line. For counted items ("6", "2 packs") the price is per piece and is multiplied by the quantity;
     * for weights and volumes ("500 g", "2 l") the price is for the whole amount. Without a readable quantity the price
     * counts once.
     */
    fun lineTotalCents(priceCents: Long?, quantity: String, unit: String = ""): Long? {
        if (priceCents == null) return null
        if (unit.trim().lowercase() in measureUnits) return priceCents
        val multiplier = parseQuantity(quantity) ?: 1.0
        return (priceCents * multiplier).roundToLong()
    }

    fun totals(items: List<ShoppingItemEntity>): PriceTotals {
        var total = 0L
        var remaining = 0L
        var cart = 0L
        var priced = 0
        var unpriced = 0
        items.forEach { item ->
            val line = lineTotalCents(item.priceCents, item.quantity, item.unit)
            if (line == null) {
                unpriced++
            } else {
                priced++
                total += line
                if (item.checked) cart += line else remaining += line
            }
        }
        return PriceTotals(total, remaining, cart, priced, unpriced)
    }

    /** Accepts "2", "2.5", "2,50", "1 234.5" with an optional leading currency symbol; at most two decimals. */
    fun parsePrice(text: String): PriceInput {
        if (text.isBlank()) return PriceInput.Empty
        val t = text.trim().trimStart { !it.isDigit() && it != '.' && it != ',' }.replace(" ", "").replace("\u00A0", "")
        if (t.none { it.isDigit() }) return PriceInput.Invalid
        val match = Regex("""^(\d{0,9})(?:[.,](\d{0,2}))?$""").matchEntire(t) ?: return PriceInput.Invalid
        val whole = match.groupValues[1].ifEmpty { "0" }.toLong()
        val frac = match.groupValues[2].padEnd(2, '0').toLong()
        return PriceInput.Valid(whole * 100 + frac)
    }

    /** 250 → "2.50" for editing (no currency symbol). */
    fun formatPriceInput(cents: Long): String =
        String.format(Locale.ROOT, "%d.%02d", cents / 100, kotlin.math.abs(cents % 100))

    /**
     * Moves an unchecked item one step up ([delta] = -1) or down (+1) among the unchecked items of its aisle. Returns
     * the whole list in its new order (ready for repository reorder), or null when there is no neighbour.
     */
    fun moveWithinAisle(items: List<ShoppingItemEntity>, itemId: Long, delta: Int): List<ShoppingItemEntity>? {
        val ordered = items.sortedWith(byPosition).toMutableList()
        val item = ordered.firstOrNull { it.id == itemId } ?: return null
        val aisle = normalizedCategory(item.category)
        val peers = ordered.filter { it.checked == item.checked && normalizedCategory(it.category) == aisle }
        val peerIndex = peers.indexOfFirst { it.id == itemId }
        val neighbour = peers.getOrNull(peerIndex + delta) ?: return null
        val a = ordered.indexOfFirst { it.id == item.id }
        val b = ordered.indexOfFirst { it.id == neighbour.id }
        ordered[a] = neighbour
        ordered[b] = item
        return ordered
    }

    fun canMove(items: List<ShoppingItemEntity>, itemId: Long, delta: Int): Boolean = moveWithinAisle(items, itemId, delta) != null

    /**
     * Plain-text version of a list for the share sheet: the items still to buy grouped by aisle. If everything is
     * already ticked the whole list is shared instead.
     */
    fun shareText(listName: String, items: List<ShoppingItemEntity>, categoryLabel: (String) -> String): String {
        val toBuy = items.filter { !it.checked }
        val source = toBuy.ifEmpty { items }.map { it.copy(checked = false) }
        val grouped = group(source)
        return buildString {
            append(listName.trim())
            grouped.sections.forEach { section ->
                append("\n\n").append(categoryLabel(section.category))
                section.items.forEach { item ->
                    append("\n• ")
                    val qty = quantityLabel(item.quantity, item.unit)
                    if (qty.isNotEmpty()) append(qty).append(' ')
                    append(item.name.trim())
                    if (item.note.isNotBlank()) append(" (").append(item.note.trim()).append(')')
                }
            }
        }
    }
}
