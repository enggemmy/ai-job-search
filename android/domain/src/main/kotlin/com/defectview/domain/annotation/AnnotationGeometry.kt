package com.defectview.domain.annotation

import com.defectview.domain.model.AnnotationType
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** An axis-aligned rectangle in the same normalized (0f..1f) space as annotation points.
 * Unlike [com.defectview.domain.model.BoundingBox] this has no invariants - it must represent
 * degenerate shapes too (e.g. a single-point text callout) while the user is mid-drag. */
data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(x: Float, y: Float, tolerance: Float = 0f): Boolean =
        x in (left - tolerance)..(right + tolerance) && y in (top - tolerance)..(bottom + tolerance)
}

enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * Pure geometry helpers for the Defect View annotation editor: hit-testing, moving, and
 * resizing annotations. Kept dependency-free (no Android Canvas/Path types) so it is testable
 * outside the app module and reusable if the editor's rendering technology ever changes.
 */
object AnnotationGeometry {

    fun boundingBox(points: List<Pair<Float, Float>>): Rect? {
        if (points.isEmpty()) return null
        var left = points[0].first
        var right = points[0].first
        var top = points[0].second
        var bottom = points[0].second
        for ((x, y) in points) {
            left = min(left, x)
            right = max(right, x)
            top = min(top, y)
            bottom = max(bottom, y)
        }
        return Rect(left, top, right, bottom)
    }

    fun translate(points: List<Pair<Float, Float>>, dx: Float, dy: Float): List<Pair<Float, Float>> =
        points.map { (x, y) -> (x + dx) to (y + dy) }

    fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val abx = bx - ax
        val aby = by - ay
        val lengthSquared = abx * abx + aby * aby
        if (lengthSquared == 0f) return hypot((px - ax).toDouble(), (py - ay).toDouble()).toFloat()
        val t = (((px - ax) * abx + (py - ay) * aby) / lengthSquared).coerceIn(0f, 1f)
        val projX = ax + t * abx
        val projY = ay + t * aby
        return hypot((px - projX).toDouble(), (py - projY).toDouble()).toFloat()
    }

    /**
     * Whether tapping at ([x], [y]) should select this annotation. Filled-shape semantics for
     * CIRCLE/RECTANGLE (tap anywhere inside), stroke-proximity for ARROW/FREEHAND, and a small
     * fixed hit box around the anchor for TEXT_CALLOUT.
     */
    fun hitTest(type: AnnotationType, points: List<Pair<Float, Float>>, x: Float, y: Float, tolerance: Float = 0.02f): Boolean {
        if (points.isEmpty()) return false
        return when (type) {
            AnnotationType.RECTANGLE -> boundingBox(points)?.contains(x, y, tolerance) ?: false
            AnnotationType.CIRCLE -> {
                val box = boundingBox(points) ?: return false
                val cx = (box.left + box.right) / 2f
                val cy = (box.top + box.bottom) / 2f
                val rx = box.width / 2f + tolerance
                val ry = box.height / 2f + tolerance
                if (rx <= 0f || ry <= 0f) return false
                val nx = (x - cx) / rx
                val ny = (y - cy) / ry
                nx * nx + ny * ny <= 1f
            }
            AnnotationType.ARROW, AnnotationType.FREEHAND -> {
                points.zipWithNext().any { (a, b) ->
                    distanceToSegment(x, y, a.first, a.second, b.first, b.second) <= tolerance
                } || (points.size == 1 && hypot((x - points[0].first).toDouble(), (y - points[0].second).toDouble()) <= tolerance)
            }
            AnnotationType.TEXT_CALLOUT -> {
                val (ax, ay) = points[0]
                hypot((x - ax).toDouble(), (y - ay).toDouble()) <= max(tolerance.toDouble(), 0.04)
            }
        }
    }

    /**
     * Resizes a two-point shape (CIRCLE/RECTANGLE, stored as [topLeft, bottomRight]) by dragging
     * one corner to a new position while the opposite corner stays fixed. No-op for shapes that
     * aren't exactly two points.
     */
    fun resizeCorner(points: List<Pair<Float, Float>>, corner: Corner, x: Float, y: Float): List<Pair<Float, Float>> {
        if (points.size != 2) return points
        val (topLeft, bottomRight) = points
        return when (corner) {
            Corner.TOP_LEFT -> listOf(x to y, bottomRight)
            Corner.TOP_RIGHT -> listOf(topLeft.first to y, x to bottomRight.second)
            Corner.BOTTOM_LEFT -> listOf(x to topLeft.second, bottomRight.first to y)
            Corner.BOTTOM_RIGHT -> listOf(topLeft, x to y)
        }
    }

    /** Clamps every point into the 0f..1f normalized image space, e.g. after a drag that overshoots the photo bounds. */
    fun clampToUnitSquare(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> =
        points.map { (x, y) -> x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f) }

    fun pathLength(points: List<Pair<Float, Float>>): Float {
        if (points.size < 2) return 0f
        var total = 0f
        for (i in 0 until points.size - 1) {
            val (ax, ay) = points[i]
            val (bx, by) = points[i + 1]
            total += sqrt(((bx - ax) * (bx - ax) + (by - ay) * (by - ay)).toDouble()).toFloat()
        }
        return total
    }
}
