package com.defectview.domain.model

/**
 * Confidence tiers the UI is allowed to show. Never map any tier to language implying the AI
 * "confirmed" a defect - see ConfidenceClassifier and CLAUDE.md-equivalent product rules in
 * spec section 7 (AI Confidence and Safety).
 */
enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW
}

data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f) {
            "BoundingBox coordinates must be normalized to 0f..1f"
        }
        require(right > left && bottom > top) { "BoundingBox must have positive width/height" }
    }
}

/** One raw detection produced by a VisionEngine implementation, before reasoning is applied. */
data class DefectDetection(
    val defectCategoryHint: String,
    val trade: Trade,
    val boundingBox: BoundingBox,
    val confidenceScore: Float,
    val evidence: String
) {
    init {
        require(confidenceScore in 0f..1f) { "confidenceScore must be in 0f..1f" }
    }
}

data class AiEngineInfo(
    val modelName: String,
    val modelVersion: String,
    val engineType: EngineType
)

enum class EngineType {
    CLASSICAL_CV,
    TFLITE_MODEL,
    ONNX_MODEL,
    SIMILARITY_SEARCH
}

/** Persisted record of one AI analysis run against a photo, always attached to an inspection. */
data class AIAnalysis(
    val id: Long = 0,
    val inspectionId: Long,
    val photoPath: String,
    val engineInfo: AiEngineInfo,
    val detections: List<DefectDetection>,
    val analyzedAt: Long,
    val verified: Boolean = false,
    val generatedBySimilaritySearch: Boolean = false,
    val confirmedByInspector: Boolean = false
)

enum class VerificationStatus {
    APPROVED,
    CORRECTED,
    REJECTED
}

/**
 * A verified training example. Only created once an inspector has approved, corrected, or
 * explicitly labeled a case - never written automatically from a raw AI prediction.
 */
data class VerifiedExample(
    val id: Long = 0,
    val originalImagePath: String,
    val croppedImagePath: String?,
    val aiPrediction: DefectDetection?,
    val correction: String?,
    val finalLabel: String,
    val boundingBox: BoundingBox?,
    val trade: Trade,
    val category: DefectCategory,
    val embedding: FloatArray,
    val verificationStatus: VerificationStatus,
    val modelVersion: String,
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VerifiedExample) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

enum class LearningQueueStatus {
    PENDING,
    QUEUED_FOR_TRAINING,
    INCLUDED_IN_MODEL,
    DISCARDED
}

data class LearningQueueEntry(
    val id: Long = 0,
    val verifiedExampleId: Long,
    val status: LearningQueueStatus,
    val enqueuedAt: Long,
    val processedAt: Long? = null
)

data class ModelVersion(
    val id: Long = 0,
    val versionLabel: String,
    val engineType: EngineType,
    val trainingExampleCount: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val notes: String = ""
)
