package com.example.cleanup.data.media

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.BaseColumns
import android.provider.MediaStore
import com.example.cleanup.domain.scan.VolumeSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Acceso de solo lectura a MediaStore. Nunca usa rutas de archivo: todo se identifica con el
 * ID de MediaStore y su URI. Las consultas excluyen por defecto los elementos pendientes y los
 * que están en la papelera del sistema.
 */
class MediaStoreSource(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    enum class Collection { IMAGES, VIDEO }

    /** Volúmenes externos (almacenamiento interno compartido y tarjetas SD) con su versión y generación. */
    fun volumeSnapshots(): List<VolumeSnapshot> =
        MediaStore.getExternalVolumeNames(appContext).map { name ->
            VolumeSnapshot(
                name = name,
                version = MediaStore.getVersion(appContext, name),
                generation = MediaStore.getGeneration(appContext, name),
            )
        }

    fun collectionUri(volumeName: String, collection: Collection): Uri = when (collection) {
        Collection.IMAGES -> MediaStore.Images.Media.getContentUri(volumeName)
        Collection.VIDEO -> MediaStore.Video.Media.getContentUri(volumeName)
    }

    /**
     * Lee las filas de una colección y las entrega en lotes de [batchSize] para no tener toda la
     * biblioteca en memoria. Con [sinceGeneration] solo trae lo añadido o modificado después.
     */
    suspend fun readRows(
        volumeName: String,
        collection: Collection,
        sinceGeneration: Long?,
        batchSize: Int = 500,
        onBatch: suspend (List<RawMediaRow>) -> Unit,
    ) {
        val selection = sinceGeneration?.let { "${MediaStore.MediaColumns.GENERATION_MODIFIED} > ?" }
        val args = sinceGeneration?.let { arrayOf(it.toString()) }
        query(volumeName, collection, selection, args, batchSize, onBatch)
    }

    /** Lee filas concretas por ID (para elementos que aparecieron sin cambiar de generación). */
    suspend fun readRowsByIds(
        volumeName: String,
        collection: Collection,
        ids: List<Long>,
        onBatch: suspend (List<RawMediaRow>) -> Unit,
    ) {
        // Lotes de 500 por el límite de 999 variables de SQLite.
        for (chunk in ids.chunked(500)) {
            val placeholders = chunk.joinToString(",") { "?" }
            query(
                volumeName = volumeName,
                collection = collection,
                selection = "${BaseColumns._ID} IN ($placeholders)",
                args = chunk.map { it.toString() }.toTypedArray(),
                batchSize = 500,
                onBatch = onBatch,
            )
        }
    }

    /** Solo los IDs visibles de una colección: consulta barata para detectar altas y bajas. */
    suspend fun readIds(volumeName: String, collection: Collection): Set<Long> =
        withContext(Dispatchers.IO) {
            val ids = HashSet<Long>()
            resolver.query(
                collectionUri(volumeName, collection),
                arrayOf(BaseColumns._ID),
                null,
                null,
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(BaseColumns._ID)
                while (cursor.moveToNext()) {
                    if (cursor.position % 1000 == 0) ensureActive()
                    ids += cursor.getLong(idColumn)
                }
            }
            ids
        }

    private suspend fun query(
        volumeName: String,
        collection: Collection,
        selection: String?,
        args: Array<String>?,
        batchSize: Int,
        onBatch: suspend (List<RawMediaRow>) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val collectionUri = collectionUri(volumeName, collection)
        val isVideo = collection == Collection.VIDEO
        resolver.query(collectionUri, PROJECTION, selection, args, "${BaseColumns._ID} ASC")
            ?.use { cursor ->
                val columns = Columns(cursor)
                val batch = ArrayList<RawMediaRow>(batchSize)
                while (cursor.moveToNext()) {
                    ensureActive() // permite cancelar el escaneo
                    batch += columns.read(cursor, volumeName, isVideo, collectionUri)
                    if (batch.size >= batchSize) {
                        onBatch(ArrayList(batch))
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) onBatch(batch)
            }
        Unit
    }

    /** Índices de columna calculados una vez por cursor. */
    private class Columns(cursor: Cursor) {
        val id = cursor.getColumnIndexOrThrow(BaseColumns._ID)
        val displayName = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val mimeType = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
        val size = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        val width = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
        val height = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
        val orientation = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)
        val duration = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
        val dateTaken = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
        val dateAdded = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        val dateModified = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
        val relativePath = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
        val bucket = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        val generation = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.GENERATION_MODIFIED)

        fun read(cursor: Cursor, volumeName: String, isVideo: Boolean, collectionUri: Uri): RawMediaRow {
            val mediaId = cursor.getLong(id)
            return RawMediaRow(
                id = mediaId,
                volumeName = volumeName,
                isVideo = isVideo,
                contentUri = ContentUris.withAppendedId(collectionUri, mediaId).toString(),
                displayName = cursor.stringOrNull(displayName),
                mimeType = cursor.stringOrNull(mimeType),
                size = cursor.longOrNull(size),
                width = cursor.intOrNull(width),
                height = cursor.intOrNull(height),
                orientation = cursor.intOrNull(orientation),
                duration = cursor.longOrNull(duration),
                dateTakenMs = cursor.longOrNull(dateTaken),
                dateAddedSec = cursor.longOrNull(dateAdded),
                dateModifiedSec = cursor.longOrNull(dateModified),
                relativePath = cursor.stringOrNull(relativePath),
                bucketName = cursor.stringOrNull(bucket),
                generationModified = cursor.longOrNull(generation),
            )
        }
    }

    private companion object {
        val PROJECTION = arrayOf(
            BaseColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.ORIENTATION,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.GENERATION_MODIFIED,
        )
    }
}

private fun Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
private fun Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)
private fun Cursor.intOrNull(index: Int): Int? = if (isNull(index)) null else getInt(index)
