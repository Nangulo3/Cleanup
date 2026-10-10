package com.example.cleanup.detection

/** Datos crudos para explicar por qué un archivo es (o no) duplicado exacto. */
data class DuplicateStatusData(
    val sizeBytes: Long?,
    /** Otros archivos del mismo tipo con exactamente el mismo tamaño. */
    val sameSizeCount: Int,
    val fingerprintValid: Boolean,
    val quickHash: String?,
    val sha256: String?,
    val hashError: String?,
    /** Otros archivos con la misma huella rápida vigente. */
    val sameQuickCount: Int,
    /** Otros archivos con el mismo SHA-256 vigente. */
    val identicalCount: Int,
)

/** En qué punto de la comparación está un archivo (se muestra en el visor). */
enum class DuplicateStatus {
    UNKNOWN_SIZE,
    /** Ningún otro archivo pesa exactamente lo mismo: no puede tener copias idénticas. */
    UNIQUE_SIZE,
    /** Hay archivos del mismo tamaño pero aún no se compararon (o cambió desde la última vez). */
    PENDING,
    READ_ERROR,
    /** Mismo tamaño que otros, pero el principio o el final del contenido es distinto. */
    DIFFERENT_CONTENT,
    /** Idéntico byte a byte a al menos otro archivo. */
    DUPLICATE,
}

object DuplicateStatusClassifier {
    fun classify(d: DuplicateStatusData): DuplicateStatus = when {
        d.sizeBytes == null -> DuplicateStatus.UNKNOWN_SIZE
        d.sameSizeCount == 0 -> DuplicateStatus.UNIQUE_SIZE
        !d.fingerprintValid -> DuplicateStatus.PENDING
        d.hashError != null -> DuplicateStatus.READ_ERROR
        d.identicalCount > 0 -> DuplicateStatus.DUPLICATE
        d.sha256 != null -> DuplicateStatus.DIFFERENT_CONTENT
        d.quickHash == null -> DuplicateStatus.PENDING
        // Comparte huella rápida con otro pero falta el hash completo: se completará en el próximo análisis.
        d.sameQuickCount > 0 -> DuplicateStatus.PENDING
        else -> DuplicateStatus.DIFFERENT_CONTENT
    }
}
