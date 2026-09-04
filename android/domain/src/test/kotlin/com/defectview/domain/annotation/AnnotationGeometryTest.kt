package com.defectview.domain.annotation

import com.defectview.domain.model.AnnotationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnnotationGeometryTest {

    @Test
    fun `boundingBox returns null for an empty point list`() {
        assertNull(AnnotationGeometry.boundingBox(emptyList()))
    }

    @Test
    fun `boundingBox covers all points regardless of order`() {
        val box = AnnotationGeometry.boundingBox(listOf(0.5f to 0.5f, 0.1f to 0.9f, 0.8f to 0.2f))
        assertEquals(0.1f, box!!.left)
        assertEquals(0.2f, box.top)
        assertEquals(0.8f, box.right)
        assertEquals(0.9f, box.bottom)
    }

    @Test
    fun `translate shifts every point by the same delta`() {
        val result = AnnotationGeometry.translate(listOf(0.1f to 0.1f, 0.2f to 0.3f), dx = 0.05f, dy = -0.05f)
        assertEquals(listOf(0.15f to 0.05f, 0.25f to 0.25f), result)
    }

    @Test
    fun `distanceToSegment is zero for a point on the segment`() {
        val d = AnnotationGeometry.distanceToSegment(0.5f, 0.5f, 0f, 0.5f, 1f, 0.5f)
        assertEquals(0f, d, 1e-5f)
    }

    @Test
    fun `distanceToSegment handles a degenerate zero-length segment`() {
        val d = AnnotationGeometry.distanceToSegment(3f, 4f, 0f, 0f, 0f, 0f)
        assertEquals(5f, d, 1e-5f)
    }

    @Test
    fun `hitTest for RECTANGLE is true inside and false well outside`() {
        val rect = listOf(0.2f to 0.2f, 0.6f to 0.6f)
        assertTrue(AnnotationGeometry.hitTest(AnnotationType.RECTANGLE, rect, 0.4f, 0.4f))
        assertFalse(AnnotationGeometry.hitTest(AnnotationType.RECTANGLE, rect, 0.9f, 0.9f))
    }

    @Test
    fun `hitTest for CIRCLE respects the ellipse not just the bounding box`() {
        val circle = listOf(0.0f to 0.0f, 1.0f to 1.0f) // center 0.5,0.5 radius 0.5
        assertTrue(AnnotationGeometry.hitTest(AnnotationType.CIRCLE, circle, 0.5f, 0.5f))
        // corner of the bounding box, outside the inscribed ellipse
        assertFalse(AnnotationGeometry.hitTest(AnnotationType.CIRCLE, circle, 0.02f, 0.02f, tolerance = 0f))
    }

    @Test
    fun `hitTest for ARROW is true near the line and false far from it`() {
        val arrow = listOf(0.1f to 0.1f, 0.9f to 0.1f)
        assertTrue(AnnotationGeometry.hitTest(AnnotationType.ARROW, arrow, 0.5f, 0.105f, tolerance = 0.02f))
        assertFalse(AnnotationGeometry.hitTest(AnnotationType.ARROW, arrow, 0.5f, 0.5f, tolerance = 0.02f))
    }

    @Test
    fun `hitTest for TEXT_CALLOUT uses a fixed radius around the anchor`() {
        val anchor = listOf(0.5f to 0.5f)
        assertTrue(AnnotationGeometry.hitTest(AnnotationType.TEXT_CALLOUT, anchor, 0.51f, 0.51f))
        assertFalse(AnnotationGeometry.hitTest(AnnotationType.TEXT_CALLOUT, anchor, 0.9f, 0.9f))
    }

    @Test
    fun `resizeCorner moves only the dragged corner`() {
        val rect = listOf(0.2f to 0.2f, 0.6f to 0.6f)
        val resized = AnnotationGeometry.resizeCorner(rect, Corner.BOTTOM_RIGHT, 0.8f, 0.9f)
        assertEquals(listOf(0.2f to 0.2f, 0.8f to 0.9f), resized)
    }

    @Test
    fun `resizeCorner is a no-op for shapes that are not exactly two points`() {
        val freehand = listOf(0.1f to 0.1f, 0.2f to 0.2f, 0.3f to 0.1f)
        assertEquals(freehand, AnnotationGeometry.resizeCorner(freehand, Corner.TOP_LEFT, 0.5f, 0.5f))
    }

    @Test
    fun `clampToUnitSquare pulls out-of-range points back into 0 to 1`() {
        val result = AnnotationGeometry.clampToUnitSquare(listOf(-0.2f to 1.5f, 0.5f to 0.5f))
        assertEquals(listOf(0f to 1f, 0.5f to 0.5f), result)
    }

    @Test
    fun `pathLength sums segment lengths and is zero for fewer than two points`() {
        assertEquals(0f, AnnotationGeometry.pathLength(listOf(0.1f to 0.1f)))
        val length = AnnotationGeometry.pathLength(listOf(0f to 0f, 3f to 4f))
        assertEquals(5f, length, 1e-5f)
    }
}
