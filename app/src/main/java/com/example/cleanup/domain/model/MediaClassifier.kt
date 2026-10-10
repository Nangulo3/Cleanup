package com.example.cleanup.domain.model

/** Tamaño de la pantalla del teléfono en píxeles (para reconocer capturas por sus dimensiones). */
data class ScreenInfo(val widthPx: Int, val heightPx: Int)

data class ClassificationInput(
    val relativePath: String?,
    val displayName: String?,
    val mimeType: String?,
    val width: Int?,
    val height: Int?,
    val isVideo: Boolean,
)

data class Classification(
    val isScreenshot: Boolean,
    /** App de chat inferida por la carpeta o el nombre ("WhatsApp", "Telegram"...), o null. */
    val chatApp: String?,
)

/**
 * Clasifica capturas de pantalla y fotos de chat combinando señales, sin depender de una sola
 * convención de nombres (cambian según fabricante, idioma y app):
 *
 * Captura (puntuación ≥ [SCREENSHOT_THRESHOLD]):
 *  - carpeta "Screenshots"/"Capturas…" ........ +3
 *  - nombre "Screenshot…"/"Captura…" .......... +2
 *  - dimensiones iguales a la pantalla ......... +2
 *  - formato PNG ............................... +1
 *
 * Chat: carpeta de WhatsApp, Telegram, Signal o Messenger, o nombre típico de WhatsApp (IMG-AAAAMMDD-WA…).
 * Es una inferencia: la interfaz lo indica así.
 */
object MediaClassifier {

    /** Subir este número fuerza a reclasificar todo el catálogo en el siguiente escaneo. */
    const val VERSION = 1
    const val SCREENSHOT_THRESHOLD = 3

    private val SCREENSHOT_FOLDER_HINTS = listOf("screenshot", "captura", "screen_capture", "screencapture")
    private val SCREENSHOT_NAME_PREFIXES = listOf("screenshot", "captura", "screen_", "scr_")
    private val WHATSAPP_NAME = Regex("^(img|vid)-\\d{8}-wa\\d+", RegexOption.IGNORE_CASE)

    fun classify(input: ClassificationInput, screen: ScreenInfo?): Classification = Classification(
        isScreenshot = !input.isVideo && screenshotScore(input, screen) >= SCREENSHOT_THRESHOLD,
        chatApp = chatApp(input.relativePath, input.displayName),
    )

    fun screenshotScore(input: ClassificationInput, screen: ScreenInfo?): Int {
        var score = 0
        val path = input.relativePath?.lowercase().orEmpty()
        val name = input.displayName?.lowercase().orEmpty()
        if (SCREENSHOT_FOLDER_HINTS.any { it in path }) score += 3
        if (SCREENSHOT_NAME_PREFIXES.any { name.startsWith(it) }) score += 2
        if (screen != null && matchesScreen(input.width, input.height, screen)) score += 2
        if (input.mimeType.equals("image/png", ignoreCase = true)) score += 1
        return score
    }

    fun chatApp(relativePath: String?, displayName: String?): String? {
        val path = relativePath?.lowercase().orEmpty()
        return when {
            "whatsapp" in path -> "WhatsApp"
            "telegram" in path -> "Telegram"
            path.startsWith("signal") || "/signal" in path -> "Signal"
            "messenger" in path -> "Messenger"
            displayName != null && WHATSAPP_NAME.containsMatchIn(displayName) -> "WhatsApp"
            else -> null
        }
    }

    private fun matchesScreen(width: Int?, height: Int?, screen: ScreenInfo): Boolean {
        if (width == null || height == null) return false
        return (width == screen.widthPx && height == screen.heightPx) ||
            (width == screen.heightPx && height == screen.widthPx)
    }
}
