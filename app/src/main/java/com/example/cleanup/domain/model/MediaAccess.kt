package com.example.cleanup.domain.model

/** Nivel de acceso a la biblioteca de fotos y videos que Android concede a la app. */
enum class MediaAccess {
    /** Puede ver todas las fotos y videos compartidos. */
    FULL,

    /** Android 14+: el usuario eligió solo algunos elementos ("Seleccionar fotos y videos"). */
    PARTIAL,

    /** Sin acceso. */
    DENIED,
}
