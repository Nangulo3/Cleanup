package com.example.cleanup.data.repo

import com.example.cleanup.data.db.AppDatabase
import com.example.cleanup.data.db.entity.DuplicateKeeperEntity
import com.example.cleanup.deletion.MarkCandidate
import com.example.cleanup.deletion.TrashRepository
import com.example.cleanup.detection.DuplicateGroup
import com.example.cleanup.detection.DuplicateGrouping
import com.example.cleanup.domain.model.DeletionSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class DuplicateSummary(
    val groupCount: Int = 0,
    val surplusCount: Int = 0,
    val surplusBytes: Long = 0,
)

/** Resultado de marcar copias sobrantes. */
data class MarkSurplusResult(
    val added: Int,
    /** Grupos omitidos porque la copia a conservar ya está en la papelera. */
    val skippedGroups: Int,
)

class DuplicatesRepository(
    db: AppDatabase,
    private val trash: TrashRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.fingerprintDao()

    val groups: Flow<List<DuplicateGroup>> = combine(
        dao.observeDuplicateRows(),
        dao.observeKeepers(),
    ) { rows, keepers ->
        DuplicateGrouping.build(rows, keepers.associate { it.groupKey to it.mediaId })
    }

    val summary: Flow<DuplicateSummary> = groups.map { groups ->
        DuplicateSummary(
            groupCount = groups.size,
            surplusCount = groups.sumOf { it.surplus.size },
            surplusBytes = groups.sumOf { it.surplusBytes },
        )
    }

    suspend fun setKeeper(groupKey: String, mediaId: Long) {
        dao.upsertKeeper(DuplicateKeeperEntity(groupKey, mediaId, clock()))
    }

    /**
     * Envía a la papelera las copias sobrantes de [groupKeys] (o de todos los grupos si es null).
     * Nunca marca la copia a conservar. Si esa copia ya está en la papelera, el grupo se omite
     * para no dejar el grupo sin ninguna copia.
     */
    suspend fun markSurplus(groupKeys: Set<String>?): MarkSurplusResult {
        val all = groups.first()
        val pending = trash.pendingIds.first()
        val targets = if (groupKeys == null) all else all.filter { it.key in groupKeys }
        val (blocked, allowed) = targets.partition { it.keeperId in pending }
        val candidates = allowed.flatMap { it.surplus }.map {
            MarkCandidate(it.id, it.contentUri, it.mediaType, it.displayName, it.sizeBytes, it.sortDateMs)
        }
        val added = if (candidates.isEmpty()) 0 else trash.mark(candidates, DeletionSource.DUPLICATES)
        return MarkSurplusResult(added = added, skippedGroups = blocked.size)
    }

    /** Cuántos grupos quedarían sin ninguna copia si se eliminan todos los [ids]. */
    suspend fun groupsLosingAllCopies(ids: Set<Long>): Int =
        groups.first().count { group -> group.members.all { it.id in ids } }
}
