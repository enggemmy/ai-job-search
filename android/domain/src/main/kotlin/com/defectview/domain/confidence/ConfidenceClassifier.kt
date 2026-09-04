package com.defectview.domain.confidence

import com.defectview.domain.model.ConfidenceLevel

/**
 * Maps a raw AI confidence score to a display-safe tier and message. The product rule (spec
 * section 7) is absolute: the UI must never say "AI confirmed defect" - only "AI suggestion",
 * with the strength of that suggestion communicated through these three tiers.
 */
object ConfidenceClassifier {
    const val HIGH_THRESHOLD = 0.75f
    const val MEDIUM_THRESHOLD = 0.45f

    fun classify(score: Float): ConfidenceLevel {
        require(score in 0f..1f) { "score must be in 0f..1f" }
        return when {
            score >= HIGH_THRESHOLD -> ConfidenceLevel.HIGH
            score >= MEDIUM_THRESHOLD -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
    }

    fun displayText(level: ConfidenceLevel): String = when (level) {
        ConfidenceLevel.HIGH -> "Possible defect detected"
        ConfidenceLevel.MEDIUM -> "Review recommended"
        ConfidenceLevel.LOW -> "Insufficient confidence"
    }

    fun displayText(score: Float): String = displayText(classify(score))
}
