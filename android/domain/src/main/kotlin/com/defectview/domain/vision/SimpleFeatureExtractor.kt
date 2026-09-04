package com.defectview.domain.vision

/**
 * Produces a small, hand-crafted feature vector from an image region for nearest-neighbor
 * similarity search over verified examples (spec section 4C: "local image embeddings or feature
 * vectors" as the practical first version, explicitly distinct from a learned neural embedding -
 * see [com.defectview.domain.learning.SimilaritySearch] for the retrieval side and its own
 * doc comment on why this is not the same thing as model retraining).
 *
 * The vector is deterministic and cheap: overall brightness and contrast, edge density (reusing
 * the same simple gradient signal as [ClassicalVisionEngine]), aspect ratio, and a coarse
 * 4-bucket luminance histogram - eight dimensions total, each roughly normalized to 0f..1f so
 * cosine similarity behaves sensibly across examples of different sizes.
 */
object SimpleFeatureExtractor {

    const val DIMENSIONS = 8
    private const val EDGE_THRESHOLD = 30f

    fun extract(image: ImageSample): FloatArray {
        val luminance = FloatArray(image.pixels.size)
        var sum = 0.0
        val histogram = IntArray(4)

        for (i in image.pixels.indices) {
            val p = image.pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val v = 0.299f * r + 0.587f * g + 0.114f * b
            luminance[i] = v
            sum += v
            histogram[(v / 64f).toInt().coerceIn(0, 3)]++
        }

        val mean = (sum / luminance.size).toFloat()
        var variance = 0.0
        var edgeCount = 0
        var pairCount = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val idx = y * image.width + x
                val v = luminance[idx]
                variance += (v - mean).toDouble() * (v - mean).toDouble()
                if (x + 1 < image.width) {
                    if (kotlin.math.abs(v - luminance[idx + 1]) > EDGE_THRESHOLD) edgeCount++
                    pairCount++
                }
                if (y + 1 < image.height) {
                    if (kotlin.math.abs(v - luminance[idx + image.width]) > EDGE_THRESHOLD) edgeCount++
                    pairCount++
                }
            }
        }
        val stdDev = kotlin.math.sqrt(variance / luminance.size).toFloat()
        val edgeDensity = if (pairCount == 0) 0f else edgeCount.toFloat() / pairCount
        val aspectRatio = image.width.toFloat() / image.height.toFloat()
        val normalizedAspect = (aspectRatio / (aspectRatio + 1f)) // squashes (0, inf) into (0, 1)

        val total = luminance.size.toFloat().coerceAtLeast(1f)
        return floatArrayOf(
            (mean / 255f).coerceIn(0f, 1f),
            (stdDev / 128f).coerceIn(0f, 1f),
            edgeDensity.coerceIn(0f, 1f),
            normalizedAspect,
            histogram[0] / total,
            histogram[1] / total,
            histogram[2] / total,
            histogram[3] / total
        )
    }
}
