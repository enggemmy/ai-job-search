package com.defectview.app.feature.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.defectview.domain.annotation.AnnotationGeometry
import com.defectview.domain.model.AnnotationType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bakes the in-memory annotation drafts onto a copy of the source photo using plain
 * android.graphics (no Compose dependency), so the composited "annotated photo" attachment can
 * be produced from a ViewModel/coroutine without a Composable in scope, and matches exactly
 * what the editor showed - normalized (0f..1f) points scaled to the bitmap's own pixel size.
 */
object AnnotationRenderer {

    fun render(source: Bitmap, annotations: List<AnnotationDraft>): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val width = output.width.toFloat()
        val height = output.height.toFloat()

        annotations.forEach { annotation ->
            drawOne(canvas, annotation, width, height)
        }
        return output
    }

    private fun drawOne(canvas: Canvas, annotation: AnnotationDraft, width: Float, height: Float) {
        val color = (0xFF000000L or (annotation.colorArgb and 0x00FFFFFFL)).toInt()
        val strokeWidth = annotation.strokeWidth * (width / 1000f).coerceAtLeast(1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
        }

        fun px(point: Pair<Float, Float>) = point.first * width to point.second * height

        when (annotation.type) {
            AnnotationType.RECTANGLE -> {
                val box = AnnotationGeometry.boundingBox(annotation.points) ?: return
                val (l, t) = px(box.left to box.top)
                val (r, b) = px(box.right to box.bottom)
                canvas.drawRect(l, t, r, b, paint)
            }
            AnnotationType.CIRCLE -> {
                val box = AnnotationGeometry.boundingBox(annotation.points) ?: return
                val (l, t) = px(box.left to box.top)
                val (r, b) = px(box.right to box.bottom)
                canvas.drawOval(android.graphics.RectF(l, t, r, b), paint)
            }
            AnnotationType.ARROW -> {
                if (annotation.points.size < 2) return
                val (sx, sy) = px(annotation.points.first())
                val (ex, ey) = px(annotation.points.last())
                canvas.drawLine(sx, sy, ex, ey, paint)
                val angle = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                val headLength = strokeWidth * 5
                val headAngle = Math.PI / 7
                val p1x = ex - (headLength * cos(angle - headAngle)).toFloat()
                val p1y = ey - (headLength * sin(angle - headAngle)).toFloat()
                val p2x = ex - (headLength * cos(angle + headAngle)).toFloat()
                val p2y = ey - (headLength * sin(angle + headAngle)).toFloat()
                canvas.drawLine(ex, ey, p1x, p1y, paint)
                canvas.drawLine(ex, ey, p2x, p2y, paint)
            }
            AnnotationType.FREEHAND -> {
                if (annotation.points.size < 2) return
                val path = Path()
                val (fx, fy) = px(annotation.points.first())
                path.moveTo(fx, fy)
                annotation.points.drop(1).forEach {
                    val (x, y) = px(it)
                    path.lineTo(x, y)
                }
                canvas.drawPath(path, paint)
            }
            AnnotationType.TEXT_CALLOUT -> {
                if (annotation.points.isEmpty()) return
                val (ax, ay) = px(annotation.points.first())
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
                canvas.drawCircle(ax, ay, strokeWidth * 1.5f, dot)
                if (annotation.label.isNotBlank()) {
                    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = color
                        textSize = (height / 45f).coerceAtLeast(24f)
                    }
                    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb(217, 255, 255, 255) }
                    val textWidth = textPaint.measureText(annotation.label)
                    val padding = strokeWidth * 2
                    canvas.drawRect(
                        ax + padding, ay - textPaint.textSize,
                        ax + padding * 2 + textWidth, ay + padding,
                        bgPaint
                    )
                    canvas.drawText(annotation.label, ax + padding * 1.5f, ay, textPaint)
                }
            }
        }
    }
}
