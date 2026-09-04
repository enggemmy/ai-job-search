package com.defectview.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.defectview.app.data.db.entity.AIAnalysisEntity
import com.defectview.app.data.db.entity.AIDetectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AIAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAnalysis(analysis: AIAnalysisEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDetections(detections: List<AIDetectionEntity>): List<Long>

    @Update
    suspend fun updateAnalysis(analysis: AIAnalysisEntity)

    @Transaction
    suspend fun insertAnalysisWithDetections(analysis: AIAnalysisEntity, detections: List<AIDetectionEntity>): Long {
        val analysisId = insertAnalysis(analysis)
        if (detections.isNotEmpty()) {
            insertDetections(detections.map { it.copy(aiAnalysisId = analysisId) })
        }
        return analysisId
    }

    @Query("SELECT * FROM ai_analyses WHERE inspectionId = :inspectionId ORDER BY analyzedAt DESC")
    fun observeForInspection(inspectionId: Long): Flow<List<AIAnalysisEntity>>

    @Query("SELECT * FROM ai_detections WHERE aiAnalysisId = :analysisId")
    fun observeDetections(analysisId: Long): Flow<List<AIDetectionEntity>>
}
