package com.example.cleanup.worker

import com.example.cleanup.data.db.AppDatabase
import com.example.cleanup.data.db.dao.NewSimilarGroup
import com.example.cleanup.data.db.dao.SimilarityInput
import com.example.cleanup.data.db.dao.VideoInput
import com.example.cleanup.data.db.entity.SimilarGroupEntity
import com.example.cleanup.data.db.entity.SimilarKind
import com.example.cleanup.detection.Cluster
import com.example.cleanup.detection.ImageSignatures
import com.example.cleanup.detection.PhotoPoint
import com.example.cleanup.detection.SimilarClustering
import com.example.cleanup.detection.SimilarityThresholds
import com.example.cleanup.detection.VideoPoint
import com.example.cleanup.detection.VideoSignatures
import com.example.cleanup.detection.VideoThresholds
import com.example.cleanup.settings.SettingsRepository
import com.example.cleanup.settings.SimilarityLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Última fase del escaneo: agrupa fotos, capturas y videos similares con las firmas ya calculadas y
 * reemplaza los grupos guardados. Es rápida (solo compara números), así que se rehace siempre.
 */
class SimilarGrouper(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(onProgress: suspend (ScanProgressInfo) -> Unit) {
        onProgress(ScanProgressInfo(ScanPhase.GROUPING, 0, null))
        val level = settings.settings.first().similarityLevel
        val inputs = db.similarDao().similarityInputs(ImageSignatures.VERSION)
        val videoInputs = db.similarDao().videoInputs(VideoSignatures.VERSION)
        val now = clock()
        val groups = withContext(Dispatchers.Default) {
            buildGroups(inputs, level, now) + buildVideoGroups(videoInputs, level, now)
        }
        db.similarDao().replaceGroups(listOf(SimilarKind.PHOTO, SimilarKind.SCREENSHOT, SimilarKind.VIDEO), groups)
    }

    companion object {
        const val ALGORITHM_VERSION = 1

        /** Lógica pura (testeable): colapsa duplicados exactos, agrupa y elige la sugerencia. */
        fun buildGroups(inputs: List<SimilarityInput>, level: SimilarityLevel, now: Long): List<NewSimilarGroup> {
            // Los duplicados exactos ya tienen su propia categoría: aquí cuentan como una sola foto.
            val collapsed = inputs
                .groupBy { it.sha256 ?: "id:${it.id}" }
                .values
                .map { copies -> copies.minBy { it.id } }
            val byId = collapsed.associateBy { it.id }
            val thresholds = SimilarityThresholds.forLevel(level)
            val (screens, photos) = collapsed.partition { it.isScreenshot }

            val photoClusters = SimilarClustering.clusterPhotos(photos.map { it.toPoint() }, thresholds)
            val screenClusters = SimilarClustering.clusterScreenshots(screens.map { it.toPoint() }, thresholds)

            fun toGroup(cluster: Cluster, kind: SimilarKind) = NewSimilarGroup(
                group = SimilarGroupEntity(
                    kind = kind,
                    level = level,
                    algorithmVersion = ALGORITHM_VERSION,
                    suggestedId = suggest(cluster.ids.mapNotNull { byId[it] }),
                    maxDistance = cluster.maxDistance,
                    createdAt = now,
                ),
                memberIds = cluster.ids,
            )
            return photoClusters.map { toGroup(it, SimilarKind.PHOTO) } +
                screenClusters.map { toGroup(it, SimilarKind.SCREENSHOT) }
        }

        /** Videos: colapsa duplicados exactos, compara secuencias de fotogramas y sugiere el de más calidad. */
        fun buildVideoGroups(inputs: List<VideoInput>, level: SimilarityLevel, now: Long): List<NewSimilarGroup> {
            val collapsed = inputs
                .groupBy { it.sha256 ?: "id:${it.id}" }
                .values
                .map { copies -> copies.minBy { it.id } }
            val byId = collapsed.associateBy { it.id }
            val clusters = VideoSignatures.cluster(collapsed.map { it.toVideoPoint() }, VideoThresholds.forLevel(level))
            return clusters.map { cluster ->
                NewSimilarGroup(
                    group = SimilarGroupEntity(
                        kind = SimilarKind.VIDEO,
                        level = level,
                        algorithmVersion = ALGORITHM_VERSION,
                        suggestedId = suggestVideo(cluster.ids.mapNotNull { byId[it] }),
                        maxDistance = cluster.maxDistance,
                        createdAt = now,
                    ),
                    memberIds = cluster.ids,
                )
            }
        }

        /** Sugerencia para videos: mayor resolución, luego mayor tasa de datos (calidad), luego el más reciente. */
        fun suggestVideo(members: List<VideoInput>): Long? = members.maxWithOrNull(
            compareBy<VideoInput> { (it.width ?: 0).toLong() * (it.height ?: 0) }
                .thenBy { (it.sizeBytes ?: 0L).toDouble() / it.durationMs.coerceAtLeast(1) }
                .thenBy { it.sortDateMs }
                .thenByDescending { it.id }
        )?.id

        private fun VideoInput.toVideoPoint(): VideoPoint {
            val w = width
            val h = height
            val aspect = if (w != null && h != null && w > 0 && h > 0) {
                if (orientation == 90 || orientation == 270) h.toFloat() / w else w.toFloat() / h
            } else {
                null
            }
            return VideoPoint(
                id = id,
                durationMs = durationMs,
                aspect = aspect,
                phashes = VideoSignatures.fromBytes(framePhashes),
                dhashes = VideoSignatures.fromBytes(frameDhashes),
                lowInfoMask = lowInfoMask,
            )
        }

        /** Sugerencia opcional: la más nítida; a igualdad, la de mayor resolución, luego la más reciente. */
        fun suggest(members: List<SimilarityInput>): Long? = members.maxWithOrNull(
            compareBy<SimilarityInput> { it.sharpness ?: 0f }
                .thenBy { (it.width ?: 0).toLong() * (it.height ?: 0) }
                .thenBy { it.sortDateMs }
                .thenByDescending { it.id }
        )?.id

        private fun SimilarityInput.toPoint() = PhotoPoint(
            id = id,
            timeMs = sortDateMs,
            phash = phash,
            dhash = dhash,
            d256 = if (d256a != null && d256b != null && d256c != null && d256d != null) {
                longArrayOf(d256a, d256b, d256c, d256d)
            } else {
                null
            },
            lowInformation = lowInformation,
        )
    }
}
