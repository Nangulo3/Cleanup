package com.example.cleanup.detection

import com.example.cleanup.data.db.dao.VideoInput
import com.example.cleanup.settings.SimilarityLevel
import com.example.cleanup.worker.SimilarGrouper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class VideoSignaturesTest {

    private val normal = VideoThresholds.forLevel(SimilarityLevel.NORMAL) // pHash ≤ 10, dHash ≤ 12, 75 %
    private val broad = VideoThresholds.forLevel(SimilarityLevel.BROAD)   // pHash ≤ 14, dHash ≤ 16, 60 %

    /** Fotogramas "base": valores pseudoaleatorios fijos, distintos entre sí. */
    private val baseP = randomFrames(seed = 1)
    private val baseD = randomFrames(seed = 2)

    private fun randomFrames(seed: Long): LongArray {
        val r = Random(seed)
        return LongArray(VideoSignatures.FRAME_COUNT) { r.nextLong() }
    }

    private fun bits(range: IntRange): Long = range.fold(0L) { acc, i -> acc or (1L shl i) }

    /** Cambia los bits indicados en todos los fotogramas (distancia de Hamming = tamaño del rango). */
    private fun LongArray.flip(range: IntRange): LongArray = LongArray(size) { this[it] xor bits(range) }

    /** Invierte por completo los fotogramas indicados (distancia 64: fotogramas totalmente distintos). */
    private fun LongArray.invert(frames: IntRange): LongArray = LongArray(size) { if (it in frames) this[it].inv() else this[it] }

    private fun v(
        id: Long,
        durationMs: Long = 60_000,
        aspect: Float? = 16f / 9f,
        p: LongArray = baseP,
        d: LongArray = baseD,
        lowMask: Int = 0,
    ) = VideoPoint(id, durationMs, aspect, p, d, lowMask)

    @Test
    fun `los hashes se guardan y recuperan sin perder bits`() {
        val values = longArrayOf(0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 0x0123456789ABCDEFL, -0x0123456789ABCDEFL)
        val bytes = VideoSignatures.toBytes(values)
        assertEquals(values.size * 8, bytes.size)
        assertArrayEquals(values, VideoSignatures.fromBytes(bytes))
    }

    @Test
    fun `las posiciones de fotogramas van del 10 al 90 por ciento`() {
        val positions = VideoSignatures.FRAME_POSITIONS
        assertEquals(VideoSignatures.FRAME_COUNT, positions.size)
        assertEquals(0.1, positions.first(), 1e-9)
        assertEquals(0.9, positions.last(), 1e-9)
    }

    @Test
    fun `secuencias identicas o con cambios pequenos son similares`() {
        assertTrue(VideoSignatures.similar(v(1), v(2), normal))
        val recompressed = v(2, p = baseP.flip(0..3), d = baseD.flip(10..14))
        assertTrue(VideoSignatures.similar(v(1), recompressed, normal))
        assertEquals(4, VideoSignatures.distance(v(1), recompressed))
    }

    @Test
    fun `duracion muy distinta no es similar aunque los fotogramas coincidan`() {
        // Tolerancia en Normal: el mayor entre 1 s y el 3 % (2,1 s para 70 s).
        assertFalse(VideoSignatures.similar(v(1, durationMs = 60_000), v(2, durationMs = 70_000), normal))
        assertTrue(VideoSignatures.similar(v(1, durationMs = 60_000), v(2, durationMs = 61_500), normal))
        // Videos cortos: 1 s de margen como mínimo.
        assertTrue(VideoSignatures.similar(v(1, durationMs = 5_000), v(2, durationMs = 5_900), normal))
        assertFalse(VideoSignatures.similar(v(1, durationMs = 5_000), v(2, durationMs = 6_500), normal))
    }

    @Test
    fun `proporcion distinta no es similar y una proporcion desconocida no bloquea`() {
        assertFalse(VideoSignatures.similar(v(1, aspect = 16f / 9f), v(2, aspect = 9f / 16f), normal))
        assertTrue(VideoSignatures.similar(v(1, aspect = null), v(2, aspect = 9f / 16f), normal))
    }

    @Test
    fun `pocos fotogramas coincidentes no basta en normal pero si en amplio`() {
        // 5 de 8 coinciden: Normal pide ceil(0,75 × 8) = 6; Amplio pide ceil(0,6 × 8) = 5.
        val other = v(2, p = baseP.invert(0..2), d = baseD.invert(0..2))
        assertFalse(VideoSignatures.similar(v(1), other, normal))
        assertTrue(VideoSignatures.similar(v(1), other, broad))
    }

    @Test
    fun `los fotogramas casi uniformes se ignoran`() {
        // Los fotogramas 0–4 son fundidos (marcados) y además distintos: solo cuentan 5, 6 y 7.
        val faded = v(2, p = baseP.invert(0..4), d = baseD.invert(0..4), lowMask = 0b11111)
        assertTrue(VideoSignatures.similar(v(1), faded, normal))
    }

    @Test
    fun `con menos de tres fotogramas utiles no se agrupa aunque sean identicos`() {
        val mostlyBlack = v(2, lowMask = 0b111111) // solo quedan los fotogramas 6 y 7
        assertFalse(VideoSignatures.similar(v(1), mostlyBlack, normal))
        assertFalse(VideoSignatures.similar(v(1), mostlyBlack, broad))
    }

    @Test
    fun `el agrupado no encadena A parecido a B y B a C si A y C no se parecen`() {
        val a = v(1)
        val b = v(2, p = baseP.flip(0..5))  // A–B: 6
        val c = v(3, p = baseP.flip(0..10)) // B–C: 5, A–C: 11 (> 10)
        val clusters = VideoSignatures.cluster(listOf(a, b, c), normal)
        assertEquals(listOf(listOf(2L, 3L)), clusters.map { it.ids })
        assertEquals(5, clusters.single().maxDistance)
    }

    @Test
    fun `agrupa por separado videos distintos y deja fuera los de otra duracion`() {
        val otherP = randomFrames(seed = 3)
        val otherD = randomFrames(seed = 4)
        val points = listOf(
            v(1, durationMs = 30_000),
            v(2, durationMs = 30_400, p = baseP.flip(0..2)),
            v(3, durationMs = 120_000, p = otherP, d = otherD),
            v(4, durationMs = 119_000, p = otherP.flip(5..8), d = otherD),
            v(5, durationMs = 90_000), // mismos fotogramas que 1, pero duración incompatible
        )
        val clusters = VideoSignatures.cluster(points, normal).map { it.ids }.sortedBy { it.first() }
        assertEquals(listOf(listOf(1L, 2L), listOf(3L, 4L)), clusters)
    }

    // --- Agrupador (SimilarGrouper) ---

    private fun input(
        id: Long,
        sha: String?,
        width: Int = 1280,
        height: Int = 720,
        orientation: Int = 0,
        p: LongArray = baseP,
        sizeBytes: Long = 10_000_000,
    ) = VideoInput(
        id = id,
        sortDateMs = 1_000L * id,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        orientation = orientation,
        durationMs = 60_000,
        framePhashes = VideoSignatures.toBytes(p),
        frameDhashes = VideoSignatures.toBytes(baseD),
        lowInfoMask = 0,
        sha256 = sha,
    )

    @Test
    fun `las copias exactas se colapsan y no forman un grupo de similares por si solas`() {
        val groups = SimilarGrouper.buildVideoGroups(
            listOf(input(1, sha = "x"), input(2, sha = "x")),
            SimilarityLevel.NORMAL,
            now = 0,
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `el grupo usa una sola copia exacta y sugiere el de mayor resolucion`() {
        val groups = SimilarGrouper.buildVideoGroups(
            listOf(
                input(1, sha = "x"),
                input(2, sha = "x"),
                input(3, sha = "y", width = 1920, height = 1080, p = baseP.flip(0..3)),
            ),
            SimilarityLevel.NORMAL,
            now = 0,
        )
        assertEquals(1, groups.size)
        assertEquals(listOf(1L, 3L), groups.single().memberIds)
        assertEquals(3L, groups.single().group.suggestedId)
    }

    @Test
    fun `la rotacion se tiene en cuenta al comparar la proporcion`() {
        // Mismo video vertical: uno guardado como 1080×1920 y otro como 1920×1080 rotado 90°.
        val groups = SimilarGrouper.buildVideoGroups(
            listOf(
                input(1, sha = "a", width = 1080, height = 1920, orientation = 0),
                input(2, sha = "b", width = 1920, height = 1080, orientation = 90),
                input(3, sha = "c", width = 1920, height = 1080, orientation = 0), // horizontal de verdad
            ),
            SimilarityLevel.NORMAL,
            now = 0,
        )
        assertEquals(listOf(listOf(1L, 2L)), groups.map { it.memberIds })
    }

    @Test
    fun `a igual resolucion se sugiere el de mayor tasa de datos`() {
        val suggested = SimilarGrouper.suggestVideo(
            listOf(
                input(1, sha = "a", sizeBytes = 8_000_000),
                input(2, sha = "b", sizeBytes = 20_000_000),
            )
        )
        assertEquals(2L, suggested)
    }
}
