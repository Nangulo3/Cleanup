package com.example.cleanup.domain.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeResumeTest {

    // Orden de la lista: más reciente primero, "sin fecha" (0) al final.
    private val months = listOf(
        MonthPending(202610, pending = 0),
        MonthPending(202609, pending = 5),
        MonthPending(202608, pending = 0),
        MonthPending(202607, pending = 3),
        MonthPending(0, pending = 1),
    )

    @Test
    fun `reanuda en el mes de la ultima decision si le quedan pendientes`() {
        assertEquals(202607, SwipeResume.resumeMonth(months, lastDecisionMonth = 202607))
    }

    @Test
    fun `si el ultimo mes ya esta completo reanuda en el mas reciente con pendientes`() {
        assertEquals(202609, SwipeResume.resumeMonth(months, lastDecisionMonth = 202610))
        assertEquals(202609, SwipeResume.resumeMonth(months, lastDecisionMonth = null))
    }

    @Test
    fun `sin pendientes no hay donde reanudar`() {
        assertNull(SwipeResume.resumeMonth(months.map { it.copy(pending = 0) }, 202609))
    }

    @Test
    fun `siguiente mes es el siguiente mas antiguo con pendientes`() {
        assertEquals(202607, SwipeResume.nextMonthWithPending(months, current = 202609))
        assertEquals(0, SwipeResume.nextMonthWithPending(months, current = 202607))
    }

    @Test
    fun `al llegar al final vuelve al primero con pendientes`() {
        assertEquals(202609, SwipeResume.nextMonthWithPending(months, current = 0))
    }

    @Test
    fun `nunca propone el mismo mes`() {
        val single = listOf(MonthPending(202609, pending = 2))
        assertNull(SwipeResume.nextMonthWithPending(single, current = 202609))
    }
}
