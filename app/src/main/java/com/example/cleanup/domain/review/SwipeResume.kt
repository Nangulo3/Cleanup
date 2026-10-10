package com.example.cleanup.domain.review

/** Mes con su cantidad de elementos pendientes de revisar. */
data class MonthPending(val monthKey: Int, val pending: Int)

/** Reglas puras para "Reanudar" y "Siguiente mes" en Deslizar. */
object SwipeResume {

    /**
     * Mes en el que reanudar: el de la última decisión si aún le quedan pendientes; si no,
     * el mes más reciente con pendientes. [months] viene ordenado como en la lista
     * (más reciente primero, "sin fecha" al final). null si no queda nada por revisar.
     */
    fun resumeMonth(months: List<MonthPending>, lastDecisionMonth: Int?): Int? {
        val last = months.firstOrNull { it.monthKey == lastDecisionMonth }
        if (last != null && last.pending > 0) return last.monthKey
        return months.firstOrNull { it.pending > 0 }?.monthKey
    }

    /**
     * Siguiente mes con pendientes después de [current] en el orden de la lista
     * (es decir, hacia meses más antiguos); si no hay, el primero con pendientes desde arriba.
     */
    fun nextMonthWithPending(months: List<MonthPending>, current: Int): Int? {
        val index = months.indexOfFirst { it.monthKey == current }
        val after = if (index >= 0) months.drop(index + 1) else months
        return after.firstOrNull { it.pending > 0 }?.monthKey
            ?: months.firstOrNull { it.pending > 0 && it.monthKey != current }?.monthKey
    }
}
