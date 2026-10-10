package com.example.cleanup.domain.model

/**
 * Categorías que se muestran en Inicio. Un mismo archivo puede pertenecer a varias
 * (por ejemplo, una captura que además está duplicada), así que los totales globales
 * nunca se calculan sumando categorías.
 *
 * [isGroupBased] indica si la categoría muestra grupos para comparar (duplicados, similares)
 * o una cuadrícula plana de elementos.
 */
enum class Category(val isGroupBased: Boolean) {
    DUPLICATES(isGroupBased = true),
    SIMILAR_PHOTOS(isGroupBased = true),
    SIMILAR_SCREENSHOTS(isGroupBased = true),
    SIMILAR_VIDEOS(isGroupBased = true),
    OTHER_SCREENSHOTS(isGroupBased = false),
    PHOTOS(isGroupBased = false),
    VIDEOS(isGroupBased = false),
    CHAT_PHOTOS(isGroupBased = false),
}
