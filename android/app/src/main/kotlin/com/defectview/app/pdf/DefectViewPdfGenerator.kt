package com.defectview.app.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.defectview.app.data.db.entity.AttachmentEntity
import com.defectview.domain.model.AttachmentType
import com.defectview.domain.model.Defect
import com.defectview.domain.model.Project
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date

/**
 * Generates the "Individual Defect View" PDF report (spec section 5F) using only
 * android.graphics.pdf.PdfDocument - no external PDF library. Every field comes from the actual
 * Defect/Project records and their attachments; nothing here is a static template image.
 */
object DefectViewPdfGenerator {

    private const val PAGE_WIDTH = 595 // A4 at 72dpi, points
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f

    fun generate(
        outputFile: File,
        project: Project,
        defect: Defect,
        attachments: List<AttachmentEntity>
    ) {
        val document = PdfDocument()
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = MARGIN

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK }
        val headingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; isFakeBoldText = true; color = Color.rgb(27, 42, 74) }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.BLACK }
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.DKGRAY }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

        fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, document.pages.size + 1).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN
            }
        }

        fun drawWrapped(text: String, paint: TextPaint, width: Int = (PAGE_WIDTH - 2 * MARGIN).toInt()) {
            if (text.isBlank()) return
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1f)
                .build()
            ensureSpace(layout.height.toFloat() + 6f)
            canvas.save()
            canvas.translate(MARGIN, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + 6f
        }

        fun drawLabelValue(label: String, value: String) {
            drawWrapped(label, labelPaint)
            drawWrapped(value.ifBlank { "—" }, bodyPaint)
        }

        fun drawDivider() {
            ensureSpace(12f)
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 12f
        }

        fun drawPhoto(path: String?, label: String) {
            drawWrapped(label, headingPaint)
            val bitmap = path?.let { BitmapFactory.decodeFile(it) }
            if (bitmap == null) {
                drawWrapped("Not available.", bodyPaint)
                return
            }
            val maxWidth = PAGE_WIDTH - 2 * MARGIN
            val maxHeight = 220f
            val scale = minOf(maxWidth / bitmap.width, maxHeight / bitmap.height)
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            ensureSpace(h + 10f)
            canvas.drawBitmap(bitmap, null, RectF(MARGIN, y, MARGIN + w, y + h), null)
            y += h + 10f
        }

        // Header
        drawWrapped("DEFECT VIEW", titlePaint)
        drawWrapped("Construction Inspection Report", bodyPaint)
        drawDivider()

        drawWrapped("Project: ${project.name} (${project.projectNumber})", headingPaint)
        drawLabelValue("Client", project.client)
        drawLabelValue("Consultant", project.consultant)
        drawLabelValue("Contractor", project.contractor)
        drawLabelValue("Location", project.location)
        drawDivider()

        drawWrapped("Defect ${defect.defectId}", headingPaint)
        drawLabelValue("Inspection date", DateFormat.getDateInstance().format(Date(defect.createdAt)))
        drawLabelValue("Location", defect.location)
        drawLabelValue("Reported by", defect.reportedBy)
        drawLabelValue("Category / Trade", "${defect.category.name} / ${defect.trade.name.replace('_', ' ')}")
        drawLabelValue(
            "Severity${if (defect.severityIsAiSuggested) " (Suggested — Inspector Confirmation Required)" else ""}",
            defect.severity.name
        )
        drawLabelValue("Status", defect.status.name.replace('_', ' '))
        drawDivider()

        drawWrapped("Description", headingPaint)
        drawWrapped(defect.description, bodyPaint)

        drawWrapped("Recommendation", headingPaint)
        drawWrapped(defect.recommendation, bodyPaint)

        val annotatedPath = attachments.firstOrNull { it.type == AttachmentType.ANNOTATED_PHOTO }?.filePath
            ?: attachments.firstOrNull { it.type == AttachmentType.ORIGINAL_PHOTO }?.filePath
        drawPhoto(annotatedPath, "Defect Photo")

        if (defect.beforePhotoPath != null || defect.afterPhotoPath != null) {
            drawDivider()
            drawWrapped("Before / After", headingPaint)
            drawPhoto(defect.beforePhotoPath, "Before")
            drawPhoto(defect.afterPhotoPath, "After")
        }

        if (defect.inspectorComments.isNotBlank()) {
            drawWrapped("Inspector Comments", headingPaint)
            drawWrapped(defect.inspectorComments, bodyPaint)
        }

        if (defect.closureComments.isNotBlank()) {
            drawWrapped("Closure Information", headingPaint)
            drawWrapped(defect.closureComments, bodyPaint)
        }

        document.finishPage(page)

        FileOutputStream(outputFile).use { out -> document.writeTo(out) }
        document.close()
    }
}
