package com.example.cleanup.data.repo

import com.example.cleanup.data.db.AppDatabase
import com.example.cleanup.data.db.dao.SimilarRow
import com.example.cleanup.data.db.entity.SimilarKind
import com.example.cleanup.deletion.MarkCandidate
import com.example.cleanup.deletion.TrashRepository
import com.example.cleanup.domain.model.DeletionSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Grupo de elementos parecidos (no idénticos) para la interfaz. */
data class SimilarGroup(
    val id: Long,
    val kind: SimilarKind,
    /** Sugerencia opcional (la más nítida). Nunca se preselecciona. */
    val suggestedId: Long?,
    val maxDistance: Int,
    val members: List<SimilarRow>,
) {
    val totalBytes: Long get() = members.sumOf { it.sizeBytes ?: 0L }
}

data class SimilarSummary(
    val groupCount: Int = 0,
    val itemCount: Int = 0,
    val totalBytes: Long = 0,
)

class SimilarRepository(
    db: AppDatabase,
    private val trash: TrashRepository,
) {
    private val dao = db.similarDao()

    /** Videos que no se pudieron analizar (se muestran como aviso; "Analizar" los reintenta). */
    val videoErrorCount: Flow<Int> = dao.observeVideoErrorCount()

    /** Grupos de un tipo. Si un miembro se eliminó y el grupo queda con uno solo, deja de mostrarse. */
    fun groups(kind: SimilarKind): Flow<List<SimilarGroup>> = dao.observeGroupRows(kind).map { rows ->
        rows.groupBy { it.groupId }
            .filterValues { it.size >= 2 }
            .map { (groupId, members) ->
                val suggested = members.first().suggestedId?.takeIf { id -> members.any { it.id == id } }
                SimilarGroup(groupId, kind, suggested, members.first().maxDistance, members)
            }
    }

    fun summary(kind: SimilarKind): Flow<SimilarSummary> = groups(kind).map { groups ->
        SimilarSummary(
            groupCount = groups.size,
            itemCount = groups.sumOf { it.members.size },
            totalBytes = groups.sumOf { it.totalBytes },
        )
    }

    /** Envía a la papelera exactamente los elementos que el usuario seleccionó. */
    suspend fun mark(rows: List<SimilarRow>): Int = trash.mark(
        rows.map { MarkCandidate(it.id, it.contentUri, it.mediaType, it.displayName, it.sizeBytes, it.sortDateMs) },
        DeletionSource.SIMILAR,
    )
}
