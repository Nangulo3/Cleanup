package com.example.cleanup.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cleanup.data.db.AppDatabase
import com.example.cleanup.data.db.entity.FingerprintEntity
import com.example.cleanup.data.db.entity.MediaItemEntity
import com.example.cleanup.deletion.MarkCandidate
import com.example.cleanup.deletion.TrashRepository
import com.example.cleanup.domain.model.DeletionSource
import com.example.cleanup.domain.model.MediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DuplicatesRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var trash: TrashRepository
    private lateinit var duplicates: DuplicatesRepository

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        trash = TrashRepository(db)
        duplicates = DuplicatesRepository(db, trash)
        // Grupo "h1": 1 (cámara), 2 y 3 (descargas). El 4 es único.
        db.mediaDao().upsertAll(
            listOf(media(1, "DCIM/Camera/"), media(2, "Download/"), media(3, "Download/"), media(4, "Pictures/"))
        )
        db.fingerprintDao().upsertAll(listOf(fp(1, "h1"), fp(2, "h1"), fp(3, "h1"), fp(4, "h2")))
    }

    @After
    fun tearDown() = db.close()

    private fun media(id: Long, path: String) = MediaItemEntity(
        id = id, mediaType = MediaType.PHOTO, contentUri = "content://media/external_primary/images/media/$id",
        volumeName = "external_primary", displayName = "img$id.jpg", mimeType = "image/jpeg", relativePath = path,
        bucketName = null, sizeBytes = 1_000, width = null, height = null, orientation = null, durationMs = null,
        dateTakenMs = null, dateAddedMs = id, dateModifiedMs = 5, generationModified = 9,
        sortDateMs = id, monthKey = 0, catalogedAt = 0,
    )

    private fun fp(id: Long, sha: String) = FingerprintEntity(
        mediaId = id, sizeBytes = 1_000, dateModifiedMs = 5, generationModified = 9,
        quickHash = "q", sha256 = sha, hashError = null, hashedAt = 0,
    )

    @Test
    fun agrupaSoloLosQueCompartenHashYSugiereLaCopiaDeCamara() = runTest {
        val group = duplicates.groups.first().single()
        assertEquals(setOf(1L, 2L, 3L), group.members.map { it.id }.toSet())
        assertEquals(1L, group.keeperId)
        val summary = duplicates.summary.first()
        assertEquals(2, summary.surplusCount)
        assertEquals(2_000L, summary.surplusBytes)
    }

    @Test
    fun marcarSobrantesNuncaMarcaLaCopiaConservada() = runTest {
        val result = duplicates.markSurplus(null)
        assertEquals(2, result.added)
        val pending = trash.pendingIds.first()
        assertEquals(setOf(2L, 3L), pending)
        assertFalse(1L in pending)
    }

    @Test
    fun cambiarLaCopiaConservadaSeRespeta() = runTest {
        val key = duplicates.groups.first().single().key
        duplicates.setKeeper(key, 3L)
        assertEquals(3L, duplicates.groups.first().single().keeperId)
        duplicates.markSurplus(setOf(key))
        assertEquals(setOf(1L, 2L), trash.pendingIds.first())
    }

    @Test
    fun siLaCopiaConservadaEstaEnLaPapeleraElGrupoSeOmite() = runTest {
        trash.mark(listOf(MarkCandidate(1, "content://x/1", MediaType.PHOTO, null, 1_000, 1)), DeletionSource.VIEWER)
        val result = duplicates.markSurplus(null)
        assertEquals(0, result.added)
        assertEquals(1, result.skippedGroups)
    }

    @Test
    fun detectaCuandoSeEliminarianTodasLasCopias() = runTest {
        assertEquals(1, duplicates.groupsLosingAllCopies(setOf(1L, 2L, 3L)))
        assertEquals(0, duplicates.groupsLosingAllCopies(setOf(2L, 3L)))
    }

    @Test
    fun unaHuellaVencidaNoCuenta() = runTest {
        // El archivo 3 cambió (nueva fecha de modificación): su huella ya no vale.
        db.mediaDao().upsertAll(listOf(media(3, "Download/").copy(dateModifiedMs = 99)))
        val group = duplicates.groups.first().single()
        assertEquals(setOf(1L, 2L), group.members.map { it.id }.toSet())
    }
}
