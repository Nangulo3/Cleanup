package com.example.cleanup.detection

import com.example.cleanup.data.db.dao.DuplicateRow

/** Un grupo de archivos con contenido idéntico byte a byte. */
data class DuplicateGroup(
    /** SHA-256 compartido. */
    val key: String,
    val members: List<DuplicateRow>,
    val keeperId: Long,
    val reason: KeepReason,
) {
    val keeper: DuplicateRow get() = members.first { it.id == keeperId }
    /** Copias sobrantes: todas menos la que se conserva. Nunca incluye a la que se conserva. */
    val surplus: List<DuplicateRow> get() = members.filter { it.id != keeperId }
    val sizeEach: Long get() = members.first().sizeBytes
    val surplusBytes: Long get() = surplus.sumOf { it.sizeBytes }
}

object DuplicateGrouping {

    /**
     * Agrupa filas por SHA-256 y decide la copia a conservar: la elegida por el usuario si sigue
     * en el grupo; si no, la sugerida por [KeepSuggester].
     */
    fun build(rows: List<DuplicateRow>, userKeepers: Map<String, Long>): List<DuplicateGroup> =
        rows.groupBy { it.groupKey }
            .filterValues { it.size > 1 }
            .map { (key, members) ->
                val chosen = userKeepers[key]?.takeIf { id -> members.any { it.id == id } }
                if (chosen != null) {
                    DuplicateGroup(key, members, chosen, KeepReason.USER_CHOICE)
                } else {
                    val suggestion = KeepSuggester.suggest(
                        members.map { KeepCandidate(it.id, it.relativePath, it.dateTakenMs, it.dateAddedMs) }
                    )
                    DuplicateGroup(key, members, suggestion.keeperId, suggestion.reason)
                }
            }
            .sortedWith(compareByDescending<DuplicateGroup> { it.surplusBytes }.thenBy { it.key })
}
