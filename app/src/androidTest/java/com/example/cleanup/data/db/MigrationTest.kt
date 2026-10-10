package com.example.cleanup.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cleanup.data.db.entity.FingerprintEntity
import com.example.cleanup.domain.model.DeletionSource
import com.example.cleanup.domain.model.MediaType
import com.example.cleanup.deletion.MarkCandidate
import com.example.cleanup.deletion.TrashRepository
import com.example.cleanup.data.repo.ReviewRepository
import com.example.cleanup.domain.model.ReviewDecision
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprueba que una base de datos creada con el esquema 1 (Fase 2) se actualiza a la versión
 * actual sin perder el catálogo. El esquema 1 se recrea con el SQL exacto de app/schemas/.../1.json.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createVersion1Database() {
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).use { db ->
            V1_SCHEMA.forEach { db.execSQL(it) }
            db.execSQL(
                "INSERT INTO media_items (id, mediaType, contentUri, volumeName, sortDateMs, monthKey, catalogedAt) " +
                    "VALUES (7, 'PHOTO', 'content://media/external_primary/images/media/7', 'external_primary', 1, 0, 0)"
            )
            db.version = 1
        }
    }

    @Test
    fun migraHastaLaVersionActualConservandoElCatalogo() = runTest {
        createVersion1Database()
        val db = AppDatabase.build(context, dbName)
        try {
            assertEquals(1, db.mediaDao().count())
            // Las tablas nuevas existen y funcionan.
            val trash = TrashRepository(db)
            val added = trash.mark(
                listOf(MarkCandidate(7, "content://media/external_primary/images/media/7", MediaType.PHOTO, null, null, 1)),
                DeletionSource.CATEGORY,
            )
            assertEquals(1, added)
            // Tabla de la versión 3 (Deslizar).
            val review = ReviewRepository(db)
            val item = review.observeQueue(0).first().single()
            review.decide(item, ReviewDecision.KEPT)
            assertEquals(1, review.observeMonth(0).first()?.reviewed)
            // Tablas de la versión 4 (duplicados).
            db.fingerprintDao().upsertAll(
                listOf(FingerprintEntity(7, null, null, null, "q", "s", null, 0))
            )
            db.fingerprintDao().deleteOrphans()
        } finally {
            db.close()
        }
    }

    private companion object {
        // Copiado de app/schemas/com.example.cleanup.data.db.AppDatabase/1.json
        val V1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `media_items` (`id` INTEGER NOT NULL, `mediaType` TEXT NOT NULL, `contentUri` TEXT NOT NULL, `volumeName` TEXT NOT NULL, `displayName` TEXT, `mimeType` TEXT, `relativePath` TEXT, `bucketName` TEXT, `sizeBytes` INTEGER, `width` INTEGER, `height` INTEGER, `orientation` INTEGER, `durationMs` INTEGER, `dateTakenMs` INTEGER, `dateAddedMs` INTEGER, `dateModifiedMs` INTEGER, `generationModified` INTEGER, `sortDateMs` INTEGER NOT NULL, `monthKey` INTEGER NOT NULL, `catalogedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_media_items_mediaType_sortDateMs` ON `media_items` (`mediaType`, `sortDateMs`)",
            "CREATE INDEX IF NOT EXISTS `index_media_items_monthKey` ON `media_items` (`monthKey`)",
            "CREATE INDEX IF NOT EXISTS `index_media_items_sizeBytes` ON `media_items` (`sizeBytes`)",
            "CREATE TABLE IF NOT EXISTS `volume_state` (`volumeName` TEXT NOT NULL, `mediaStoreVersion` TEXT NOT NULL, `lastGeneration` INTEGER NOT NULL, PRIMARY KEY(`volumeName`))",
            "CREATE TABLE IF NOT EXISTS `scan_state` (`id` INTEGER NOT NULL, `lastStartedAt` INTEGER, `lastCompletedAt` INTEGER, `lastWasFull` INTEGER NOT NULL, `lastAccess` TEXT, `lastError` TEXT, `lastItemCount` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'c2547885924830d62fd79ffaae858dbb')",
        )
    }
}
