package com.riyadmondol2006.taskfern.ui.shopping

import com.riyadmondol2006.taskfern.data.db.ShoppingItemEntity
import com.riyadmondol2006.taskfern.domain.GroceryCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingLogicTest {

    private fun item(
        id: Long,
        name: String,
        category: String = GroceryCategories.OTHER,
        position: Int = id.toInt(),
        checked: Boolean = false,
        checkedAt: Long? = null,
        quantity: String = "",
        unit: String = "",
        price: Long? = null,
        note: String = "",
    ) = ShoppingItemEntity(
        id = id, listId = 1, name = name, quantity = quantity, unit = unit, category = category, checked = checked,
        position = position, priceCents = price, note = note, createdAt = 0, checkedAt = checkedAt,
    )

    // Grouping

    @Test fun groupsUncheckedByAisleInStoreOrder() {
        val items = listOf(
            item(1, "Soap", GroceryCategories.HOUSEHOLD),
            item(2, "Milk", GroceryCategories.DAIRY),
            item(3, "Apples", GroceryCategories.PRODUCE),
            item(4, "Bread", GroceryCategories.BAKERY),
        )
        val g = ShoppingLogic.group(items)
        assertEquals(
            listOf(GroceryCategories.PRODUCE, GroceryCategories.BAKERY, GroceryCategories.DAIRY, GroceryCategories.HOUSEHOLD),
            g.sections.map { it.category },
        )
        assertEquals(4, g.remaining)
        assertTrue(g.inCart.isEmpty())
    }

    @Test fun itemsInAnAisleFollowPosition() {
        val items = listOf(
            item(1, "Pears", GroceryCategories.PRODUCE, position = 5),
            item(2, "Apples", GroceryCategories.PRODUCE, position = 1),
            item(3, "Kiwi", GroceryCategories.PRODUCE, position = 3),
        )
        assertEquals(listOf("Apples", "Kiwi", "Pears"), ShoppingLogic.group(items).sections.single().items.map { it.name })
    }

    @Test fun blankAndUnknownCategoriesSortBeforeOther() {
        val items = listOf(
            item(1, "Thing", ""),
            item(2, "Gift", "Gifts"),
            item(3, "Apples", GroceryCategories.PRODUCE),
        )
        val g = ShoppingLogic.group(items)
        assertEquals(listOf(GroceryCategories.PRODUCE, "Gifts", GroceryCategories.OTHER), g.sections.map { it.category })
    }

    @Test fun checkedItemsGoToTheCartMostRecentFirst() {
        val items = listOf(
            item(1, "Milk", GroceryCategories.DAIRY, checked = true, checkedAt = 100),
            item(2, "Eggs", GroceryCategories.DAIRY, checked = true, checkedAt = 300),
            item(3, "Bread", GroceryCategories.BAKERY),
        )
        val g = ShoppingLogic.group(items)
        assertEquals(listOf("Eggs", "Milk"), g.inCart.map { it.name })
        assertEquals(listOf("Bread"), g.sections.flatMap { s -> s.items.map { it.name } })
        assertEquals(3, g.total)
    }

    // Quantities

    @Test fun parsesQuantities() {
        assertEquals(2.0, ShoppingLogic.parseQuantity("2")!!, 0.0)
        assertEquals(1.5, ShoppingLogic.parseQuantity("1,5")!!, 0.0)
        assertEquals(0.5, ShoppingLogic.parseQuantity("½")!!, 0.0)
        assertEquals(1.5, ShoppingLogic.parseQuantity("1½")!!, 0.0)
        assertEquals(0.25, ShoppingLogic.parseQuantity("1/4")!!, 0.0)
        assertNull(ShoppingLogic.parseQuantity(""))
        assertNull(ShoppingLogic.parseQuantity("a few"))
        assertNull(ShoppingLogic.parseQuantity("1/0"))
        assertNull(ShoppingLogic.parseQuantity("0"))
    }

    @Test fun formatsQuantities() {
        assertEquals("3", ShoppingLogic.formatQuantity(3.0))
        assertEquals("1.5", ShoppingLogic.formatQuantity(1.5))
        assertEquals("0.33", ShoppingLogic.formatQuantity(1.0 / 3))
    }

    @Test fun quantityLabels() {
        assertEquals("", ShoppingLogic.quantityLabel("", ""))
        assertEquals("×3", ShoppingLogic.quantityLabel("3", ""))
        assertEquals("2 kg", ShoppingLogic.quantityLabel("2", "kg"))
        assertEquals("pack", ShoppingLogic.quantityLabel("", "pack"))
    }

    @Test fun mergesQuantitiesOfTheSameItem() {
        assertEquals("2" to "", ShoppingLogic.mergeQuantity("", "", "", ""))
        assertEquals("5" to "", ShoppingLogic.mergeQuantity("2", "", "3", ""))
        assertEquals("3" to "kg", ShoppingLogic.mergeQuantity("1", "kg", "2", "KG"))
        assertEquals("3" to "kg", ShoppingLogic.mergeQuantity("2", "kg", "", ""))
        assertEquals("2.5" to "l", ShoppingLogic.mergeQuantity("1,5", "l", "1", "l"))
    }

    @Test fun refusesToMergeDifferentUnits() {
        assertNull(ShoppingLogic.mergeQuantity("1", "kg", "500", "g"))
        assertNull(ShoppingLogic.mergeQuantity("2", "", "1", "kg"))
        assertNull(ShoppingLogic.mergeQuantity("some", "", "1", ""))
    }

    // Prices

    @Test fun lineTotalMultipliesByNumericQuantity() {
        assertEquals(500L, ShoppingLogic.lineTotalCents(250, "2"))
        assertEquals(375L, ShoppingLogic.lineTotalCents(250, "1.5"))
        assertEquals(250L, ShoppingLogic.lineTotalCents(250, ""))
        assertEquals(250L, ShoppingLogic.lineTotalCents(250, "a few"))
        assertNull(ShoppingLogic.lineTotalCents(null, "2"))
    }

    @Test fun totalsSplitRemainingAndCart() {
        val items = listOf(
            item(1, "Rice", quantity = "2", price = 150),
            item(2, "Milk", price = 99, checked = true),
            item(3, "Bread"),
            item(4, "Eggs", checked = true),
        )
        val t = ShoppingLogic.totals(items)
        assertEquals(399L, t.totalCents)
        assertEquals(300L, t.remainingCents)
        assertEquals(99L, t.inCartCents)
        assertEquals(2, t.pricedItems)
        assertEquals(2, t.unpricedItems)
        assertTrue(t.hasAnyPrice)
        assertFalse(ShoppingLogic.totals(listOf(item(1, "Bread"))).hasAnyPrice)
    }

    @Test fun parsesPriceInput() {
        assertEquals(PriceInput.Empty, ShoppingLogic.parsePrice("  "))
        assertEquals(PriceInput.Valid(250), ShoppingLogic.parsePrice("2.5"))
        assertEquals(PriceInput.Valid(250), ShoppingLogic.parsePrice("2,50"))
        assertEquals(PriceInput.Valid(1200), ShoppingLogic.parsePrice("12"))
        assertEquals(PriceInput.Valid(1200), ShoppingLogic.parsePrice("12."))
        assertEquals(PriceInput.Valid(399), ShoppingLogic.parsePrice("$3.99"))
        assertEquals(PriceInput.Valid(5), ShoppingLogic.parsePrice(".05"))
        assertEquals(PriceInput.Invalid, ShoppingLogic.parsePrice("0.999"))
        assertEquals(PriceInput.Invalid, ShoppingLogic.parsePrice("abc"))
        assertEquals(PriceInput.Invalid, ShoppingLogic.parsePrice("1.2.3"))
    }

    @Test fun formatsPriceForEditing() {
        assertEquals("2.50", ShoppingLogic.formatPriceInput(250))
        assertEquals("0.05", ShoppingLogic.formatPriceInput(5))
        assertEquals("12.00", ShoppingLogic.formatPriceInput(1200))
    }

    // Reordering

    @Test fun movesWithinTheSameAisleOnly() {
        val items = listOf(
            item(1, "Apples", GroceryCategories.PRODUCE, position = 0),
            item(2, "Milk", GroceryCategories.DAIRY, position = 1),
            item(3, "Pears", GroceryCategories.PRODUCE, position = 2),
        )
        val moved = ShoppingLogic.moveWithinAisle(items, 3, -1)!!
        assertEquals(listOf(3L, 2L, 1L), moved.map { it.id })
        // The Pears are now first in Produce, so they cannot move further up.
        val reIndexed = moved.mapIndexed { i, it -> it.copy(position = i) }
        assertFalse(ShoppingLogic.canMove(reIndexed, 3, -1))
        assertTrue(ShoppingLogic.canMove(reIndexed, 3, 1))
    }

    @Test fun cannotMovePastTheEdgesOrIntoTheCart() {
        val items = listOf(
            item(1, "Apples", GroceryCategories.PRODUCE, position = 0),
            item(2, "Pears", GroceryCategories.PRODUCE, position = 1, checked = true),
        )
        assertNull(ShoppingLogic.moveWithinAisle(items, 1, -1))
        assertNull(ShoppingLogic.moveWithinAisle(items, 1, 1))
        assertNull(ShoppingLogic.moveWithinAisle(items, 99, 1))
    }

    // Sharing

    @Test fun sharesItemsStillToBuyGroupedByAisle() {
        val items = listOf(
            item(1, "Milk", GroceryCategories.DAIRY, quantity = "2", unit = "l"),
            item(2, "Apples", GroceryCategories.PRODUCE, quantity = "6", note = "Green"),
            item(3, "Bread", GroceryCategories.BAKERY, checked = true),
        )
        val text = ShoppingLogic.shareText("Groceries", items) { it.uppercase() }
        assertEquals("Groceries\n\nPRODUCE\n• ×6 Apples (Green)\n\nDAIRY & EGGS\n• 2 l Milk", text)
    }

    @Test fun sharesEverythingWhenAllIsTicked() {
        val items = listOf(item(1, "Bread", GroceryCategories.BAKERY, checked = true))
        assertEquals("Hardware\n\nBakery\n• Bread", ShoppingLogic.shareText("Hardware", items) { it })
    }
}
