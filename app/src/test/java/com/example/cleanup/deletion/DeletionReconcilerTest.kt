package com.example.cleanup.deletion

import com.example.cleanup.settings.DeletionMode
import org.junit.Assert.assertEquals
import org.junit.Test

class DeletionReconcilerTest {

    private val items = listOf(
        BatchItem(1, 1_000),
        BatchItem(2, 2_000),
        BatchItem(3, null),
    )

    @Test
    fun `todos eliminados`() {
        val outcome = DeletionReconciler.reconcile(items, presentIds = emptySet())
        assertEquals(listOf(1L, 2L, 3L), outcome.confirmedDeleted)
        assertEquals(emptyList<Long>(), outcome.stillPresent)
        assertEquals(3_000L, outcome.deletedKnownBytes)
        assertEquals(1, outcome.deletedUnknownSizeCount)
    }

    @Test
    fun `ninguno eliminado - cancelado o rechazado`() {
        val outcome = DeletionReconciler.reconcile(items, presentIds = setOf(1, 2, 3))
        assertEquals(emptyList<Long>(), outcome.confirmedDeleted)
        assertEquals(3, outcome.stillPresent.size)
        assertEquals(0L, outcome.deletedKnownBytes)
    }

    @Test
    fun `eliminacion parcial solo cuenta lo que desaparecio`() {
        val outcome = DeletionReconciler.reconcile(items, presentIds = setOf(2))
        assertEquals(listOf(1L, 3L), outcome.confirmedDeleted)
        assertEquals(listOf(2L), outcome.stillPresent)
        assertEquals(1_000L, outcome.deletedKnownBytes)
        assertEquals(1, outcome.deletedUnknownSizeCount)
    }

    private fun report(accepted: Boolean?, stillPresent: Int) = DeletionReport(
        mode = DeletionMode.PERMANENT, deleted = 1, deletedKnownBytes = 0, deletedUnknownSizeCount = 0,
        stillPresent = stillPresent, systemAccepted = accepted, skippedMissing = 0,
        skippedInaccessible = 0, remainingAfterLimit = 0,
    )

    @Test
    fun `con OK de Android lo que sigue presente es un fallo`() {
        val r = report(accepted = true, stillPresent = 2)
        assertEquals(2, r.failed)
        assertEquals(0, r.kept)
    }

    @Test
    fun `cancelado o sin respuesta lo que sigue presente se conserva`() {
        assertEquals(2, report(accepted = false, stillPresent = 2).kept)
        assertEquals(0, report(accepted = false, stillPresent = 2).failed)
        assertEquals(2, report(accepted = null, stillPresent = 2).kept)
    }
}
