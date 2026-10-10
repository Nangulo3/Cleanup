package com.example.cleanup.domain.model

/**
 * Orden de las cuadrículas. [sqlCode] es el número que recibe la consulta de Room
 * (Room no permite pasar ORDER BY como parámetro, así que se usa CASE en SQL).
 */
enum class MediaSort(val sqlCode: Int) {
    DATE_DESC(0),
    DATE_ASC(1),
    SIZE_DESC(2),
    NAME_ASC(3),
}
