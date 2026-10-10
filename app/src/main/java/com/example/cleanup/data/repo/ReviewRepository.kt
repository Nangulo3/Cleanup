package com.example.cleanup.data.repo

import androidx.room.withTransaction
import com.example.cleanup.data.db.AppDatabase
import com.example.cleanup.data.db.dao.MonthProgress
import com.example.cleanup.data.db.dao.SwipeItem
import com.example.cleanup.data.db.entity.ActionItemEntity
import com.example.cleanup.data.db.entity.ActionLogEntity
import com.example.cleanup.data.db.entity.ActionType
import com.example.cleanup.data.db.entity.PendingDeletionEntity
import com.example.cleanup.data.db.entity.ReviewDecisionEntity
import com.example.cleanup.data.db.entity.TrashChange
import com.example.cleanup.domain.model.DeletionSource
import com.example.cleanup.domain.model.ReviewDecision
import kotlinx.coroutines.flow.Flow

/**
 * Datos del modo Deslizar. Cada decisión se guarda en review_decisions y, si cambia la
 * papelera, también allí, dentro de la misma transacción y con una entrada en el historial
 * para poder deshacerla.
 */
class ReviewRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val reviewDao = db.reviewDao()
    private val trashDao = db.trashDao()

    val months: Flow<List<MonthProgress>> = reviewDao.observeMonths()
    val lastDecisionMonth: Flow<Int?> = reviewDao.observeLastDecisionMonth()

    fun observeMonth(monthKey: Int): Flow<MonthProgress?> = reviewDao.observeMonth(monthKey)

    fun observeQueue(monthKey: Int, limit: Int = QUEUE_WINDOW): Flow<List<SwipeItem>> =
        reviewDao.observeQueue(monthKey, limit)

    /**
     * Registra una decisión:
     * - MARKED: añade a la papelera si no estaba.
     * - KEPT: si estaba en la papelera (marcado desde otra pantalla), lo quita, porque la decisión
     *   más reciente del usuario es conservarlo. Al deshacer se restaura la marca original.
     */
    suspend fun decide(item: SwipeItem, decision: ReviewDecision) {
        db.withTransaction {
            val now = clock()
            reviewDao.upsertDecision(ReviewDecisionEntity(item.id, decision, now))

            val existing = trashDao.pendingByIds(listOf(item.id)).firstOrNull()
            var change = TrashChange.NONE
            var snapshot = item.toPending(now)
            when {
                decision == ReviewDecision.MARKED && existing == null -> {
                    trashDao.insertPending(listOf(snapshot))
                    change = TrashChange.ADDED
                }
                decision == ReviewDecision.KEPT && existing != null -> {
                    trashDao.deletePending(listOf(item.id))
                    snapshot = existing // para restaurar la marca original al deshacer
                    change = TrashChange.REMOVED
                }
            }

            val actionId = trashDao.insertAction(ActionLogEntity(type = ActionType.REVIEW, createdAt = now, itemCount = 1))
            trashDao.insertActionItems(
                listOf(
                    ActionItemEntity(
                        actionId = actionId,
                        mediaId = snapshot.mediaId,
                        contentUri = snapshot.contentUri,
                        mediaType = snapshot.mediaType,
                        displayName = snapshot.displayName,
                        sizeBytes = snapshot.sizeBytes,
                        sortDateMs = snapshot.sortDateMs,
                        markedAt = snapshot.markedAt,
                        source = snapshot.source,
                        reviewDecision = decision,
                        trashChange = change,
                    )
                )
            )
            trashDao.pruneActions(MAX_UNDO_ACTIONS)
            trashDao.deleteOrphanActionItems()
        }
    }

    /**
     * Vuelve a poner todo el mes como "sin revisar". No toca la papelera ni los archivos.
     * También borra esas decisiones del historial de deshacer para que no se mezclen.
     */
    suspend fun resetMonth(monthKey: Int) {
        db.withTransaction {
            reviewDao.deleteReviewActionItemsForMonth(monthKey)
            trashDao.deleteEmptyActions()
            reviewDao.deleteDecisionsForMonth(monthKey)
        }
    }

    private fun SwipeItem.toPending(now: Long) = PendingDeletionEntity(
        mediaId = id,
        contentUri = contentUri,
        mediaType = mediaType,
        displayName = displayName,
        sizeBytes = sizeBytes,
        sortDateMs = sortDateMs,
        markedAt = now,
        source = DeletionSource.SWIPE,
    )

    companion object {
        /** Elementos que se cargan por adelantado: el actual y los siguientes para precargar. */
        const val QUEUE_WINDOW = 6
        private const val MAX_UNDO_ACTIONS = 50
    }
}
