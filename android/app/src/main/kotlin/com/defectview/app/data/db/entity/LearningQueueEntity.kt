package com.defectview.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.defectview.domain.model.LearningQueueStatus

@Entity(
    tableName = "learning_queue",
    foreignKeys = [
        ForeignKey(
            entity = VerifiedExampleEntity::class,
            parentColumns = ["id"],
            childColumns = ["verifiedExampleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("verifiedExampleId"), Index("status")]
)
data class LearningQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val verifiedExampleId: Long,
    val status: LearningQueueStatus,
    val enqueuedAt: Long,
    val processedAt: Long?
)
