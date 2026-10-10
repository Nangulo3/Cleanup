package com.example.cleanup.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.cleanup.domain.model.DeletionSource
import com.example.cleanup.domain.model.MediaType
import com.example.cleanup.domain.model.ReviewDecision
import com.example.cleanup.settings.DeletionMode

/**
 * Un elemento en la Papelera de revisión. Es solo una marca: el archivo sigue en el teléfono.
 * La clave primaria es el ID de MediaStore, así que un archivo no puede estar dos veces en la
 * cola aunque se marque desde varias pantallas.
 *
 * No tiene clave foránea hacia media_items a propósito: si el catálogo pierde temporalmente
 * el elemento (p. ej. con acceso parcial), la marca no se borra sola.
 */
@Entity(tableName = "pending_deletions", indices = [Index(value = ["markedAt"])])
data class PendingDeletionEntity(
    @PrimaryKey val mediaId: Long,
    val contentUri: String,
    val mediaType: MediaType,
    val displayName: String?,
    val sizeBytes: Long?,
    val sortDateMs: Long,
    val markedAt: Long,
    val source: DeletionSource,
)

enum class ActionType {
    /** Se añadieron elementos a la papelera. Deshacer = quitarlos. */
    MARK,

    /** Se quitaron elementos de la papelera. Deshacer = volver a añadirlos. */
    UNMARK,

    /** Decisión en Deslizar. Deshacer = borrar la decisión y revertir el cambio en la papelera. */
    REVIEW,
}

/** Cambio que una decisión de Deslizar hizo en la papelera (se guarda como entero). */
object TrashChange {
    const val NONE = 0
    const val ADDED = 1
    const val REMOVED = 2
}

/** Historial de acciones para "Deshacer". Sobrevive al cierre de la app. */
@Entity(tableName = "action_log")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ActionType,
    val createdAt: Long,
    val itemCount: Int,
)

/** Copia de cada elemento afectado por una acción, suficiente para revertirla. */
@Entity(
    tableName = "action_items",
    primaryKeys = ["actionId", "mediaId"],
    foreignKeys = [
        ForeignKey(
            entity = ActionLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["actionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["mediaId"])],
)
data class ActionItemEntity(
    val actionId: Long,
    val mediaId: Long,
    val contentUri: String,
    val mediaType: MediaType,
    val displayName: String?,
    val sizeBytes: Long?,
    val sortDateMs: Long,
    val markedAt: Long,
    val source: DeletionSource,
    /** Solo en acciones REVIEW: la decisión tomada. */
    val reviewDecision: ReviewDecision? = null,
    /** Solo en acciones REVIEW: [TrashChange] que hay que revertir al deshacer. */
    @ColumnInfo(defaultValue = "0") val trashChange: Int = TrashChange.NONE,
)

/**
 * Una solicitud de eliminación enviada a Android. Se guarda antes de mostrar el diálogo del
 * sistema para poder verificar el resultado aunque Android cierre la app mientras tanto.
 * [completedAt] == null significa que aún no se ha verificado.
 */
@Entity(tableName = "deletion_batches")
data class DeletionBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val mode: DeletionMode,
    val requestedCount: Int,
    val requestedKnownBytes: Long,
    /** Ya no existían al preparar la solicitud (se quitaron de la cola). */
    val skippedMissing: Int,
    /** No visibles por acceso parcial (siguen en la cola). */
    val skippedInaccessible: Int,
    /** Quedaron fuera por el límite de elementos por solicitud. */
    val remainingAfterLimit: Int,
    val resultCode: Int? = null,
    val deletedCount: Int = 0,
    val deletedKnownBytes: Long = 0,
    val deletedUnknownSizeCount: Int = 0,
    val stillPresentCount: Int = 0,
    val completedAt: Long? = null,
)

@Entity(
    tableName = "deletion_batch_items",
    primaryKeys = ["batchId", "mediaId"],
    foreignKeys = [
        ForeignKey(
            entity = DeletionBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DeletionBatchItemEntity(
    val batchId: Long,
    val mediaId: Long,
    val mediaType: MediaType,
    val sizeBytes: Long?,
)
