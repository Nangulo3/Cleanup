package com.example.cleanup.domain.model

/** Decisión tomada en Deslizar. Ninguna de las dos modifica el archivo original. */
enum class ReviewDecision {
    /** "No marcar para eliminar". */
    KEPT,

    /** Añadido a la Papelera de revisión. */
    MARKED,
}
