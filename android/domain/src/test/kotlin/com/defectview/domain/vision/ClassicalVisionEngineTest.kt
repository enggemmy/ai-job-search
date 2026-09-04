package com.defectview.domain.vision

import com.defectview.domain.model.EngineType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClassicalVisionEngineTest {

    private val engine = ClassicalVisionEngine(gridSize = 4)

    private fun solidImage(width: Int, height: Int, argb: Int): ImageSample =
        ImageSample(width, height, IntArray(width * height) { argb })

    private fun grayArgb(value: Int): Int {
        val v = value.coerceIn(0, 255)
        return (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }

    @Test
    fun `engine identifies itself as classical CV never a trained model`() {
        assertEquals(EngineType.CLASSICAL_CV, engine.info.engineType)
    }

    @Test
    fun `a perfectly uniform photo produces no detections`() = runTest {
        val image = solidImage(400, 400, grayArgb(128))
        val detections = engine.analyze(image)
        assertTrue(detections.isEmpty(), "uniform image should not trigger any outlier tile")
    }

    @Test
    fun `too small an image is skipped rather than misanalyzed`() = runTest {
        val tiny = solidImage(4, 4, grayArgb(128))
        assertTrue(engine.analyze(tiny).isEmpty())
    }

    @Test
    fun `a locally noisy tile against an otherwise uniform photo is flagged`() = runTest {
        val width = 400
        val height = 400
        val pixels = IntArray(width * height) { grayArgb(128) }

        // Tile (1,1) of a 4x4 grid spans roughly x in [100,200), y in [100,200).
        // Paint a high-contrast checkerboard there - a real, deliberate edge signal, not noise
        // dressed up as a defect: every other pixel flips between near-black and near-white.
        for (y in 100 until 200) {
            for (x in 100 until 200) {
                val checker = (x / 4 + y / 4) % 2 == 0
                pixels[y * width + x] = grayArgb(if (checker) 10 else 245)
            }
        }

        val detections = engine.analyze(ImageSample(width, height, pixels))

        assertTrue(detections.isNotEmpty(), "the checkerboard tile should stand out against the uniform background")
        val hit = detections.first()
        assertEquals("cracks", hit.defectCategoryHint, "extreme edge density should map to the edge-dominant hint")
        assertTrue(hit.confidenceScore <= ClassicalVisionEngine.CLASSICAL_CV_MAX_CONFIDENCE)
        assertTrue(hit.confidenceScore > 0f)
        assertTrue(hit.evidence.contains("Edge density"))
        // Bounding box should land on the tile we actually perturbed (grid cell 1,1 of 4x4 -> 0.25..0.5).
        assertTrue(hit.boundingBox.left in 0.2f..0.3f)
        assertTrue(hit.boundingBox.top in 0.2f..0.3f)
    }

    @Test
    fun `confidence never claims full certainty from a classical heuristic`() = runTest {
        val width = 200
        val height = 200
        val pixels = IntArray(width * height) { grayArgb(128) }
        for (y in 0 until 50) {
            for (x in 0 until 50) {
                pixels[y * width + x] = grayArgb(if ((x + y) % 2 == 0) 0 else 255)
            }
        }
        val detections = engine.analyze(ImageSample(width, height, pixels))
        detections.forEach { assertTrue(it.confidenceScore <= 0.8f) }
    }
}
