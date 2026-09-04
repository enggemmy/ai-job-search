package com.defectview.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.defectview.domain.model.AttachmentType

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = DefectEntity::class,
            parentColumns = ["id"],
            childColumns = ["defectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = InspectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["inspectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("defectId"), Index("inspectionId")]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val defectId: Long?,
    val inspectionId: Long?,
    val type: AttachmentType,
    val filePath: String,
    val createdAt: Long
)
