package com.example.cleanup.detection

import com.example.cleanup.data.db.dao.SimilarityInput
import com.example.cleanup.settings.SimilarityLevel
import com.example.cleanup.worker.SimilarGrouper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarClusteringTest {

    private val t = SimilarityThresholds.forLevel(SimilarityLevel.NORMAL) // pHash ≤ 10, dHash ≤ 12
    private val hour = 3_600_000L

    private fun bits(range: IntRange): Long = range.fold(0L) { acc, i -> acc or (1L shl i) }

    private fun p(id: Long, phash: Long, time: Long = 0, low: Boolean = false, d256: LongArray? = null) =
        PhotoPoint(id, time, phash, dhash = 0L, d256 = d256, lowInformation = low)

    @Test
    fun `no encadena A parecida a B y B a C si A y C no se parecen`() {
        val a = p(1, 0L)
        val b = p(2, bits(0..5))  // A–B: 6
        val c = p(3, bits(0..10)) // B–C: 5, A–C: 11 (> 10)
        val clusters = SimilarClustering.clusterPhotos(listOf(a, b, c), t)
        assertEquals(1, clusters.size)
        // B–C es el par más parecido; A no entra porque no se parece a C.
        assertEquals(listOf(2L, 3L), clusters.single().ids)
    }

    @Test
    fun `fotos parecidas tomadas muy lejos en el tiempo solo se agrupan si son casi identicas`() {
        val a = p(1, 0L, time = 0)
        val far = p(2, bits(0..4), time = 48 * hour)       // distancia 5: no pasa la pasada global (≤ 3)
        val farTwin = p(3, bits(10..11), time = 96 * hour) // distancia 2: sí pasa
        val clusters = SimilarClustering.clusterPhotos(listOf(a, far, farTwin), t)
        assertEquals(listOf(listOf(1L, 3L)), clusters.map { it.ids })
    }

    @Test
    fun `las imagenes casi uniformes no se agrupan`() {
        val clusters = SimilarClustering.clusterPhotos(listOf(p(1, 0L, low = true), p(2, 0L, low = true)), t)
        assertTrue(clusters.isEmpty())
    }

    @Test
    fun `capturas usan el hash de 256 bits`() {
        val base = longArrayOf(0, 0, 0, 0)
        val close = longArrayOf(bits(0..9), 0, 0, 0)            // 10 bits de 256
        val other = longArrayOf(-1L, 0, 0, 0)                     // 64 bits de 256
        val clusters = SimilarClustering.clusterScreenshots(
            listOf(p(1, 0L, d256 = base), p(2, 0L, d256 = close), p(3, 0L, d256 = other)), t
        )
        assertEquals(listOf(listOf(1L, 2L)), clusters.map { it.ids })
    }

    private fun input(id: Long, phash: Long, sharp: Float, sha: String? = null) = SimilarityInput(
        id = id, sortDateMs = 0, isScreenshot = false, width = 100, height = 100, phash = phash, dhash = 0L,
        d256a = 0, d256b = 0, d256c = 0, d256d = 0, lowInformation = false, sharpness = sharp, sha256 = sha,
    )

    @Test
    fun `los duplicados exactos cuentan como una sola foto y se sugiere la mas nitida`() {
        val groups = SimilarGrouper.buildGroups(
            listOf(
                input(1, 0L, sharp = 10f, sha = "x"),
                input(2, 0L, sharp = 10f, sha = "x"), // copia exacta de 1
                input(3, bits(0..3), sharp = 50f),
            ),
            SimilarityLevel.NORMAL,
            now = 0,
        )
        val group = groups.single()
        assertEquals(listOf(1L, 3L), group.memberIds)
        assertEquals(3L, group.group.suggestedId)
    }
}
