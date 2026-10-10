package com.example.cleanup.detection

import com.example.cleanup.data.db.dao.DuplicateRow
import com.example.cleanup.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KeepSuggesterTest {

    private fun c(id: Long, path: String?, taken: Long? = null, added: Long? = null) = KeepCandidate(id, path, taken, added)

    @Test
    fun `prefiere la carpeta de la camara sobre descargas y chats`() {
        val s = KeepSuggester.suggest(
            listOf(
                c(1, "Download/"),
                c(2, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"),
                c(3, "DCIM/Camera/"),
            )
        )
        assertEquals(3L, s.keeperId)
        assertEquals(KeepReason.BETTER_FOLDER, s.reason)
    }

    @Test
    fun `a igual carpeta prefiere la que tiene fecha de captura`() {
        val s = KeepSuggester.suggest(listOf(c(1, "Pictures/", taken = null), c(2, "Pictures/", taken = 5)))
        assertEquals(2L, s.keeperId)
        assertEquals(KeepReason.HAS_CAPTURE_DATE, s.reason)
    }

    @Test
    fun `despues prefiere la mas antigua`() {
        val s = KeepSuggester.suggest(listOf(c(1, "Pictures/", added = 20), c(2, "Pictures/", added = 10)))
        assertEquals(2L, s.keeperId)
        assertEquals(KeepReason.OLDEST, s.reason)
    }

    @Test
    fun `sin diferencias elige siempre la misma`() {
        val members = listOf(c(7, "Pictures/", added = 10), c(3, "Pictures/", added = 10))
        val first = KeepSuggester.suggest(members)
        val second = KeepSuggester.suggest(members.reversed())
        assertEquals(3L, first.keeperId)
        assertEquals(first, second)
        assertEquals(KeepReason.STABLE_DEFAULT, first.reason)
    }

    private fun row(id: Long, key: String, path: String) = DuplicateRow(
        groupKey = key, id = id, mediaType = MediaType.PHOTO, contentUri = "content://x/$id", displayName = null,
        mimeType = null, sizeBytes = 100, width = null, height = null, durationMs = null, sortDateMs = 0,
        dateTakenMs = null, dateAddedMs = null, dateModifiedMs = null, relativePath = path,
    )

    @Test
    fun `la eleccion del usuario se respeta y los sobrantes nunca incluyen la copia conservada`() {
        val rows = listOf(row(1, "h", "DCIM/Camera/"), row(2, "h", "Download/"))
        val suggested = DuplicateGrouping.build(rows, emptyMap()).single()
        assertEquals(1L, suggested.keeperId)
        val chosen = DuplicateGrouping.build(rows, mapOf("h" to 2L)).single()
        assertEquals(2L, chosen.keeperId)
        assertEquals(KeepReason.USER_CHOICE, chosen.reason)
        assertFalse(chosen.surplus.any { it.id == chosen.keeperId })
    }

    @Test
    fun `una eleccion que ya no esta en el grupo se ignora`() {
        val rows = listOf(row(1, "h", "DCIM/Camera/"), row(2, "h", "Download/"))
        val group = DuplicateGrouping.build(rows, mapOf("h" to 99L)).single()
        assertEquals(1L, group.keeperId)
    }
}
