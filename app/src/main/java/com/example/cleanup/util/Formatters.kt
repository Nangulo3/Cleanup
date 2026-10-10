package com.example.cleanup.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Funciones de formato puras (sin dependencias de Android) para poder probarlas en la JVM. */
object Formatters {

    private val UNITS = listOf("kB", "MB", "GB", "TB")

    /**
     * Formatea bytes con unidades decimales (1 kB = 1000 B), igual que el almacenamiento
     * de Android. Ej.: 1_500_000 → "1,5 MB" en español.
     */
    fun bytes(bytes: Long, locale: Locale = Locale.getDefault()): String {
        require(bytes >= 0) { "bytes no puede ser negativo" }
        if (bytes < 1000) return "$bytes B"
        var value = bytes.toDouble()
        var unit = -1
        // 999.5 en vez de 1000 para que 999_999 B se muestre "1,0 MB" y no "1000 kB".
        while (value >= 999.5 && unit < UNITS.lastIndex) {
            value /= 1000.0
            unit++
        }
        val pattern = if (value < 10) "%.1f %s" else "%.0f %s"
        return String.format(locale, pattern, value, UNITS[unit])
    }

    /** Cantidades con separador de miles según el idioma. Ej.: 12345 → "12.345" en español. */
    fun count(value: Int, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%,d", value)

    /** Duración de video: "0:07", "12:34" o "1:02:03". */
    fun duration(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0) + 500) / 1000 // redondeo al segundo más cercano
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    /** Fecha con el formato medio del idioma. Ej.: "10 oct 2026". */
    fun date(epochMs: Long, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .format(Instant.ofEpochMilli(epochMs).atZone(zone))

    /** Fecha y hora. Ej.: "10 oct 2026, 10:45 p. m.". */
    fun dateTime(epochMs: Long, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .format(Instant.ofEpochMilli(epochMs).atZone(zone))
}
