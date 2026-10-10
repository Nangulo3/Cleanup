package com.example.cleanup.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import kotlin.random.Random

class HashAlgorithmsTest {

    private fun bytes(size: Int, seed: Int = 1) = Random(seed).nextBytes(size)

    private fun quick(data: ByteArray) = HashAlgorithms.quickHash(data.size.toLong()) { position, length ->
        val start = position.toInt().coerceAtMost(data.size)
        data.copyOfRange(start, (start + length).coerceAtMost(data.size))
    }

    @Test
    fun `sha256 de un valor conocido`() {
        val hash = HashAlgorithms.sha256(ByteArrayInputStream("abc".toByteArray()))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash)
    }

    @Test
    fun `leer por bloques pequenos da el mismo resultado`() {
        val data = bytes(300_000)
        val big = HashAlgorithms.sha256(ByteArrayInputStream(data))
        var chunks = 0
        val small = HashAlgorithms.sha256(ByteArrayInputStream(data), bufferSize = 1024) { chunks++ }
        assertEquals(big, small)
        assertEquals(true, chunks >= 293) // se procesó por bloques, no de una vez
    }

    @Test
    fun `mismo contenido con distinto nombre da la misma huella`() {
        val a = bytes(200_000)
        val b = a.copyOf()
        assertEquals(quick(a), quick(b))
        assertEquals(HashAlgorithms.sha256(ByteArrayInputStream(a)), HashAlgorithms.sha256(ByteArrayInputStream(b)))
    }

    @Test
    fun `un cambio al final cambia la huella rapida`() {
        val a = bytes(200_000)
        val c = a.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        assertNotEquals(quick(a), quick(c))
    }

    @Test
    fun `un cambio en el medio solo lo detecta el hash completo`() {
        val a = bytes(200_000)
        val d = a.copyOf().also { it[100_000] = (it[100_000] + 1).toByte() }
        assertEquals(quick(a), quick(d)) // la huella rápida no lee el medio
        assertNotEquals(
            HashAlgorithms.sha256(ByteArrayInputStream(a)),
            HashAlgorithms.sha256(ByteArrayInputStream(d)),
        )
    }

    @Test
    fun `archivos pequenos y de distinto tamano`() {
        val small = bytes(1_000)
        assertEquals(quick(small), quick(small.copyOf()))
        assertNotEquals(quick(small), quick(small.copyOf(999)))
    }
}
