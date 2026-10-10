package com.example.cleanup.data.media

import com.example.cleanup.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class MediaRowMapperTest {

    private val bogota = ZoneId.of("America/Bogota")

    private fun row(
        isVideo: Boolean = false,
        size: Long? = 1_000,
        width: Int? = 4000,
        height: Int? = 3000,
        duration: Long? = null,
        dateTakenMs: Long? = null,
        dateAddedSec: Long? = null,
        dateModifiedSec: Long? = null,
        displayName: String? = "IMG_0001.jpg",
    ) = RawMediaRow(
        id = 42,
        volumeName = "external_primary",
        isVideo = isVideo,
        contentUri = "content://media/external_primary/images/media/42",
        displayName = displayName,
        mimeType = "image/jpeg",
        size = size,
        width = width,
        height = height,
        orientation = 0,
        duration = duration,
        dateTakenMs = dateTakenMs,
        dateAddedSec = dateAddedSec,
        dateModifiedSec = dateModifiedSec,
        relativePath = "DCIM/Camera/",
        bucketName = "Camera",
        generationModified = 10,
    )

    @Test
    fun `prefiere la fecha de captura`() {
        val entity = MediaRowMapper.toEntity(
            row(dateTakenMs = 1_700_000_000_000, dateModifiedSec = 1_800_000_000, dateAddedSec = 1_600_000_000),
            catalogedAt = 0,
            zone = bogota,
        )
        assertEquals(1_700_000_000_000, entity.sortDateMs)
    }

    @Test
    fun `sin fecha de captura usa la de modificacion convertida a milisegundos`() {
        val entity = MediaRowMapper.toEntity(row(dateModifiedSec = 1_800_000_000, dateAddedSec = 1_600_000_000), 0, bogota)
        assertEquals(1_800_000_000_000, entity.sortDateMs)
        assertEquals(1_600_000_000_000, entity.dateAddedMs)
    }

    @Test
    fun `sin ninguna fecha queda en cero y sin mes`() {
        val entity = MediaRowMapper.toEntity(row(), 0, bogota)
        assertEquals(0L, entity.sortDateMs)
        assertEquals(0, entity.monthKey)
    }

    @Test
    fun `valores cero o negativos se guardan como desconocidos`() {
        val entity = MediaRowMapper.toEntity(row(size = 0, width = 0, height = -1, dateTakenMs = 0), 0, bogota)
        assertNull(entity.sizeBytes)
        assertNull(entity.width)
        assertNull(entity.height)
        assertNull(entity.dateTakenMs)
    }

    @Test
    fun `la duracion solo aplica a videos`() {
        assertNull(MediaRowMapper.toEntity(row(isVideo = false, duration = 5_000), 0, bogota).durationMs)
        val video = MediaRowMapper.toEntity(row(isVideo = true, duration = 5_000), 0, bogota)
        assertEquals(MediaType.VIDEO, video.mediaType)
        assertEquals(5_000L, video.durationMs)
    }

    @Test
    fun `nombre vacio se trata como desconocido`() {
        assertNull(MediaRowMapper.toEntity(row(displayName = "  "), 0, bogota).displayName)
    }

    @Test
    fun `el mes usa la zona horaria local`() {
        // 2026-11-01T03:00Z es todavía 31 de octubre a las 22:00 en Bogotá (UTC-5).
        val epochMs = 1_793_502_000_000
        assertEquals(202610, MediaRowMapper.monthKey(epochMs, bogota))
        assertEquals(202611, MediaRowMapper.monthKey(epochMs, ZoneId.of("UTC")))
    }

    @Test
    fun `clasifica capturas y fotos de chat al catalogar`() {
        val screenshot = row(width = 1080, height = 2340).copy(
            relativePath = "DCIM/Screenshots/", displayName = "Screenshot_1.png", mimeType = "image/png",
        )
        val entity = MediaRowMapper.toEntity(screenshot, 0, bogota, com.example.cleanup.domain.model.ScreenInfo(1080, 2340))
        assertEquals(true, entity.isScreenshot)
        val chat = row().copy(relativePath = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/")
        assertEquals("WhatsApp", MediaRowMapper.toEntity(chat, 0, bogota).chatApp)
    }
}
