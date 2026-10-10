package com.example.cleanup.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaClassifierTest {

    private val screen = ScreenInfo(1080, 2340)

    private fun input(path: String?, name: String?, mime: String? = "image/jpeg", w: Int? = 4000, h: Int? = 3000, video: Boolean = false) =
        ClassificationInput(path, name, mime, w, h, video)

    @Test
    fun `captura de Samsung por carpeta y nombre`() {
        val c = MediaClassifier.classify(input("DCIM/Screenshots/", "Screenshot_20261010-101010_WhatsApp.jpg", w = 1080, h = 2340), screen)
        assertTrue(c.isScreenshot)
        assertNull(c.chatApp) // el nombre menciona WhatsApp, pero la carpeta es de capturas
    }

    @Test
    fun `captura por carpeta aunque cambie el nombre`() {
        assertTrue(MediaClassifier.classify(input("Pictures/Screenshots/", "IMG_001.jpg"), screen).isScreenshot)
        assertTrue(MediaClassifier.classify(input("Pictures/Capturas de pantalla/", "x.png"), screen).isScreenshot)
    }

    @Test
    fun `png con las dimensiones de la pantalla cuenta como captura`() {
        assertTrue(MediaClassifier.classify(input("Pictures/", "imagen.png", "image/png", 2340, 1080), screen).isScreenshot)
    }

    @Test
    fun `una foto de camara no es captura`() {
        assertFalse(MediaClassifier.classify(input("DCIM/Camera/", "20261010_101010.jpg"), screen).isScreenshot)
    }

    @Test
    fun `un solo indicio debil no basta`() {
        // Solo PNG (1 punto) o solo dimensiones (2 puntos): no llega al umbral de 3.
        assertFalse(MediaClassifier.classify(input("Download/", "logo.png", "image/png", 512, 512), screen).isScreenshot)
        assertFalse(MediaClassifier.classify(input("Download/", "foto.jpg", "image/jpeg", 1080, 2340), screen).isScreenshot)
    }

    @Test
    fun `los videos nunca son capturas`() {
        assertFalse(MediaClassifier.classify(input("Movies/Screenshots/", "Screenshot.mp4", "video/mp4", video = true), screen).isScreenshot)
    }

    @Test
    fun `apps de chat por carpeta o nombre`() {
        assertEquals("WhatsApp", MediaClassifier.chatApp("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/", "x.jpg"))
        assertEquals("Telegram", MediaClassifier.chatApp("Pictures/Telegram/", "x.jpg"))
        assertEquals("WhatsApp", MediaClassifier.chatApp("Download/", "IMG-20261010-WA0003.jpg"))
        assertNull(MediaClassifier.chatApp("DCIM/Camera/", "20261010_101010.jpg"))
    }
}
