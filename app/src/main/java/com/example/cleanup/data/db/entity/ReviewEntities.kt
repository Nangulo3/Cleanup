package com.example.cleanup.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.cleanup.domain.model.ReviewDecision

/**
 * Decisión de Deslizar para un elemento. Un elemento "está revisado" si tiene fila aquí;
 * así el progreso sobrevive al cierre de la app y la posición se deduce sola
 * (el primer elemento del mes sin decisión).
 */
@Entity(tableName = "review_decisions", indices = [Index(value = ["decidedAt"])])
data class ReviewDecisionEntity(
    @PrimaryKey val mediaId: Long,
    val decision: ReviewDecision,
    val decidedAt: Long,
)
