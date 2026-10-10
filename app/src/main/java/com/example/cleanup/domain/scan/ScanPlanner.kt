package com.example.cleanup.domain.scan

/** Estado actual de un volumen de almacenamiento según MediaStore. */
data class VolumeSnapshot(
    val name: String,
    /** Cambia si Android reconstruye su base de datos de medios: los IDs dejan de ser fiables. */
    val version: String,
    /** Contador que aumenta cada vez que se añade o modifica un archivo en el volumen. */
    val generation: Long,
)

/** Lo que guardamos del último escaneo exitoso de un volumen. */
data class StoredVolume(
    val name: String,
    val version: String,
    val lastGeneration: Long,
)

/** Plan para un volumen: [sinceGeneration] == null significa leerlo completo. */
data class VolumePlan(val volume: VolumeSnapshot, val sinceGeneration: Long?) {
    val isFull: Boolean get() = sinceGeneration == null
}

/**
 * Decide, por volumen, si basta con leer los cambios desde la última generación conocida
 * o si hay que releer todo. Es una función pura para poder probar cada caso.
 */
object ScanPlanner {
    fun plan(
        forceFull: Boolean,
        current: List<VolumeSnapshot>,
        stored: Map<String, StoredVolume>,
    ): List<VolumePlan> = current.map { volume ->
        val previous = stored[volume.name]
        val incrementalFrom = when {
            forceFull -> null
            previous == null -> null                         // volumen nuevo (p. ej. una SD recién insertada)
            previous.version != volume.version -> null       // MediaStore se reconstruyó
            volume.generation < previous.lastGeneration -> null // contador reiniciado: no es fiable
            else -> previous.lastGeneration
        }
        VolumePlan(volume, incrementalFrom)
    }
}
