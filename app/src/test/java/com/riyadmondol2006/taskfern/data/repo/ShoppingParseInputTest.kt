package com.riyadmondol2006.taskfern.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingParseInputTest {

    private fun parse(text: String) = ShoppingRepository.parseInput(text)

    @Test fun plainNameStaysWhole() = assertEquals(ItemInput("Milk"), parse("Milk"))

    @Test fun trimsAndCollapsesSpaces() = assertEquals(ItemInput("Olive oil"), parse("  Olive   oil  "))

    @Test fun leadingQuantityAndUnit() = assertEquals(ItemInput("rice", "2", "kg"), parse("2 kg rice"))

    @Test fun unitIsLowercased() = assertEquals(ItemInput("rice", "2", "kg"), parse("2 KG rice"))

    @Test fun leadingQuantityWithoutUnitKeepsAdjective() = assertEquals(ItemInput("red apples", "2"), parse("2 red apples"))

    @Test fun leadingMultiplier() = assertEquals(ItemInput("eggs", "3"), parse("3x eggs"))

    @Test fun trailingMultiplier() = assertEquals(ItemInput("rice", "3"), parse("rice x3"))

    @Test fun trailingTimesSign() = assertEquals(ItemInput("eggs", "6"), parse("eggs ×6"))

    @Test fun trailingQuantityAndUnit() = assertEquals(ItemInput("rice", "2", "kg"), parse("rice 2kg"))

    @Test fun trailingDecimalWithUnit() = assertEquals(ItemInput("Coke", "1.5", "l"), parse("Coke 1.5 l"))

    @Test fun decimalComma() = assertEquals(ItemInput("flour", "1,5", "kg"), parse("1,5 kg flour"))

    @Test fun fractionSymbol() = assertEquals(ItemInput("cabbage", "½"), parse("½ cabbage"))

    @Test fun trailingUnknownWordIsPartOfName() = assertEquals(ItemInput("Bread 2 slices"), parse("Bread 2 slices"))

    @Test fun numberInsideAWordIsNotAQuantity() {
        assertEquals(ItemInput("7up"), parse("7up"))
        assertEquals(ItemInput("Vitamin B12"), parse("Vitamin B12"))
    }

    @Test fun blankInputGivesBlankName() = assertEquals("", parse("   ").name)
}
