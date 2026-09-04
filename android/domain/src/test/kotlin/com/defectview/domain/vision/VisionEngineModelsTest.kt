package com.defectview.domain.vision

import com.defectview.domain.model.BoundingBox
import com.defectview.domain.model.DefectDetection
import com.defectview.domain.model.Trade
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class VisionEngineModelsTest {

    @Test
    fun `BoundingBox rejects coordinates outside the 0 to 1 range`() {
        assertThrows(IllegalArgumentException::class.java) {
            BoundingBox(left = -0.1f, top = 0f, right = 0.5f, bottom = 0.5f)
        }
    }

    @Test
    fun `BoundingBox rejects non-positive width or height`() {
        assertThrows(IllegalArgumentException::class.java) {
            BoundingBox(left = 0.5f, top = 0.5f, right = 0.5f, bottom = 0.6f)
        }
    }

    @Test
    fun `DefectDetection rejects out-of-range confidence`() {
        val box = BoundingBox(0.1f, 0.1f, 0.4f, 0.4f)
        assertThrows(IllegalArgumentException::class.java) {
            DefectDetection("cracks", Trade.MASONRY, box, confidenceScore = 1.2f, evidence = "edge density")
        }
    }

    @Test
    fun `ImageSample rejects mismatched pixel buffer size`() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageSample(width = 2, height = 2, pixels = IntArray(3))
        }
    }
}
