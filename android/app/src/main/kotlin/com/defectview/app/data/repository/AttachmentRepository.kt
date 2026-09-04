package com.defectview.app.data.repository

import com.defectview.app.data.db.dao.AttachmentDao
import com.defectview.app.data.db.entity.AttachmentEntity
import com.defectview.domain.model.AttachmentType
import kotlinx.coroutines.flow.Flow

class AttachmentRepository(private val attachmentDao: AttachmentDao) {

    fun observeForInspection(inspectionId: Long): Flow<List<AttachmentEntity>> =
        attachmentDao.observeForInspection(inspectionId)

    fun observeForDefect(defectId: Long): Flow<List<AttachmentEntity>> =
        attachmentDao.observeForDefect(defectId)

    suspend fun attachToInspection(inspectionId: Long, filePath: String, type: AttachmentType = AttachmentType.ORIGINAL_PHOTO): Long =
        attachmentDao.insert(
            AttachmentEntity(defectId = null, inspectionId = inspectionId, type = type, filePath = filePath, createdAt = System.currentTimeMillis())
        )

    suspend fun attachToDefect(defectId: Long, filePath: String, type: AttachmentType): Long =
        attachmentDao.insert(
            AttachmentEntity(defectId = defectId, inspectionId = null, type = type, filePath = filePath, createdAt = System.currentTimeMillis())
        )
}
