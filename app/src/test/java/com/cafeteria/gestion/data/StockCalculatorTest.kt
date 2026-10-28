package com.cafeteria.gestion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StockCalculatorTest {
    @Test
    fun sellingBoxesAndLooseCigarettesNormalizesRemainingStock() {
        val remaining = StockCalculator.afterSale(
            stockUnits = 10,
            stockSubunits = 0,
            subunitsPerUnit = 20,
            soldUnits = 5,
            soldSubunits = 30
        )
        assertEquals(3L, remaining.units)
        assertEquals(10L, remaining.subunits)
    }

    @Test
    fun twentyOneLooseItemsConsumesOneUnitAndOneLooseItem() {
        val remaining = StockCalculator.afterSale(
            stockUnits = 10,
            stockSubunits = 0,
            subunitsPerUnit = 20,
            soldUnits = 0,
            soldSubunits = 21
        )
        assertEquals(8L, remaining.units)
        assertEquals(19L, remaining.subunits)
    }

    @Test
    fun knownEquivalencePreventsOverselling() {
        assertThrows(IllegalArgumentException::class.java) {
            StockCalculator.afterSale(1, 0, 20, 1, 1)
        }
    }

    @Test
    fun unknownEquivalenceRequiresAtLeastOneContainerForPortions() {
        assertThrows(IllegalArgumentException::class.java) {
            StockCalculator.afterSale(
                stockUnits = 0,
                stockSubunits = null,
                subunitsPerUnit = null,
                soldUnits = 0,
                soldSubunits = 1
            )
        }
    }

    @Test
    fun unknownEquivalenceDoesNotGuessContainerYield() {
        val remaining = StockCalculator.afterSale(
            stockUnits = 3,
            stockSubunits = null,
            subunitsPerUnit = null,
            soldUnits = 0,
            soldSubunits = 8
        )
        assertEquals(3L, remaining.units)
        assertEquals(null, remaining.subunits)
    }
}
