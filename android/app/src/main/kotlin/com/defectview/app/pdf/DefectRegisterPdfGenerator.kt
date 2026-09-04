package com.defectview.app.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.defectview.domain.model.Defect
import com.defectview.domain.model.Project
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date

/**
 * "Defect Register" report (spec section 5F): a single tabular PDF listing every defect for a
 * project, generated straight from the live Defect table - not a static export.
 */
object DefectRegisterPdfGenerator {

    private const val PAGE_WIDTH = 842 // A4 landscape at 72dpi
    private const val PAGE_HEIGHT = 595
    private const val MARGIN = 28f
    private const val ROW_HEIGHT = 22f

    private val columns = listOf(
        "Defect ID" to 70f,
        "Location" to 110f,
        "Trade" to 90f,
        "Severity" to 60f,
        "Status" to 90f,
        "Title" to 220f,
        "Reported" to 100f
    )

    fun generate(outputFile: File, project: Project, defects: List<Defect>) {
        val document = PdfDocument()
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = MARGIN

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; isFakeBoldText = true }
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; isFakeBoldText = true; color = Color.WHITE }
        val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.BLACK }
        val headerBgPaint = Paint().apply { color = Color.rgb(27, 42, 74) }
        val rowLinePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }

        fun drawHeaderRow() {
            canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + ROW_HEIGHT, headerBgPaint)
            var x = MARGIN + 4f
            columns.forEach { (label, width) ->
                canvas.drawText(label, x, y + ROW_HEIGHT - 7f, headerPaint)
                x += width
            }
            y += ROW_HEIGHT
        }

        fun newPage() {
            document.finishPage(page)
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, document.pages.size + 1).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = MARGIN
            drawHeaderRow()
        }

        canvas.drawText("DEFECT REGISTER — ${project.name} (${project.projectNumber})", MARGIN, y + 14f, titlePaint)
        y += 28f
        drawHeaderRow()

        defects.forEach { defect ->
            if (y + ROW_HEIGHT > PAGE_HEIGHT - MARGIN) newPage()

            var x = MARGIN + 4f
            val values = listOf(
                defect.defectId,
                defect.location,
                defect.trade.name.replace('_', ' '),
                defect.severity.name,
                defect.status.name.replace('_', ' '),
                defect.title,
                DateFormat.getDateInstance(DateFormat.SHORT).format(Date(defect.createdAt))
            )
            values.forEachIndexed { index, value ->
                val (_, width) = columns[index]
                val truncated = truncateToWidth(value, width - 8f, cellPaint)
                canvas.drawText(truncated, x, y + ROW_HEIGHT - 7f, cellPaint)
                x += width
            }
            canvas.drawLine(MARGIN, y + ROW_HEIGHT, PAGE_WIDTH - MARGIN, y + ROW_HEIGHT, rowLinePaint)
            y += ROW_HEIGHT
        }

        if (defects.isEmpty()) {
            val emptyText = "No defects recorded for this project."
            val layout = StaticLayout.Builder.obtain(emptyText, 0, emptyText.length, cellPaint, (PAGE_WIDTH - 2 * MARGIN).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            canvas.save()
            canvas.translate(MARGIN, y + 8f)
            layout.draw(canvas)
            canvas.restore()
        }

        document.finishPage(page)
        FileOutputStream(outputFile).use { out -> document.writeTo(out) }
        document.close()
    }

    private fun truncateToWidth(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
