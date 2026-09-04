package com.defectview.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.defectview.domain.model.AnnotationType

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = DefectEntity::class,
            parentColumns = ["id"],
            childColumns = ["defectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("defectId")]
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val defectId: Long,
    val type: AnnotationType,
    val label: String,
    val colorArgb: Long,
    /** Normalized (0f..1f) point list, serialized as "x1,y1;x2,y2;..." - see Converters. */
    val points: List<Pair<Float, Float>>,
    val strokeWidth: Float,
    val createdByAi: Boolean,
    val orderIndex: Int
)
