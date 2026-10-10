package com.example.cleanup.data.repo

import com.example.cleanup.data.db.dao.MediaDao
import com.example.cleanup.data.db.dao.MediaGridItem
import com.example.cleanup.data.db.dao.ScanDao
import com.example.cleanup.data.db.dao.TypeTotals
import com.example.cleanup.data.db.entity.MediaItemEntity
import com.example.cleanup.data.db.entity.ScanStateEntity
import com.example.cleanup.domain.model.Category
import com.example.cleanup.domain.model.MediaSort
import com.example.cleanup.domain.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Punto de acceso de la interfaz a los datos del catálogo (siempre como Flow observable). */
class MediaRepository(
    private val mediaDao: MediaDao,
    private val scanDao: ScanDao,
) {
    val typeTotals: Flow<List<TypeTotals>> = mediaDao.observeTypeTotals()

    val scanState: Flow<ScanStateEntity?> = scanDao.observeScanState()

    /** Elementos de una categoría plana. Las categorías de grupos llegan en fases siguientes. */
    fun observeCategoryItems(category: Category, sort: MediaSort): Flow<List<MediaGridItem>> =
        when (category) {
            Category.PHOTOS -> mediaDao.observeGridItems(MediaType.PHOTO, sort.sqlCode)
            Category.VIDEOS -> mediaDao.observeGridItems(MediaType.VIDEO, sort.sqlCode)
            else -> flowOf(emptyList())
        }

    fun observeItem(id: Long): Flow<MediaItemEntity?> = mediaDao.observeItem(id)

    suspend fun recordScanError(code: String) {
        val current = scanDao.scanState() ?: ScanStateEntity()
        scanDao.upsertScanState(current.copy(lastError = code))
    }
}
