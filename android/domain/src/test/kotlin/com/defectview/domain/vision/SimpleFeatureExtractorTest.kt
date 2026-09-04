package com.defectview.domain.vision

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimpleFeatureExtractorTest {

    private fun grayArgb(value: Int): Int {
        val v = value.coerceIn(0, 255)
        return (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }

    @Test
    fun `vector has the documented number of dimensions and stays in range`() {
        val image = ImageSample(20, 20, IntArray(400) { grayArgb(128) })
        val vector = SimpleFeatureExtractor.extract(image)
        assertEquals(SimpleFeatureExtractor.DIMENSIONS, vector.size)
        vector.forEach { assertTrue(it in 0f..1f, "component $it out of range") }
    }

    @Test
    fun `a uniform gray image has near-zero variance and edge density`() {
        val image = ImageSample(20, 20, IntArray(400) { grayArgb(128) })
        val vector = SimpleFeatureExtractor.extract(image)
        assertTrue(vector[1] < 0.05f, "stddev component should be near zero for a flat image")
        assertTrue(vector[2] < 0.05f, "edge density component should be near zero for a flat image")
    }

    @Test
    fun `a checkerboard has high edge density`() {
        val width = 20
        val height = 20
        val pixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            grayArgb(if ((x + y) % 2 == 0) 10 else 245)
        }
        val vector = SimpleFeatureExtractor.extract(ImageSample(width, height, pixels))
        assertTrue(vector[2] > 0.5f, "checkerboard should register high edge density")
    }

    @Test
    fun `brighter images have a higher mean-luminance component`() {
        val dark = SimpleFeatureExtractor.extract(ImageSample(10, 10, IntArray(100) { grayArgb(20) }))
        val bright = SimpleFeatureExtractor.extract(ImageSample(10, 10, IntArray(100) { grayArgb(230) }))
        assertTrue(bright[0] > dark[0])
    }

    @Test
    fun `identical images produce identical vectors`() {
        val a = SimpleFeatureExtractor.extract(ImageSample(10, 10, IntArray(100) { grayArgb(90) }))
        val b = SimpleFeatureExtractor.extract(ImageSample(10, 10, IntArray(100) { grayArgb(90) }))
        assertEquals(a.toList(), b.toList())
    }
}
