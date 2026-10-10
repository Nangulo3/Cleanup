package com.example.cleanup.detection

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cleanup.data.db.AppDatabase
import com.example.cleanup.data.media.MediaStoreSource
import com.example.cleanup.domain.model.MediaAccess
import com.example.cleanup.worker.CatalogScanner
import com.example.cleanup.worker.HashScanner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * Integración con el MediaStore real: crea archivos propios con contenido controlado y comprueba
 * que solo los idénticos byte a byte quedan agrupados.
 */
@RunWith(AndroidJUnit4::class)
class HashScannerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private val inserted = mutableListOf<Uri>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        inserted.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
        db.close()
    }

    private fun insert(name: String, content: ByteArray): Long {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/CleanupTest")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
        inserted += uri
        resolver.openOutputStream(uri)!!.use { it.write(content) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri.lastPathSegment!!.toLong()
    }

    @Test
    fun soloLosIdenticosQuedanAgrupados() = runTest {
        val tag = System.currentTimeMillis()
        val base = Random(42).nextBytes(200_000)
        val idA = insert("dup_a_$tag.png", base)
        val idB = insert("dup_b_$tag.png", base.copyOf())                                   // copia exacta, otro nombre
        insert("dup_c_$tag.png", base.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }) // cambia el final
        insert("dup_d_$tag.png", base.copyOf().also { it[100_000] = (it[100_000] + 1).toByte() }) // cambia el medio

        CatalogScanner(MediaStoreSource(context), db).run(forceFull = true, access = MediaAccess.FULL) {}
        val result = HashScanner(db, ContentHasher(context)).run(retryErrors = false) {}

        val groups = DuplicateGrouping.build(db.fingerprintDao().observeDuplicateRows().first(), emptyMap())
        val ours = groups.filter { group -> group.members.any { it.id == idA || it.id == idB } }
        assertEquals(1, ours.size)
        assertEquals(setOf(idA, idB), ours.single().members.map { it.id }.toSet())
        assertEquals(0, result.errors)
    }

    @Test
    fun noRecalculaHuellasVigentes() = runTest {
        val tag = System.currentTimeMillis()
        val base = Random(7).nextBytes(150_000)
        insert("same_a_$tag.png", base)
        insert("same_b_$tag.png", base.copyOf())

        val catalog = CatalogScanner(MediaStoreSource(context), db)
        val hashes = HashScanner(db, ContentHasher(context))
        catalog.run(forceFull = true, access = MediaAccess.FULL) {}
        hashes.run(retryErrors = false) {}
        val second = hashes.run(retryErrors = false) {}
        assertEquals(0, second.quickHashed)
        assertEquals(0, second.fullHashed)
    }
}
