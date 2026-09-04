package com.defectview.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.defectview.domain.model.DefectCategory
import com.defectview.domain.model.DefectPriority
import com.defectview.domain.model.DefectSeverity
import com.defectview.domain.model.DefectStatus
import com.defectview.domain.model.Trade

@Entity(
    tableName = "defects",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = InspectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["inspectionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("projectId"), Index("inspectionId"), Index(value = ["defectId"], unique = true)]
)
data class DefectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val defectId: String,
    val projectId: Long,
    val inspectionId: Long?,
    val location: String,
    val title: String,
    val description: String,
    val category: DefectCategory,
    val trade: Trade,
    val severity: DefectSeverity,
    val severityIsAiSuggested: Boolean,
    val recommendation: String,
    val status: DefectStatus,
    val responsibleParty: String,
    val priority: DefectPriority,
    val targetDate: Long?,
    val reportedBy: String,
    val inspectorComments: String,
    val closureComments: String,
    val beforePhotoPath: String?,
    val afterPhotoPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val closedAt: Long?
)
