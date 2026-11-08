package com.cafeteria.gestion.data

data class StockBalance(val units: Long, val subunits: Long?)

/** Pure inventory math shared by sale recording and tests. */
object StockCalculator {
    private const val MAX_STOCK_UNITS = 9_000_000_000_000L
    private const val MAX_SAFE_SUBUNIT_TOTAL = 9_007_199_254_740_991L
    fun afterAdjustment(
        stockUnits: Long,
        stockSubunits: Long?,
        subunitsPerUnit: Int?,
        unitsDelta: Long,
        subunitsDelta: Long?,
        allowNoop: Boolean = false
    ): StockBalance {
        require(stockUnits >= 0L && (stockSubunits == null || stockSubunits >= 0L)) {
            "Las existencias actuales no son válidas."
        }
        require(allowNoop || unitsDelta != 0L || (subunitsDelta ?: 0L) != 0L) { "El ajuste debe modificar alguna cantidad." }
        val ratio = subunitsPerUnit?.takeIf { it > 0 }
        if (ratio != null) {
            require(stockSubunits != null && stockSubunits < ratio.toLong()) {
                "La configuración de subunidades necesita revisión."
            }
            val current = Math.addExact(Math.multiplyExact(stockUnits, ratio.toLong()), stockSubunits)
            val delta = Math.addExact(Math.multiplyExact(unitsDelta, ratio.toLong()), subunitsDelta ?: 0L)
            require(current <= MAX_SAFE_SUBUNIT_TOTAL) { "La equivalencia actual supera el límite seguro de cálculo." }
            val next = Math.addExact(current, delta)
            require(next >= 0L) { "El ajuste no puede dejar existencias negativas." }
            require(next <= MAX_SAFE_SUBUNIT_TOTAL && next / ratio <= MAX_STOCK_UNITS) {
                "El ajuste supera el límite seguro de existencias."
            }
            return StockBalance(next / ratio, next % ratio)
        }
        require((subunitsDelta ?: 0L) == 0L) {
            "No se pueden ajustar subunidades si no se conoce su equivalencia."
        }
        require(stockSubunits == null) { "El inventario contiene subunidades sin equivalencia conocida." }
        val nextUnits = Math.addExact(stockUnits, unitsDelta)
        require(nextUnits >= 0L) { "El ajuste no puede dejar existencias negativas." }
        require(nextUnits <= MAX_STOCK_UNITS) { "El ajuste supera el límite seguro de existencias." }
        return StockBalance(nextUnits, null)
    }

    fun afterSale(
        stockUnits: Long,
        stockSubunits: Long?,
        subunitsPerUnit: Int?,
        soldUnits: Long,
        soldSubunits: Long,
        allowUntrackedSubunitSales: Boolean = false
    ): StockBalance {
        require(stockUnits >= 0 && (stockSubunits == null || stockSubunits >= 0)) { "Las existencias actuales no son válidas." }
        require(soldUnits >= 0 && soldSubunits >= 0 && (soldUnits > 0 || soldSubunits > 0)) { "Indica una cantidad mayor que cero." }
        val equivalence = subunitsPerUnit?.takeIf { it > 0 }
        if (equivalence == null) {
            require(stockSubunits == null) { "El inventario contiene subunidades sin equivalencia conocida." }
            if (allowUntrackedSubunitSales && stockUnits == 0L && soldUnits == 0L && soldSubunits > 0L) {
                // Products such as coffee or hot chocolate sold by cups do not have a meaningful
                // container-to-cup conversion. Record their revenue without fictitious stock math.
                return StockBalance(stockUnits, stockSubunits)
            }
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
        require(available <= MAX_SAFE_SUBUNIT_TOTAL && requested <= MAX_SAFE_SUBUNIT_TOTAL) {
            "La equivalencia supera el límite seguro de cálculo del inventario."
        }
        require(requested <= available) {
            "No hay existencias suficientes. Disponibles: ${available / equivalence} unidades y ${available % equivalence} subunidades."
        }
        val remaining = available - requested
        return StockBalance(remaining / equivalence, remaining % equivalence)
    }
}
