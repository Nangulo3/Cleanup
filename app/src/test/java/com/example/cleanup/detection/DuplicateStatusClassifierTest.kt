package com.example.cleanup.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateStatusClassifierTest {

    private fun d(
        size: Long? = 1_000,
        sameSize: Int = 1,
        valid: Boolean = true,
        quick: String? = "q",
        sha: String? = null,
        error: String? = null,
        sameQuick: Int = 0,
        identical: Int = 0,
    ) = DuplicateStatusData(size, sameSize, valid, quick, sha, error, sameQuick, identical)

    @Test
    fun `tamano unico no puede tener copias`() {
        assertEquals(DuplicateStatus.UNIQUE_SIZE, DuplicateStatusClassifier.classify(d(sameSize = 0, valid = false)))
    }

    @Test
    fun `sin tamano conocido no se compara`() {
        assertEquals(DuplicateStatus.UNKNOWN_SIZE, DuplicateStatusClassifier.classify(d(size = null)))
    }

    @Test
    fun `huella vencida o ausente queda pendiente`() {
        assertEquals(DuplicateStatus.PENDING, DuplicateStatusClassifier.classify(d(valid = false)))
        assertEquals(DuplicateStatus.PENDING, DuplicateStatusClassifier.classify(d(quick = null)))
    }

    @Test
    fun `huella rapida compartida sin hash completo queda pendiente`() {
        assertEquals(DuplicateStatus.PENDING, DuplicateStatusClassifier.classify(d(sameQuick = 1)))
    }

    @Test
    fun `huella rapida distinta es contenido distinto`() {
        assertEquals(DuplicateStatus.DIFFERENT_CONTENT, DuplicateStatusClassifier.classify(d()))
        assertEquals(DuplicateStatus.DIFFERENT_CONTENT, DuplicateStatusClassifier.classify(d(sha = "s", sameQuick = 1)))
    }

    @Test
    fun `mismo sha256 es duplicado`() {
        assertEquals(DuplicateStatus.DUPLICATE, DuplicateStatusClassifier.classify(d(sha = "s", sameQuick = 1, identical = 2)))
    }

    @Test
    fun `error de lectura`() {
        assertEquals(DuplicateStatus.READ_ERROR, DuplicateStatusClassifier.classify(d(quick = null, error = "READ_FAILED")))
    }
}
