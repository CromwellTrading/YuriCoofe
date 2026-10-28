package com.cafeteria.gestion.data

data class StockBalance(val units: Long, val subunits: Long?)

/** Pure inventory math shared by sale recording and tests. */
object StockCalculator {
    fun afterSale(
        stockUnits: Long,
        stockSubunits: Long?,
        subunitsPerUnit: Int?,
        soldUnits: Long,
        soldSubunits: Long
    ): StockBalance {
        require(stockUnits >= 0 && (stockSubunits == null || stockSubunits >= 0)) { "Las existencias actuales no son válidas." }
        require(soldUnits >= 0 && soldSubunits >= 0 && (soldUnits > 0 || soldSubunits > 0)) { "Indica una cantidad mayor que cero." }
        val equivalence = subunitsPerUnit?.takeIf { it > 0 }
        if (equivalence == null) {
            require(soldUnits <= stockUnits) { "No hay suficientes unidades completas. Disponibles: $stockUnits." }
            // When a product is sold in unknown portions (for example, scoops from a tub), keep
            // container stock honest: at least one container must remain available for the portions
            // sold in this transaction. Do not invent a conversion or decrement a full container.
            require(soldSubunits == 0L || stockUnits > soldUnits) {
                "No hay recipientes disponibles para registrar esas subunidades. Repón existencias o revisa el producto."
            }
            return StockBalance(stockUnits - soldUnits, stockSubunits)
        }
        val available = Math.addExact(Math.multiplyExact(stockUnits, equivalence.toLong()), stockSubunits ?: 0L)
        val requested = Math.addExact(Math.multiplyExact(soldUnits, equivalence.toLong()), soldSubunits)
        require(requested <= available) {
            "No hay existencias suficientes. Disponibles: ${available / equivalence} unidades y ${available % equivalence} subunidades."
        }
        val remaining = available - requested
        return StockBalance(remaining / equivalence, remaining % equivalence)
    }
}
