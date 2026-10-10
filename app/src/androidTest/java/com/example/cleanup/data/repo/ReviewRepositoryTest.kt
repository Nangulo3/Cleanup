package com.example.cleanup.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cleanup.data.db.AppDatabase
import com.example.cleanup.data.db.dao.SwipeItem
import com.example.cleanup.data.db.entity.MediaItemEntity
import com.example.cleanup.deletion.MarkCandidate
import com.example.cleanup.deletion.TrashRepository
import com.example.cleanup.domain.model.DeletionSource
import com.example.cleanup.domain.model.MediaType
import com.example.cleanup.domain.model.ReviewDecision
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var review: ReviewRepository
    private lateinit var trash: TrashRepository
    private val month = 202610

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        review = ReviewRepository(db)
        trash = TrashRepository(db)
        db.mediaDao().upsertAll((1L..3L).map { media(it) })
    }

    @After
    fun tearDown() = db.close()

    private fun media(id: Long) = MediaItemEntity(
        id = id, mediaType = MediaType.PHOTO, contentUri = "content://media/external_primary/images/media/$id",
        volumeName = "external_primary", displayName = "img$id.jpg", mimeType = "image/jpeg", relativePath = null,
        bucketName = null, sizeBytes = 100, width = null, height = null, orientation = null, durationMs = null,
        dateTakenMs = id, dateAddedMs = null, dateModifiedMs = null, generationModified = null,
        sortDateMs = id, monthKey = month, catalogedAt = 0,
    )

    private suspend fun queueIds() = review.observeQueue(month).first().map { it.id }
    private suspend fun head(): SwipeItem = review.observeQueue(month).first().first()

    @Test
    fun conservarAvanzaYNoTocaLaPapelera() = runTest {
        assertEquals(listOf(1L, 2L, 3L), queueIds())
        review.decide(head(), ReviewDecision.KEPT)
        assertEquals(listOf(2L, 3L), queueIds())
        assertTrue(trash.pendingIds.first().isEmpty())
        val progress = review.observeMonth(month).first()!!
        assertEquals(1, progress.reviewed)
        assertEquals(1, progress.kept)
    }

    @Test
    fun eliminarAnadeALaPapeleraYDeshacerRecuperaLaPosicion() = runTest {
        review.decide(head(), ReviewDecision.MARKED)
        assertEquals(setOf(1L), trash.pendingIds.first())
        assertEquals(listOf(2L, 3L), queueIds())

        trash.undoLast()
        assertTrue(trash.pendingIds.first().isEmpty())
        assertEquals(listOf(1L, 2L, 3L), queueIds()) // vuelve a ser el actual
    }

    @Test
    fun conservarQuitaUnaMarcaPreviaYDeshacerLaRestaura() = runTest {
        trash.mark(listOf(MarkCandidate(1, "content://x/1", MediaType.PHOTO, null, 100, 1)), DeletionSource.CATEGORY)
        review.decide(head(), ReviewDecision.KEPT)
        assertFalse(1L in trash.pendingIds.first())

        trash.undoLast()
        val restored = trash.pending.first().single()
        assertEquals(1L, restored.mediaId)
        assertEquals(DeletionSource.CATEGORY, restored.source) // la marca original, no una nueva
    }

    @Test
    fun eliminarAlgoYaMarcadoNoDuplicaYDeshacerNoLoQuita() = runTest {
        trash.mark(listOf(MarkCandidate(1, "content://x/1", MediaType.PHOTO, null, 100, 1)), DeletionSource.CATEGORY)
        review.decide(head(), ReviewDecision.MARKED)
        assertEquals(1, trash.summary.first().itemCount)
        trash.undoLast() // deshace la decisión, pero la marca era anterior
        assertEquals(setOf(1L), trash.pendingIds.first())
    }

    @Test
    fun reiniciarMesVuelveATodoSinRevisarSinTocarLaPapelera() = runTest {
        review.decide(head(), ReviewDecision.MARKED)
        review.decide(head(), ReviewDecision.KEPT)
        review.resetMonth(month)
        assertEquals(listOf(1L, 2L, 3L), queueIds())
        assertEquals(setOf(1L), trash.pendingIds.first())
    }

    @Test
    fun listaDeMesesCuentaRevisados() = runTest {
        review.decide(head(), ReviewDecision.KEPT)
        val months = review.months.first()
        assertEquals(1, months.size)
        assertEquals(3, months[0].total)
        assertEquals(1, months[0].reviewed)
        assertEquals(2, months[0].pending)
        assertEquals(month, review.lastDecisionMonth.first())
    }
}
