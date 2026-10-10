package com.example.cleanup.domain.model

/** Desde dónde se marcó un elemento para eliminar (se guarda para mostrarlo y depurar). */
enum class DeletionSource {
    VIEWER,
    CATEGORY,
    SWIPE,
    DUPLICATES,
    SIMILAR,
}
