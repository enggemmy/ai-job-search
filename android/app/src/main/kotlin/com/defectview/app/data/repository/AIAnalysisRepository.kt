package com.defectview.app.data.repository

import com.defectview.app.data.db.dao.AIAnalysisDao
import com.defectview.app.data.db.entity.AIAnalysisEntity
import com.defectview.app.data.db.entity.AIDetectionEntity
import com.defectview.domain.model.AiEngineInfo
import com.defectview.domain.model.DefectDetection

class AIAnalysisRepository(private val aiAnalysisDao: AIAnalysisDao) {

    fun observeForInspection(inspectionId: Long) = aiAnalysisDao.observeForInspection(inspectionId)

    suspend fun record(
        inspectionId: Long,
        photoPath: String,
        engineInfo: AiEngineInfo,
        detections: List<DefectDetection>,
        analyzedAt: Long
    ): Long {
        val analysis = AIAnalysisEntity(
            inspectionId = inspectionId,
            photoPath = photoPath,
            modelName = engineInfo.modelName,
            modelVersion = engineInfo.modelVersion,
            engineType = engineInfo.engineType,
            analyzedAt = analyzedAt,
            verified = false,
            generatedBySimilaritySearch = false,
            confirmedByInspector = false
        )
        val detectionEntities = detections.map {
            AIDetectionEntity(
                aiAnalysisId = 0, // set by insertAnalysisWithDetections
                defectCategoryHint = it.defectCategoryHint,
                trade = it.trade,
                boxLeft = it.boundingBox.left,
                boxTop = it.boundingBox.top,
                boxRight = it.boundingBox.right,
                boxBottom = it.boundingBox.bottom,
                confidenceScore = it.confidenceScore,
                evidence = it.evidence
            )
        }
        return aiAnalysisDao.insertAnalysisWithDetections(analysis, detectionEntities)
    }
}
