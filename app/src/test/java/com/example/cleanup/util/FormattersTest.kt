package com.example.cleanup.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormattersTest {

    private val us = Locale.US
    private val esCo = Locale.forLanguageTag("es-CO")

    @Test
    fun `bytes por debajo de 1000 se muestran sin convertir`() {
        assertEquals("0 B", Formatters.bytes(0, us))
        assertEquals("999 B", Formatters.bytes(999, us))
    }

    @Test
    fun `usa unidades decimales con un decimal por debajo de 10`() {
        assertEquals("1.0 kB", Formatters.bytes(1_000, us))
        assertEquals("1.5 kB", Formatters.bytes(1_536, us))
        assertEquals("1.5 GB", Formatters.bytes(1_500_000_000, us))
    }

    @Test
    fun `sin decimales desde 10`() {
        assertEquals("12 MB", Formatters.bytes(12_345_678, us))
        assertEquals("250 GB", Formatters.bytes(250_000_000_000, us))
    }

    @Test
    fun `no muestra 1000 kB al redondear`() {
        assertEquals("1.0 MB", Formatters.bytes(999_999, us))
    }

    @Test
    fun `respeta el separador decimal del idioma`() {
        assertEquals("1,5 GB", Formatters.bytes(1_500_000_000, esCo))
    }

    @Test
    fun `cantidades con separador de miles`() {
        assertEquals("12,345", Formatters.count(12_345, us))
        assertEquals("12.345", Formatters.count(12_345, esCo))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bytes negativos no son validos`() {
        Formatters.bytes(-1, us)
    }

    @Test
    fun `duraciones cortas en minutos y segundos`() {
        assertEquals("0:00", Formatters.duration(0))
        assertEquals("0:07", Formatters.duration(7_000))
        assertEquals("0:08", Formatters.duration(7_600)) // redondea al segundo más cercano
        assertEquals("12:34", Formatters.duration(754_000))
    }

    @Test
    fun `duraciones largas incluyen horas`() {
        assertEquals("1:02:03", Formatters.duration(3_723_000))
    }

    @Test
    fun `nombre de mes con mayuscula inicial`() {
        assertEquals("Octubre 2026", Formatters.month(202610, Locale.forLanguageTag("es")))
        assertEquals("January 2025", Formatters.month(202501, Locale.US))
    }

    @Test
    fun `mes sin fecha devuelve null`() {
        assertEquals(null, Formatters.month(0, us))
    }
}
