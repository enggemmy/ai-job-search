package com.defectview.domain.confidence

import com.defectview.domain.model.ConfidenceLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ConfidenceClassifierTest {

    @ParameterizedTest
    @CsvSource(
        "0.0, LOW",
        "0.44, LOW",
        "0.45, MEDIUM",
        "0.74, MEDIUM",
        "0.75, HIGH",
        "1.0, HIGH"
    )
    fun `classify buckets score into the correct tier`(score: Float, expected: ConfidenceLevel) {
        assertEquals(expected, ConfidenceClassifier.classify(score))
    }

    @ParameterizedTest
    @CsvSource(
        "HIGH, Possible defect detected",
        "MEDIUM, Review recommended",
        "LOW, Insufficient confidence"
    )
    fun `displayText never implies AI confirmation`(level: ConfidenceLevel, expected: String) {
        val text = ConfidenceClassifier.displayText(level)
        assertEquals(expected, text)
        assert(!text.contains("confirmed", ignoreCase = true))
    }

    @org.junit.jupiter.api.Test
    fun `classify rejects out-of-range scores`() {
        assertThrows(IllegalArgumentException::class.java) { ConfidenceClassifier.classify(1.5f) }
        assertThrows(IllegalArgumentException::class.java) { ConfidenceClassifier.classify(-0.1f) }
    }
}
