package com.example.cleanup.deletion

import com.example.cleanup.settings.DeletionMode

/** Elemento de una solicitud de eliminación, con el tamaño que tenía al pedirla. */
data class BatchItem(val mediaId: Long, val sizeBytes: Long?)

data class ReconcileOutcome(
    val confirmedDeleted: List<Long>,
    val stillPresent: List<Long>,
    val deletedKnownBytes: Long,
    val deletedUnknownSizeCount: Int,
)

/**
 * Decide qué se eliminó de verdad comparando lo pedido con lo que MediaStore sigue mostrando
 * DESPUÉS de la respuesta de Android. No se fía del código de resultado: un "OK" no garantiza
 * que se borraran todos, y un "cancelado" no garantiza que no se borrara ninguno.
 */
object DeletionReconciler {
    fun reconcile(items: List<BatchItem>, presentIds: Set<Long>): ReconcileOutcome {
        val (present, gone) = items.partition { it.mediaId in presentIds }
        return ReconcileOutcome(
            confirmedDeleted = gone.map { it.mediaId },
            stillPresent = present.map { it.mediaId },
            deletedKnownBytes = gone.sumOf { it.sizeBytes ?: 0L },
            deletedUnknownSizeCount = gone.count { it.sizeBytes == null },
        )
    }
}

/**
 * Resumen que ve el usuario tras una eliminación.
 * [systemAccepted]: true si Android devolvió OK, false si se canceló o rechazó, null si no hubo
 * respuesta (la app se cerró y se verificó después).
 */
data class DeletionReport(
    val mode: DeletionMode,
    val deleted: Int,
    val deletedKnownBytes: Long,
    val deletedUnknownSizeCount: Int,
    val stillPresent: Int,
    val systemAccepted: Boolean?,
    val skippedMissing: Int,
    val skippedInaccessible: Int,
    val remainingAfterLimit: Int,
) {
    /** Android dijo OK pero el archivo sigue ahí. */
    val failed: Int get() = if (systemAccepted == true) stillPresent else 0

    /** Cancelado por el usuario o sin respuesta: siguen en el teléfono y en la papelera. */
    val kept: Int get() = if (systemAccepted == true) 0 else stillPresent
}
