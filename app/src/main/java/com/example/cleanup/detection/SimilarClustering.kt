package com.example.cleanup.detection

import com.example.cleanup.settings.SimilarityLevel

/** Firma de una foto lista para comparar. */
class PhotoPoint(
    val id: Long,
    val timeMs: Long,
    val phash: Long,
    val dhash: Long,
    val d256: LongArray?,
    val lowInformation: Boolean,
)

/** Un grupo encontrado: ids y la mayor distancia entre dos miembros. */
data class Cluster(val ids: List<Long>, val maxDistance: Int)

/**
 * Umbrales por nivel. Distancias de Hamming sobre hashes de 64 bits (256 para capturas).
 * Se calibran con fotos reales; ver README.
 */
data class SimilarityThresholds(
    /** Fotos: pHash máximo para considerarlas parecidas (pasada por ventana de tiempo). */
    val photoPhash: Int,
    /** Fotos: dHash máximo; deben cumplirse los dos para evitar coincidencias por casualidad. */
    val photoDhash: Int,
    /** Fotos: solo se comparan con la pasada normal si se tomaron con menos de esta diferencia. */
    val windowMs: Long,
    /** Fotos: pHash máximo para la pasada global (cualquier fecha); muy estricto, ≤ 3. */
    val globalPhash: Int,
    /** Capturas: dHash de 256 bits máximo. */
    val screenshotD256: Int,
    /** Capturas: pHash máximo (filtro adicional). */
    val screenshotPhash: Int,
) {
    companion object {
        private const val HOUR = 3_600_000L

        fun forLevel(level: SimilarityLevel): SimilarityThresholds = when (level) {
            SimilarityLevel.STRICT -> SimilarityThresholds(6, 8, 2 * HOUR, 2, 12, 10)
            SimilarityLevel.NORMAL -> SimilarityThresholds(10, 12, 6 * HOUR, 3, 20, 14)
            SimilarityLevel.BROAD -> SimilarityThresholds(14, 16, 24 * HOUR, 3, 30, 18)
        }
    }
}

/**
 * Agrupa fotos parecidas sin encadenamiento: un elemento entra a un grupo solo si se parece a
 * TODOS sus miembros (enlace completo). Así A~B y B~C no meten a A y C juntos si no se parecen.
 *
 * Fotos: pares candidatos por cercanía en el tiempo (misma escena) más una pasada global muy
 * estricta para copias casi idénticas tomadas o guardadas en otra fecha.
 * Capturas: todas contra todas con el hash de 256 bits (la interfaz de la app pesa mucho en
 * los hashes de 64 bits, así que se necesita más detalle para distinguir textos distintos).
 */
object SimilarClustering {

    /** Cubetas de la pasada global más grandes que esto se omiten (imágenes muy genéricas). */
    private const val MAX_BUCKET = 2_000

    fun clusterPhotos(points: List<PhotoPoint>, t: SimilarityThresholds): List<Cluster> {
        val usable = points.filterNot { it.lowInformation }
        val byId = usable.associateBy { it.id }
        val pairs = HashMap<Pair<Long, Long>, Int>()

        fun accept(a: PhotoPoint, b: PhotoPoint) =
            ImageSignatures.hamming(a.phash, b.phash) <= t.photoPhash &&
                ImageSignatures.hamming(a.dhash, b.dhash) <= t.photoDhash

        // 1. Ventana de tiempo.
        val sorted = usable.sortedBy { it.timeMs }
        for (i in sorted.indices) {
            val a = sorted[i]
            var j = i + 1
            while (j < sorted.size && sorted[j].timeMs - a.timeMs <= t.windowMs) {
                val b = sorted[j]
                if (accept(a, b)) pairs[key(a.id, b.id)] = ImageSignatures.hamming(a.phash, b.phash)
                j++
            }
        }

        // 2. Pasada global estricta con índice por fragmentos: si dos hashes de 64 bits difieren en
        //    ≤ 3 bits, al menos uno de sus 4 fragmentos de 16 bits es idéntico (principio del palomar).
        val buckets = HashMap<Long, MutableList<PhotoPoint>>()
        for (p in usable) {
            for (chunk in 0 until 4) {
                val value = (p.phash ushr (chunk * 16)) and 0xFFFF
                buckets.getOrPut(chunk.toLong() shl 16 or value) { mutableListOf() } += p
            }
        }
        for (bucket in buckets.values) {
            if (bucket.size < 2 || bucket.size > MAX_BUCKET) continue
            for (i in bucket.indices) for (j in i + 1 until bucket.size) {
                val a = bucket[i]
                val b = bucket[j]
                val d = ImageSignatures.hamming(a.phash, b.phash)
                if (d <= t.globalPhash && ImageSignatures.hamming(a.dhash, b.dhash) <= t.photoDhash) {
                    pairs[key(a.id, b.id)] = d
                }
            }
        }

        return completeLinkage(pairs, accept = { x, y -> accept(byId.getValue(x), byId.getValue(y)) }) { x, y ->
            ImageSignatures.hamming(byId.getValue(x).phash, byId.getValue(y).phash)
        }
    }

    fun clusterScreenshots(points: List<PhotoPoint>, t: SimilarityThresholds): List<Cluster> {
        val usable = points.filter { !it.lowInformation && it.d256 != null }
        val byId = usable.associateBy { it.id }
        fun accept(a: PhotoPoint, b: PhotoPoint) =
            ImageSignatures.hamming(a.d256!!, b.d256!!) <= t.screenshotD256 &&
                ImageSignatures.hamming(a.phash, b.phash) <= t.screenshotPhash

        val pairs = HashMap<Pair<Long, Long>, Int>()
        for (i in usable.indices) for (j in i + 1 until usable.size) {
            val a = usable[i]
            val b = usable[j]
            if (accept(a, b)) pairs[key(a.id, b.id)] = ImageSignatures.hamming(a.d256!!, b.d256!!)
        }
        return completeLinkage(pairs, accept = { x, y -> accept(byId.getValue(x), byId.getValue(y)) }) { x, y ->
            ImageSignatures.hamming(byId.getValue(x).d256!!, byId.getValue(y).d256!!)
        }
    }

    /**
     * Enlace completo voraz: recorre los pares del más parecido al menos parecido y une grupos
     * solo si todos los miembros quedan dentro del umbral entre sí.
     */
    fun completeLinkage(
        pairs: Map<Pair<Long, Long>, Int>,
        accept: (Long, Long) -> Boolean,
        distance: (Long, Long) -> Int,
    ): List<Cluster> {
        val clusterOf = HashMap<Long, MutableList<Long>>()
        val ordered = pairs.entries.sortedWith(compareBy({ it.value }, { it.key.first }, { it.key.second }))
        for ((pair, _) in ordered) {
            val (a, b) = pair
            val ca = clusterOf[a]
            val cb = clusterOf[b]
            when {
                ca == null && cb == null -> {
                    val c = mutableListOf(a, b)
                    clusterOf[a] = c
                    clusterOf[b] = c
                }
                ca != null && cb == null -> if (ca.all { accept(it, b) }) { ca += b; clusterOf[b] = ca }
                ca == null && cb != null -> if (cb.all { accept(it, a) }) { cb += a; clusterOf[a] = cb }
                ca != null && cb != null && ca !== cb ->
                    if (ca.all { x -> cb.all { y -> accept(x, y) } }) {
                        ca += cb
                        cb.forEach { clusterOf[it] = ca }
                    }
            }
        }
        val unique = LinkedHashSet<List<Long>>()
        clusterOf.values.forEach { unique += it }
        return unique.filter { it.size >= 2 }.map { ids ->
            var max = 0
            for (i in ids.indices) for (j in i + 1 until ids.size) max = maxOf(max, distance(ids[i], ids[j]))
            Cluster(ids.sorted(), max)
        }
    }

    private fun key(a: Long, b: Long): Pair<Long, Long> = if (a < b) a to b else b to a
}
