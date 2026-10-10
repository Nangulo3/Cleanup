package com.example.cleanup.detection

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ImageSignaturesTest {

    private val size = 256

    /** "Escena" suave y aleatoria: una rejilla de 6×6 valores interpolada (parecida a una foto borrosa). */
    private fun scene(seed: Int): IntArray {
        val rnd = Random(seed)
        // 0–220 para que el cambio de brillo de la prueba no sature en blanco.
        val grid = Array(6) { FloatArray(6) { rnd.nextFloat() * 220f } }
        return IntArray(size * size) { i ->
            val x = (i % size) / (size - 1f) * 5f
            val y = (i / size) / (size - 1f) * 5f
            val x0 = x.toInt().coerceAtMost(4); val y0 = y.toInt().coerceAtMost(4)
            val fx = x - x0; val fy = y - y0
            val v = grid[y0][x0] * (1 - fx) * (1 - fy) + grid[y0][x0 + 1] * fx * (1 - fy) +
                grid[y0 + 1][x0] * (1 - fx) * fy + grid[y0 + 1][x0 + 1] * fx * fy
            gray(v.toInt())
        }
    }

    private fun gray(v: Int): Int {
        val c = v.coerceIn(0, 255)
        return (0xFF shl 24) or (c shl 16) or (c shl 8) or c
    }

    private fun IntArray.mapLuma(f: (Int) -> Int) = IntArray(size) { i -> gray(f(this[i] and 0xFF)) }

    private fun sig(pixels: IntArray) = ImageSignatures.compute(pixels, size, size)

    @Test
    fun `reduccion por promedio de areas`() {
        val src = floatArrayOf(
            0f, 2f, 10f, 10f,
            4f, 6f, 10f, 10f,
            1f, 1f, 3f, 5f,
            1f, 1f, 7f, 9f,
        )
        assertArrayEquals(floatArrayOf(3f, 10f, 1f, 6f), ImageSignatures.resizeArea(src, 4, 4, 2, 2), 0.0001f)
    }

    @Test
    fun `la misma imagen da hashes identicos`() {
        val a = sig(scene(1))
        val b = sig(scene(1))
        assertEquals(a.phash, b.phash)
        assertEquals(a.dhash, b.dhash)
    }

    @Test
    fun `ruido leve y cambio de brillo dan hashes cercanos`() {
        val base = scene(2)
        val noise = Random(99)
        val noisy = base.mapLuma { it + noise.nextInt(-4, 5) }
        val brighter = base.mapLuma { it + 15 }
        val s = sig(base)
        assertTrue(ImageSignatures.hamming(s.phash, sig(noisy).phash) <= 6)
        assertTrue(ImageSignatures.hamming(s.dhash, sig(noisy).dhash) <= 8)
        assertTrue(ImageSignatures.hamming(s.phash, sig(brighter).phash) <= 4)
    }

    @Test
    fun `escenas distintas dan hashes lejanos`() {
        val a = sig(scene(3))
        val b = sig(scene(4))
        assertTrue(ImageSignatures.hamming(a.phash, b.phash) >= 16)
    }

    @Test
    fun `imagen uniforme es de baja informacion`() {
        assertTrue(sig(IntArray(size * size) { gray(128) }).lowInformation)
        assertFalse(sig(scene(5)).lowInformation)
    }

    @Test
    fun `una imagen con bordes marcados es mas nitida que una suave`() {
        val checker = IntArray(size * size) { i -> gray(if (((i % size) / 8 + (i / size) / 8) % 2 == 0) 0 else 255) }
        assertTrue(sig(checker).sharpness > sig(scene(6)).sharpness)
    }

    @Test
    fun `distancia de hamming`() {
        assertEquals(0, ImageSignatures.hamming(5L, 5L))
        assertEquals(64, ImageSignatures.hamming(0L, -1L))
        assertEquals(3, ImageSignatures.hamming(longArrayOf(1, 0), longArrayOf(0, 3)))
    }
}
