package com.example.cleanup.detection

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * Prueba con miniaturas reales del sistema: la misma escena guardada como PNG y como JPEG
 * comprimido debe dar firmas cercanas; una escena distinta, lejanas.
 */
@RunWith(AndroidJUnit4::class)
class ThumbnailSignerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val inserted = mutableListOf<Uri>()

    @After
    fun tearDown() {
        inserted.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
    }

    private fun sceneBitmap(seed: Int, size: Int = 600): Bitmap {
        val rnd = Random(seed)
        val grid = Array(6) { IntArray(6) { rnd.nextInt(0, 255) } }
        val pixels = IntArray(size * size) { i ->
            val x = (i % size) / (size - 1f) * 5f
            val y = (i / size) / (size - 1f) * 5f
            val x0 = x.toInt().coerceAtMost(4); val y0 = y.toInt().coerceAtMost(4)
            val fx = x - x0; val fy = y - y0
            val v = (grid[y0][x0] * (1 - fx) * (1 - fy) + grid[y0][x0 + 1] * fx * (1 - fy) +
                grid[y0 + 1][x0] * (1 - fx) * fy + grid[y0 + 1][x0 + 1] * fx * fy).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (v shl 16) or ((255 - v) shl 8) or (v / 2)
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun insert(name: String, bitmap: Bitmap, format: Bitmap.CompressFormat, mime: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/CleanupTest")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
        inserted += uri
        resolver.openOutputStream(uri)!!.use { bitmap.compress(format, 80, it) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri
    }

    @Test
    fun mismaEscenaEnOtroFormatoEsCercanaYOtraEscenaLejana() {
        val tag = System.currentTimeMillis()
        val png = insert("scene_a_$tag.png", sceneBitmap(1), Bitmap.CompressFormat.PNG, "image/png")
        val jpg = insert("scene_a_$tag.jpg", sceneBitmap(1), Bitmap.CompressFormat.JPEG, "image/jpeg")
        val other = insert("scene_b_$tag.png", sceneBitmap(2), Bitmap.CompressFormat.PNG, "image/png")

        val signer = ThumbnailSigner(context)
        val a = signer.sign(png)
        val b = signer.sign(jpg)
        val c = signer.sign(other)

        val same = ImageSignatures.hamming(a.phash, b.phash)
        val different = ImageSignatures.hamming(a.phash, c.phash)
        assertTrue("misma escena: distancia $same", same <= 8)
        assertTrue("escenas distintas: distancia $different", different >= 16)
    }
}
