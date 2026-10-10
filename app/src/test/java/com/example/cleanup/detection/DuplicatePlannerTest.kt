package com.example.cleanup.detection

import com.example.cleanup.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicatePlannerTest {

    private fun s(id: Long, size: Long, quick: String? = null, full: String? = null, error: Boolean = false, type: MediaType = MediaType.PHOTO) =
        HashState(id, type, size, quick, full, error)

    @Test
    fun `solo se calcula huella rapida en grupos del mismo tamano y tipo`() {
        val items = listOf(s(1, 100), s(2, 100), s(3, 200), s(4, 100, type = MediaType.VIDEO))
        assertEquals(listOf(1L, 2L), DuplicatePlanner.needQuickHash(items, retryErrors = false).map { it.id })
    }

    @Test
    fun `no recalcula huellas vigentes`() {
        val items = listOf(s(1, 100, quick = "q"), s(2, 100))
        assertEquals(listOf(2L), DuplicatePlanner.needQuickHash(items, false).map { it.id })
    }

    @Test
    fun `los errores no se reintentan salvo que se pida`() {
        val items = listOf(s(1, 100, error = true), s(2, 100))
        assertEquals(listOf(2L), DuplicatePlanner.needQuickHash(items, false).map { it.id })
        assertEquals(listOf(1L, 2L), DuplicatePlanner.needQuickHash(items, true).map { it.id })
    }

    @Test
    fun `hash completo solo para coincidencias de huella rapida`() {
        val items = listOf(
            s(1, 100, quick = "a"),
            s(2, 100, quick = "a"),
            s(3, 100, quick = "b"),
            s(4, 100, quick = "a", full = "x"),
        )
        assertEquals(listOf(1L, 2L), DuplicatePlanner.needFullHash(items, false).map { it.id })
    }
}
