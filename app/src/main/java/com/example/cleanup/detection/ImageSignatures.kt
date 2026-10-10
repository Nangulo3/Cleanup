package com.example.cleanup.detection

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** Firmas visuales de una imagen. */
class ImageSignature(
    val phash: Long,
    val dhash: Long,
    val d256: LongArray,
    val lowInformation: Boolean,
    val sharpness: Float,
)

/**
 * Hashes perceptuales en Kotlin puro (sin Android) para poder probarlos en la JVM.
 * Trabajan sobre la luminancia de una miniatura pequeña, así que dos fotos casi iguales
 * (recomprimidas, redimensionadas, con pequeños cambios) dan hashes cercanos.
 *
 * - pHash: transformada del coseno (DCT) de una versión de 32×32; describe la estructura general.
 * - dHash: compara cada píxel con su vecino; describe hacia dónde cambia el brillo.
 * - dHash de 256 bits: igual pero con más detalle, útil para capturas donde la interfaz se repite
 *   y lo que cambia es el texto.
 *
 * Al usar luminancia, dos fotos con colores parecidos pero distinta estructura NO se parecen.
 */
object ImageSignatures {

    /** Subir este número fuerza a recalcular todas las firmas. */
    const val VERSION = 1

    /** Desviación típica (0–255) por debajo de la cual una imagen se considera casi uniforme. */
    const val LOW_INFORMATION_STD = 8f

    fun compute(argb: IntArray, width: Int, height: Int): ImageSignature {
        require(width > 0 && height > 0 && argb.size >= width * height)
        val gray = luma(argb, width, height)
        val g32 = resizeArea(gray, width, height, 32, 32)
        return ImageSignature(
            phash = pHash(g32),
            dhash = dHash64(resizeArea(gray, width, height, 9, 8)),
            d256 = dHash256(resizeArea(gray, width, height, 17, 16)),
            lowInformation = stdDev(g32) < LOW_INFORMATION_STD,
            sharpness = laplacianVariance(resizeArea(gray, width, height, 64, 64), 64, 64),
        )
    }

    /** Luminancia (0–255) según ITU-R BT.601. */
    fun luma(argb: IntArray, width: Int, height: Int): FloatArray {
        val out = FloatArray(width * height)
        for (i in out.indices) {
            val c = argb[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            out[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        return out
    }

    /** Reduce por promedio de áreas (evita el aliasing de tomar píxeles sueltos). */
    fun resizeArea(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        val out = FloatArray(dw * dh)
        for (dy in 0 until dh) {
            val y0 = dy * sh / dh
            val y1 = maxOf(y0 + 1, (dy + 1) * sh / dh)
            for (dx in 0 until dw) {
                val x0 = dx * sw / dw
                val x1 = maxOf(x0 + 1, (dx + 1) * sw / dw)
                var sum = 0f
                var count = 0
                for (y in y0 until minOf(y1, sh)) {
                    val row = y * sw
                    for (x in x0 until minOf(x1, sw)) {
                        sum += src[row + x]
                        count++
                    }
                }
                out[dy * dw + dx] = if (count == 0) 0f else sum / count
            }
        }
        return out
    }

    // Tabla de cosenos para la DCT de 32 puntos, solo las 8 primeras frecuencias.
    private val DCT_COS: Array<DoubleArray> = Array(8) { u ->
        DoubleArray(32) { x -> cos((2 * x + 1) * u * PI / 64.0) }
    }

    /** pHash: DCT separable de 32×32, frecuencias 8×8 más bajas, bit = coeficiente > mediana. */
    fun pHash(gray32: FloatArray): Long {
        require(gray32.size == 32 * 32)
        // Filas: temp[y][u] = Σx p[y][x]·cos(u, x)
        val temp = Array(32) { DoubleArray(8) }
        for (y in 0 until 32) {
            for (u in 0 until 8) {
                var sum = 0.0
                val c = DCT_COS[u]
                for (x in 0 until 32) sum += gray32[y * 32 + x] * c[x]
                temp[y][u] = sum
            }
        }
        // Columnas: coeff[v][u] = Σy temp[y][u]·cos(v, y)
        val coeffs = DoubleArray(64)
        for (v in 0 until 8) {
            val c = DCT_COS[v]
            for (u in 0 until 8) {
                var sum = 0.0
                for (y in 0 until 32) sum += temp[y][u] * c[y]
                coeffs[v * 8 + u] = sum
            }
        }
        val median = coeffs.sortedArray().let { (it[31] + it[32]) / 2.0 }
        var hash = 0L
        for (i in 0 until 64) if (coeffs[i] > median) hash = hash or (1L shl i)
        return hash
    }

    /** dHash de 64 bits sobre una imagen de 9×8: bit = el píxel es más oscuro que el de su derecha. */
    fun dHash64(gray9x8: FloatArray): Long {
        require(gray9x8.size == 9 * 8)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (gray9x8[y * 9 + x] < gray9x8[y * 9 + x + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    /** dHash de 256 bits sobre 17×16, en cuatro Long de 64 bits. */
    fun dHash256(gray17x16: FloatArray): LongArray {
        require(gray17x16.size == 17 * 16)
        val parts = LongArray(4)
        var bit = 0
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                if (gray17x16[y * 17 + x] < gray17x16[y * 17 + x + 1]) {
                    parts[bit / 64] = parts[bit / 64] or (1L shl (bit % 64))
                }
                bit++
            }
        }
        return parts
    }

    fun stdDev(values: FloatArray): Float {
        val mean = values.average()
        var acc = 0.0
        for (v in values) acc += (v - mean) * (v - mean)
        return sqrt(acc / values.size).toFloat()
    }

    /** Varianza del laplaciano: alta en fotos nítidas, baja en fotos movidas o desenfocadas. */
    fun laplacianVariance(gray: FloatArray, width: Int, height: Int): Float {
        if (width < 3 || height < 3) return 0f
        val values = FloatArray((width - 2) * (height - 2))
        var i = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val c = gray[y * width + x]
                values[i++] = gray[(y - 1) * width + x] + gray[(y + 1) * width + x] +
                    gray[y * width + x - 1] + gray[y * width + x + 1] - 4 * c
            }
        }
        val sd = stdDev(values)
        return sd * sd
    }

    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    fun hamming(a: LongArray, b: LongArray): Int {
        var d = 0
        for (i in a.indices) d += java.lang.Long.bitCount(a[i] xor b[i])
        return d
    }
}
