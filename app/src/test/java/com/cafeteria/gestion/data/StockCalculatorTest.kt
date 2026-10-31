package com.cafeteria.gestion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StockCalculatorTest {
    @Test
    fun `mixed carton and cigarette sales normalize remaining stock`() {
        val remaining = StockCalculator.afterSale(10, 0, 20, 5, 30)
        assertEquals(3L, remaining.units)
        assertEquals(10L, remaining.subunits)
    }

    @Test
    fun `21 loose cigarettes consume one carton and one loose cigarette`() {
        val remaining = StockCalculator.afterSale(10, 0, 20, 0, 21)
        assertEquals(8L, remaining.units)
        assertEquals(19L, remaining.subunits)
    }

    @Test
    fun `untracked cups can be sold without inventing container stock`() {
        val remaining = StockCalculator.afterSale(0, null, null, 0, 12, allowUntrackedSubunitSales = true)
        assertEquals(0L, remaining.units)
        assertEquals(null, remaining.subunits)
    }

    @Test
    fun `known container count is not consumed when yield is unknown`() {
        val remaining = StockCalculator.afterSale(1, null, null, 0, 12, allowUntrackedSubunitSales = true)
        assertEquals(1L, remaining.units)
        assertEquals(null, remaining.subunits)
    }

    @Test
    fun `known stock cannot become negative`() {
        assertThrows(IllegalArgumentException::class.java) {
            StockCalculator.afterSale(1, 0, 20, 0, 21)
        }
    }
}
