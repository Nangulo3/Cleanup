package com.example.cleanup.deletion

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cleanup.data.db.AppDatabase
import com.example.cleanup.data.db.entity.ActionType
import com.example.cleanup.domain.model.DeletionSource
import com.example.cleanup.domain.model.MediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrashRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var trash: TrashRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        trash = TrashRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun candidate(id: Long, size: Long? = 100) =
        MarkCandidate(id, "content://media/external_primary/images/media/$id", MediaType.PHOTO, "img$id.jpg", size, id)

    private suspend fun pendingIds() = trash.pendingIds.first()

    @Test
    fun marcarDesdeDosVistasNoDuplica() = runTest {
        assertEquals(2, trash.mark(listOf(candidate(1), candidate(2)), DeletionSource.CATEGORY))
        // El mismo elemento marcado desde otra pantalla: no se añade otra vez.
        assertEquals(0, trash.mark(listOf(candidate(1)), DeletionSource.VIEWER))
        assertEquals(setOf(1L, 2L), pendingIds())
        assertEquals(2, trash.summary.first().itemCount)
    }

    @Test
    fun deshacerMarcarQuitaSoloLoAnadido() = runTest {
        trash.mark(listOf(candidate(1)), DeletionSource.CATEGORY)
        trash.mark(listOf(candidate(1), candidate(2)), DeletionSource.CATEGORY) // solo añade el 2
        val undo = trash.undoLast()
        assertEquals(ActionType.MARK, undo?.type)
        assertEquals(1, undo?.itemCount)
        assertEquals(setOf(1L), pendingIds())
    }

    @Test
    fun deshacerDesmarcarRestauraLosElementos() = runTest {
        trash.mark(listOf(candidate(1), candidate(2)), DeletionSource.CATEGORY)
        assertEquals(2, trash.unmark(listOf(1L, 2L)))
        assertTrue(pendingIds().isEmpty())
        val undo = trash.undoLast()
        assertEquals(ActionType.UNMARK, undo?.type)
        assertEquals(setOf(1L, 2L), pendingIds())
    }

    @Test
    fun sinAccionesNoHayNadaQueDeshacer() = runTest {
        assertNull(trash.undoLast())
        assertFalse(trash.canUndo.first())
    }

    @Test
    fun elementosEliminadosNoResucitanAlDeshacer() = runTest {
        trash.mark(listOf(candidate(1), candidate(2)), DeletionSource.CATEGORY)
        trash.unmark(listOf(1L, 2L))
        // Android confirmó que el 1 ya no existe.
        trash.forgetDeleted(listOf(1L))
        trash.undoLast()
        assertEquals(setOf(2L), pendingIds())
    }

    @Test
    fun tamanosDesconocidosSeCuentanAparte() = runTest {
        trash.mark(listOf(candidate(1, 500), candidate(2, null)), DeletionSource.CATEGORY)
        val summary = trash.summary.first()
        assertEquals(500L, summary.knownBytes)
        assertEquals(1, summary.unknownSizeCount)
    }
}
