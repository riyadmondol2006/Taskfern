package com.riyadmondol2006.taskfern.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroceryCategoriesTest {

    private fun guess(name: String) = GroceryCategories.guess(name)

    @Test fun commonItems() {
        assertEquals(GroceryCategories.DAIRY, guess("Milk"))
        assertEquals(GroceryCategories.DAIRY, guess("Eggs"))
        assertEquals(GroceryCategories.PRODUCE, guess("Bananas"))
        assertEquals(GroceryCategories.BAKERY, guess("Bread"))
        assertEquals(GroceryCategories.MEAT, guess("Chicken breast"))
        assertEquals(GroceryCategories.PANTRY, guess("Rice"))
        assertEquals(GroceryCategories.DRINKS, guess("Sparkling water"))
        assertEquals(GroceryCategories.SNACKS, guess("Dark chocolate"))
        assertEquals(GroceryCategories.HOUSEHOLD, guess("Dish soap"))
        assertEquals(GroceryCategories.PERSONAL, guess("Toothpaste"))
        assertEquals(GroceryCategories.BABY_PET, guess("Cat litter"))
    }

    @Test fun isCaseInsensitive() = assertEquals(GroceryCategories.DAIRY, guess("  CHEESE "))

    @Test fun longerKeywordsWin() {
        assertEquals(GroceryCategories.PANTRY, guess("Peanut butter"))
        assertEquals(GroceryCategories.FROZEN, guess("Ice cream"))
        assertEquals(GroceryCategories.FROZEN, guess("Frozen peas"))
        assertEquals(GroceryCategories.HOUSEHOLD, guess("AA batteries"))
    }

    @Test fun unknownGoesToOther() {
        assertEquals(GroceryCategories.OTHER, guess("Birthday card"))
        assertEquals(GroceryCategories.OTHER, guess(""))
    }

    @Test fun orderedListsEveryAisleOnceWithOtherLast() {
        val ordered = GroceryCategories.ordered
        assertEquals(ordered.size, ordered.toSet().size)
        assertEquals(GroceryCategories.OTHER, ordered.last())
        assertTrue(GroceryCategories.PRODUCE in ordered)
    }
}
