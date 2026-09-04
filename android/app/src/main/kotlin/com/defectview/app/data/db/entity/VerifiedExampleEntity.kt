package com.defectview.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.defectview.domain.model.DefectCategory
import com.defectview.domain.model.Trade
import com.defectview.domain.model.VerificationStatus

@Entity(tableName = "verified_examples")
data class VerifiedExampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalImagePath: String,
    val croppedImagePath: String?,
    val aiPredictionHint: String?,
    val aiPredictionConfidence: Float?,
    val correction: String?,
    val finalLabel: String,
    val boxLeft: Float?,
    val boxTop: Float?,
    val boxRight: Float?,
    val boxBottom: Float?,
    val trade: Trade,
    val category: DefectCategory,
    /** Feature embedding, serialized as a comma-separated float string - see Converters. */
    val embedding: FloatArray,
    val verificationStatus: VerificationStatus,
    val modelVersion: String,
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VerifiedExampleEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
