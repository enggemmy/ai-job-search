package com.defectview.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.defectview.domain.model.EngineType
import com.defectview.domain.model.Trade

@Entity(
    tableName = "ai_analyses",
    foreignKeys = [
        ForeignKey(
            entity = InspectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["inspectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("inspectionId")]
)
data class AIAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inspectionId: Long,
    val photoPath: String,
    val modelName: String,
    val modelVersion: String,
    val engineType: EngineType,
    val analyzedAt: Long,
    val verified: Boolean,
    val generatedBySimilaritySearch: Boolean,
    val confirmedByInspector: Boolean
)

/** One raw detection belonging to an [AIAnalysisEntity]. Kept as its own table (not a JSON
 * blob) so detections stay queryable and the schema mirrors the ER design in spec section 6. */
@Entity(
    tableName = "ai_detections",
    foreignKeys = [
        ForeignKey(
            entity = AIAnalysisEntity::class,
            parentColumns = ["id"],
            childColumns = ["aiAnalysisId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("aiAnalysisId")]
)
data class AIDetectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val aiAnalysisId: Long,
    val defectCategoryHint: String,
    val trade: Trade,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val confidenceScore: Float,
    val evidence: String
)
