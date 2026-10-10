package com.example.cleanup.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cleanup.data.db.entity.MediaItemEntity
import com.example.cleanup.domain.model.MediaSort
import com.example.cleanup.domain.model.MediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = db.close()

    private fun item(id: Long, type: MediaType, size: Long?, date: Long, name: String) = MediaItemEntity(
        id = id, mediaType = type, contentUri = "content://test/$id", volumeName = "external_primary",
        displayName = name, mimeType = null, relativePath = null, bucketName = null, sizeBytes = size,
        width = null, height = null, orientation = null, durationMs = null, dateTakenMs = date,
        dateAddedMs = null, dateModifiedMs = null, generationModified = null, sortDateMs = date,
        monthKey = 0, catalogedAt = 0,
    )

    @Test
    fun totalesPorTipoSeparanTamanosDesconocidos() = runTest {
        val dao = db.mediaDao()
        dao.upsertAll(
            listOf(
                item(1, MediaType.PHOTO, 100, 1, "a"),
                item(2, MediaType.PHOTO, null, 2, "b"),
                item(3, MediaType.VIDEO, 1_000, 3, "c"),
            )
        )
        val totals = dao.observeTypeTotals().first().associateBy { it.mediaType }
        assertEquals(2, totals.getValue(MediaType.PHOTO).itemCount)
        assertEquals(100L, totals.getValue(MediaType.PHOTO).knownBytes)
        assertEquals(1, totals.getValue(MediaType.PHOTO).unknownSizeCount)
        assertEquals(1_000L, totals.getValue(MediaType.VIDEO).knownBytes)
    }

    @Test
    fun upsertActualizaSinDuplicar() = runTest {
        val dao = db.mediaDao()
        dao.upsertAll(listOf(item(1, MediaType.PHOTO, 100, 1, "a")))
        dao.upsertAll(listOf(item(1, MediaType.PHOTO, 200, 1, "a")))
        assertEquals(1, dao.count())
        assertEquals(200L, dao.observeItem(1).first()?.sizeBytes)
    }

    @Test
    fun ordenesDeLaCuadricula() = runTest {
        val dao = db.mediaDao()
        dao.upsertAll(
            listOf(
                item(1, MediaType.PHOTO, 300, 10, "beta"),
                item(2, MediaType.PHOTO, 100, 30, "Alfa"),
                item(3, MediaType.PHOTO, null, 20, "gamma"),
            )
        )
        suspend fun ids(sort: MediaSort) =
            dao.observeGridItems(MediaType.PHOTO, sort.sqlCode).first().map { it.id }
        assertEquals(listOf(2L, 3L, 1L), ids(MediaSort.DATE_DESC))
        assertEquals(listOf(1L, 3L, 2L), ids(MediaSort.DATE_ASC))
        assertEquals(listOf(1L, 2L, 3L), ids(MediaSort.SIZE_DESC)) // sin tamaño al final
        assertEquals(listOf(2L, 1L, 3L), ids(MediaSort.NAME_ASC))  // sin distinguir mayúsculas
    }

    @Test
    fun borrarPorIds() = runTest {
        val dao = db.mediaDao()
        dao.upsertAll((1L..5L).map { item(it, MediaType.PHOTO, 1, it, "n$it") })
        dao.deleteByIds(listOf(2L, 4L))
        assertEquals(listOf(1L, 3L, 5L), dao.allIds().sorted())
    }
}
