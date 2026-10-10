package com.example.cleanup.detection

import com.example.cleanup.settings.SimilarityLevel
import kotlin.math.abs
import kotlin.math.ceil

/** Firma de un video lista para comparar. */
class VideoPoint(
    val id: Long,
    val durationMs: Long,
    /** Ancho/alto ya corregido por rotación; null si no se conoce. */
    val aspect: Float?,
    val phashes: LongArray,
    val dhashes: LongArray,
    val lowInfoMask: Int,
)

data class VideoThresholds(
    val framePhash: Int,
    val frameDhash: Int,
    /** Fracción mínima de fotogramas útiles que deben coincidir. */
    val minMatchRatio: Float,
    /** Diferencia de duración permitida: el mayor entre este porcentaje y [durationToleranceMs]. */
    val durationTolerancePct: Float,
    val durationToleranceMs: Long,
) {
    companion object {
        fun forLevel(level: SimilarityLevel): VideoThresholds = when (level) {
            SimilarityLevel.STRICT -> VideoThresholds(6, 8, 0.85f, 0.02f, 1_000)
            SimilarityLevel.NORMAL -> VideoThresholds(10, 12, 0.75f, 0.03f, 1_000)
            SimilarityLevel.BROAD -> VideoThresholds(14, 16, 0.6f, 0.05f, 2_000)
        }
    }
}

/**
 * Compara videos por secuencias de fotogramas, no por uno solo.
 *
 * Dos videos son similares si: duración parecida, misma proporción de imagen y suficientes
 * fotogramas alineados parecidos (pHash y dHash bajo el umbral). Los fotogramas casi uniformes
 * (fundidos a negro, pantallas en blanco) se ignoran; si quedan menos de [MIN_USEFUL_FRAMES]
 * útiles, no se agrupa para evitar falsos positivos.
 *
 * Los videos idénticos byte a byte se detectan aparte (Duplicados). Limitaciones: recortes,
 * ediciones, cambios de velocidad o videos reordenados no se reconocen.
 */
object VideoSignatures {

    const val VERSION = 1
    const val FRAME_COUNT = 8
    const val MIN_USEFUL_FRAMES = 3

    /** Posiciones relativas de los fotogramas: 10 %, 21 %, … 90 %. */
    val FRAME_POSITIONS: List<Double> = List(FRAME_COUNT) { i -> 0.1 + i * 0.8 / (FRAME_COUNT - 1) }

    fun durationsCompatible(a: Long, b: Long, t: VideoThresholds): Boolean {
        val longer = maxOf(a, b)
        val tolerance = maxOf(t.durationToleranceMs, (longer * t.durationTolerancePct).toLong())
        return abs(a - b) <= tolerance
    }

    fun similar(a: VideoPoint, b: VideoPoint, t: VideoThresholds): Boolean {
        if (!durationsCompatible(a.durationMs, b.durationMs, t)) return false
        if (a.aspect != null && b.aspect != null && abs(a.aspect - b.aspect) > 0.08f) return false
        val useful = usefulFrames(a, b)
        if (useful.size < MIN_USEFUL_FRAMES) return false
        val matches = useful.count { i ->
            ImageSignatures.hamming(a.phashes[i], b.phashes[i]) <= t.framePhash &&
                ImageSignatures.hamming(a.dhashes[i], b.dhashes[i]) <= t.frameDhash
        }
        return matches >= ceil(t.minMatchRatio * useful.size).toInt()
    }

    /** Distancia media de pHash en los fotogramas útiles (para mostrar qué tan parecidos son). */
    fun distance(a: VideoPoint, b: VideoPoint): Int {
        val useful = usefulFrames(a, b)
        if (useful.isEmpty()) return 64
        return useful.sumOf { ImageSignatures.hamming(a.phashes[it], b.phashes[it]) } / useful.size
    }

    /** Agrupa con enlace completo; solo compara videos de duración compatible (ordenados por duración). */
    fun cluster(points: List<VideoPoint>, t: VideoThresholds): List<Cluster> {
        val byId = points.associateBy { it.id }
        val sorted = points.sortedBy { it.durationMs }
        val pairs = HashMap<Pair<Long, Long>, Int>()
        for (i in sorted.indices) {
            var j = i + 1
            while (j < sorted.size && durationsCompatible(sorted[i].durationMs, sorted[j].durationMs, t)) {
                val a = sorted[i]
                val b = sorted[j]
                if (similar(a, b, t)) {
                    val key = if (a.id < b.id) a.id to b.id else b.id to a.id
                    pairs[key] = distance(a, b)
                }
                j++
            }
        }
        return SimilarClustering.completeLinkage(
            pairs,
            accept = { x, y -> similar(byId.getValue(x), byId.getValue(y), t) },
        ) { x, y -> distance(byId.getValue(x), byId.getValue(y)) }
    }

    private fun usefulFrames(a: VideoPoint, b: VideoPoint): List<Int> {
        val n = minOf(a.phashes.size, b.phashes.size, a.dhashes.size, b.dhashes.size)
        return (0 until n).filter { i -> (a.lowInfoMask shr i) and 1 == 0 && (b.lowInfoMask shr i) and 1 == 0 }
    }

    /** Convierte hashes a bytes para guardarlos en la base de datos (8 bytes por hash). */
    fun toBytes(values: LongArray): ByteArray {
        val out = ByteArray(values.size * 8)
        values.forEachIndexed { i, v ->
            for (b in 0 until 8) out[i * 8 + b] = (v ushr (56 - 8 * b)).toByte()
        }
        return out
    }

    fun fromBytes(bytes: ByteArray): LongArray = LongArray(bytes.size / 8) { i ->
        var v = 0L
        for (b in 0 until 8) v = (v shl 8) or (bytes[i * 8 + b].toLong() and 0xFF)
        v
    }
}
