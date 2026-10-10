package com.example.cleanup.detection

/** Datos de una copia que influyen en qué conviene conservar. */
data class KeepCandidate(
    val id: Long,
    val relativePath: String?,
    val dateTakenMs: Long?,
    val dateAddedMs: Long?,
)

/** Por qué se sugiere esa copia (la interfaz lo explica con un texto). */
enum class KeepReason {
    /** El usuario la eligió. */
    USER_CHOICE,

    /** Está en una carpeta de cámara o de fotos, y las otras en descargas, chats u otras. */
    BETTER_FOLDER,

    /** Conserva la fecha de captura y las otras no. */
    HAS_CAPTURE_DATE,

    /** Llegó antes al teléfono (probablemente es la original). */
    OLDEST,

    /** Ninguna destaca: se elige una de forma estable (siempre la misma). */
    STABLE_DEFAULT,
}

data class KeepSuggestion(val keeperId: Long, val reason: KeepReason)

/**
 * Sugiere qué copia de un grupo de duplicados conservar. Es solo una sugerencia: el usuario
 * puede cambiarla. El resultado es determinista (mismo grupo → misma sugerencia).
 */
object KeepSuggester {

    fun suggest(members: List<KeepCandidate>): KeepSuggestion {
        require(members.isNotEmpty()) { "Un grupo necesita al menos un miembro" }
        val sorted = members.sortedWith(ORDER)
        val best = sorted.first()
        val runnerUp = sorted.getOrNull(1) ?: return KeepSuggestion(best.id, KeepReason.STABLE_DEFAULT)
        val reason = when {
            folderRank(best.relativePath) != folderRank(runnerUp.relativePath) -> KeepReason.BETTER_FOLDER
            hasCaptureDate(best) != hasCaptureDate(runnerUp) -> KeepReason.HAS_CAPTURE_DATE
            (best.dateAddedMs ?: Long.MAX_VALUE) != (runnerUp.dateAddedMs ?: Long.MAX_VALUE) -> KeepReason.OLDEST
            else -> KeepReason.STABLE_DEFAULT
        }
        return KeepSuggestion(best.id, reason)
    }

    /** Menor = mejor lugar para la copia que se conserva. */
    fun folderRank(relativePath: String?): Int {
        val path = relativePath?.lowercase() ?: return 3
        return when {
            path.startsWith("dcim/camera") -> 0
            CHAT_HINTS.any { it in path } -> 5
            "download" in path -> 4
            path.startsWith("dcim/") -> 1
            path.startsWith("pictures/") || path.startsWith("movies/") -> 2
            else -> 3
        }
    }

    private fun hasCaptureDate(c: KeepCandidate) = (c.dateTakenMs ?: 0L) > 0L

    private val ORDER = compareBy<KeepCandidate> { folderRank(it.relativePath) }
        .thenBy { if (hasCaptureDate(it)) 0 else 1 }
        .thenBy { it.dateAddedMs ?: Long.MAX_VALUE }
        .thenBy { it.id }

    private val CHAT_HINTS = listOf("whatsapp", "telegram", "signal", "messenger")
}
