package com.example.cleanup.worker

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cleanup.data.db.AppDatabase
import com.example.cleanup.data.media.MediaStoreSource
import com.example.cleanup.domain.model.MediaAccess
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba de integración con el MediaStore real del emulador o teléfono.
 * La app puede ver y borrar sin permisos los archivos que ella misma crea, así que la prueba
 * inserta imágenes propias en Pictures/CleanupTest y las limpia al final.
 */
@RunWith(AndroidJUnit4::class)
class CatalogScannerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var scanner: CatalogScanner
    private val inserted = mutableListOf<Uri>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        scanner = CatalogScanner(MediaStoreSource(context), db)
    }

    @After
    fun tearDown() {
        inserted.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
        db.close()
    }

    private fun insertTestImage(name: String, color: Int): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/CleanupTest")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
        )!!
        inserted += uri
        resolver.openOutputStream(uri)!!.use { out ->
            Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
                .compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri
    }

    private suspend fun catalogNames(): Set<String?> =
        db.mediaDao().allIds()
            .mapNotNull { id -> db.mediaDao().observeItem(id).first() }
            .map { it.displayName }
            .toSet()

    @Test
    fun escaneoCompletoEncuentraImagenesYDetectaBorrados() = runTest {
        val suffix = System.currentTimeMillis()
        val a = "cleanup_test_a_$suffix.png"
        val b = "cleanup_test_b_$suffix.png"
        insertTestImage(a, Color.RED)
        val uriB = insertTestImage(b, Color.BLUE)

        scanner.run(forceFull = true, access = MediaAccess.FULL) {}
        val afterFirst = catalogNames()
        assertTrue(a in afterFirst)
        assertTrue(b in afterFirst)

        // Borrado "desde otra app": la conciliación por IDs debe quitarlo del catálogo.
        context.contentResolver.delete(uriB, null, null)
        val result = scanner.run(forceFull = false, access = MediaAccess.FULL) {}
        val afterSecond = catalogNames()
        assertTrue(a in afterSecond)
        assertFalse(b in afterSecond)
        assertTrue(result.removed >= 1)
    }

    @Test
    fun escaneoIncrementalAgregaElementosNuevos() = runTest {
        scanner.run(forceFull = true, access = MediaAccess.FULL) {}
        val before = db.mediaDao().count()

        val name = "cleanup_test_c_${System.currentTimeMillis()}.png"
        insertTestImage(name, Color.GREEN)
        val result = scanner.run(forceFull = false, access = MediaAccess.FULL) {}

        assertFalse(result.wasFull)
        assertEquals(before + 1, db.mediaDao().count())
        assertTrue(name in catalogNames())
    }
}
