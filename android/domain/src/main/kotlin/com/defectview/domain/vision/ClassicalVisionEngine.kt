package com.defectview.domain.vision

import com.defectview.domain.model.AiEngineInfo
import com.defectview.domain.model.BoundingBox
import com.defectview.domain.model.DefectDetection
import com.defectview.domain.model.EngineType
import com.defectview.domain.model.Trade
import kotlin.math.sqrt

/**
 * A real, deterministic local vision engine using classical image-processing signals - NOT a
 * trained neural network, and it never claims to be one (EngineType.CLASSICAL_CV). It looks for
 * tiles of the photo whose edge density or color variance is a statistical outlier relative to
 * the rest of the image, on the premise that irregular workmanship (cracks, uneven plaster,
 * inconsistent finishing) shows up as local irregularity against an otherwise consistent
 * surface. This is a coarse, explainable heuristic - it flags "something is locally unusual
 * here," not a specific defect classification, which is exactly why every result carries a
 * confidence capped well below certainty and an [DefectDetection.evidence] string with the real
 * numbers behind the flag, never a fabricated explanation.
 *
 * Serves as the default/fallback engine and as the reference implementation of [VisionEngine]
 * until a trained LiteRT/ONNX model is available (spec section 3/4A) - see the engine
 * abstraction in [VisionEngine].
 */
class ClassicalVisionEngine(
    private val gridSize: Int = 4,
    private val significanceThreshold: Float = 1.8f
) : VisionEngine {

    override val info = AiEngineInfo(
        modelName = "Classical CV Heuristics",
        modelVersion = "1.0.0",
        engineType = EngineType.CLASSICAL_CV
    )

    override suspend fun analyze(image: ImageSample): List<DefectDetection> {
        if (image.width < gridSize * 4 || image.height < gridSize * 4) return emptyList()

        val gray = toLuminance(image)
        val tiles = buildTileMetrics(gray, image.width, image.height, gridSize)
        if (tiles.isEmpty()) return emptyList()

        val avgEdgeDensity = tiles.map { it.edgeDensity }.average().toFloat().coerceAtLeast(EPSILON)
        val avgVariance = tiles.map { it.variance }.average().toFloat().coerceAtLeast(EPSILON)

        return tiles.mapNotNull { tile ->
            val edgeRatio = tile.edgeDensity / avgEdgeDensity
            val varianceRatio = tile.variance / avgVariance
            val dominantRatio = maxOf(edgeRatio, varianceRatio)
            if (dominantRatio < significanceThreshold) return@mapNotNull null

            val (hint, trade) = classify(edgeRatio, varianceRatio)
            val confidence = confidenceFor(dominantRatio)
            val evidence = "Edge density %.1fx image average, color variance %.1fx image average (tile %d,%d of %dx%d grid)"
                .format(edgeRatio, varianceRatio, tile.gridX, tile.gridY, gridSize, gridSize)

            DefectDetection(
                defectCategoryHint = hint,
                trade = trade,
                boundingBox = tile.toBoundingBox(gridSize),
                confidenceScore = confidence,
                evidence = evidence
            )
        }
    }

    private fun classify(edgeRatio: Float, varianceRatio: Float): Pair<String, Trade> = when {
        edgeRatio >= varianceRatio && edgeRatio >= 2.5f -> "cracks" to Trade.MASONRY
        edgeRatio >= varianceRatio -> "uneven_plaster" to Trade.MASONRY
        varianceRatio > edgeRatio && edgeRatio >= significanceThreshold -> "poor_finishing" to Trade.MASONRY
        else -> "possible_workmanship_issue" to Trade.GENERAL
    }

    /** Classical heuristics never reach full certainty - capped so the UI can never legitimately
     * claim more than "review recommended" territory from this engine alone (spec section 7). */
    private fun confidenceFor(dominantRatio: Float): Float {
        val normalized = ((dominantRatio - 1f).coerceIn(0f, 4f) / 4f)
        return (0.2f + normalized * 0.6f).coerceIn(0.2f, CLASSICAL_CV_MAX_CONFIDENCE)
    }

    private fun toLuminance(image: ImageSample): FloatArray {
        val out = FloatArray(image.pixels.size)
        for (i in image.pixels.indices) {
            val p = image.pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            out[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        return out
    }

    private data class TileMetrics(val gridX: Int, val gridY: Int, val edgeDensity: Float, val variance: Float) {
        fun toBoundingBox(gridSize: Int): BoundingBox {
            val step = 1f / gridSize
            val left = (gridX * step).coerceIn(0f, 1f)
            val top = (gridY * step).coerceIn(0f, 1f)
            val right = maxOf((gridX + 1) * step, left + MIN_BOX_SPAN).coerceAtMost(1f)
            val bottom = maxOf((gridY + 1) * step, top + MIN_BOX_SPAN).coerceAtMost(1f)
            return BoundingBox(left, top, right, bottom)
        }
    }

    private fun buildTileMetrics(gray: FloatArray, width: Int, height: Int, gridSize: Int): List<TileMetrics> {
        val tileW = width / gridSize
        val tileH = height / gridSize
        if (tileW < 2 || tileH < 2) return emptyList()

        val tiles = mutableListOf<TileMetrics>()
        for (gy in 0 until gridSize) {
            for (gx in 0 until gridSize) {
                val startX = gx * tileW
                val startY = gy * tileH
                val endX = if (gx == gridSize - 1) width else startX + tileW
                val endY = if (gy == gridSize - 1) height else startY + tileH

                var sum = 0.0
                var sumSq = 0.0
                var edgeCount = 0
                var pairCount = 0
                var count = 0

                for (y in startY until endY) {
                    for (x in startX until endX) {
                        val idx = y * width + x
                        val v = gray[idx]
                        sum += v
                        sumSq += v.toDouble() * v.toDouble()
                        count++

                        if (x + 1 < endX) {
                            val diff = kotlin.math.abs(v - gray[idx + 1])
                            if (diff > EDGE_THRESHOLD) edgeCount++
                            pairCount++
                        }
                        if (y + 1 < endY) {
                            val diff = kotlin.math.abs(v - gray[idx + width])
                            if (diff > EDGE_THRESHOLD) edgeCount++
                            pairCount++
                        }
                    }
                }

                if (count == 0) continue
                val mean = sum / count
                val variance = (sumSq / count) - (mean * mean)
                val edgeDensity = if (pairCount == 0) 0f else edgeCount.toFloat() / pairCount

                tiles.add(TileMetrics(gx, gy, edgeDensity, sqrt(variance.coerceAtLeast(0.0)).toFloat()))
            }
        }
        return tiles
    }

    companion object {
        private const val EDGE_THRESHOLD = 30f
        private const val EPSILON = 0.001f
        private const val MIN_BOX_SPAN = 0.01f
        const val CLASSICAL_CV_MAX_CONFIDENCE = 0.8f
    }
}
