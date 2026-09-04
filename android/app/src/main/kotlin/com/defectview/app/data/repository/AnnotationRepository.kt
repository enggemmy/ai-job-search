package com.defectview.app.data.repository

import com.defectview.app.data.db.dao.AnnotationDao
import com.defectview.app.data.db.entity.AnnotationEntity
import com.defectview.app.feature.editor.AnnotationDraft

class AnnotationRepository(private val annotationDao: AnnotationDao) {

    fun observeForDefect(defectId: Long) = annotationDao.observeForDefect(defectId)

    suspend fun replaceAllForDefect(defectId: Long, drafts: List<AnnotationDraft>) {
        annotationDao.deleteAllForDefect(defectId)
        if (drafts.isEmpty()) return
        val entities = drafts.mapIndexed { index, draft ->
            AnnotationEntity(
                defectId = defectId,
                type = draft.type,
                label = draft.label,
                colorArgb = draft.colorArgb,
                points = draft.points,
                strokeWidth = draft.strokeWidth,
                createdByAi = draft.createdByAi,
                orderIndex = index
            )
        }
        annotationDao.insertAll(entities)
    }
}
