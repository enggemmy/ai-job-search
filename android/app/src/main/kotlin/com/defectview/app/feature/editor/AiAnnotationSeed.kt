package com.defectview.app.feature.editor

import com.defectview.domain.model.AnnotationType
import com.defectview.domain.model.DefectDetection
import com.defectview.domain.taxonomy.DefectTaxonomy

/**
 * Converts raw AI detections into editable rectangle annotations pre-drawn on the Defect View
 * editor canvas (spec section 5C: "AI-generated annotations must be editable" / "the user must
 * be able to correct the AI location"). The inspector can move, resize, recolor, relabel, or
 * delete every one of these exactly like a hand-drawn annotation - `createdByAi = true` is kept
 * only as a provenance flag, it changes no editing behavior.
 */
fun DefectDetection.toSeedAnnotationDraft(localKey: Long): AnnotationDraft {
    val label = DefectTaxonomy.find(defectCategoryHint)?.displayName
        ?: defectCategoryHint.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    return AnnotationDraft(
        localKey = localKey,
        type = AnnotationType.RECTANGLE,
        label = label,
        colorArgb = AI_SUGGESTION_COLOR,
        points = listOf(boundingBox.left to boundingBox.top, boundingBox.right to boundingBox.bottom),
        createdByAi = true
    )
}

fun List<DefectDetection>.toSeedAnnotationDrafts(): List<AnnotationDraft> =
    mapIndexed { index, detection -> detection.toSeedAnnotationDraft(localKey = index.toLong()) }
