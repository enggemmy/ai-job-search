package com.defectview.domain.reasoning

import com.defectview.domain.model.BoundingBox
import com.defectview.domain.model.ConfidenceLevel
import com.defectview.domain.model.DefectDetection
import com.defectview.domain.model.Trade
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InspectionReasoningEngineTest {

    private val engine = InspectionReasoningEngine()
    private val box = BoundingBox(0.1f, 0.1f, 0.5f, 0.5f)

    @Test
    fun `known taxonomy key resolves to its display name and default recommendation`() {
        val detection = DefectDetection("excess_mortar", Trade.MASONRY, box, confidenceScore = 0.9f, evidence = "irregular surface texture")
        val result = engine.reason(detection)

        assertEquals("Excess Mortar", result.title)
        assertTrue(result.recommendation.contains("Remove excess mortar"))
        assertEquals(ConfidenceLevel.HIGH, result.confidenceLevel)
        assertEquals("Possible defect detected", result.confidenceDisplayText)
    }

    @Test
    fun `unknown hint falls back to a humanized title and generic verification text`() {
        val detection = DefectDetection("mystery_defect", Trade.GENERAL, box, confidenceScore = 0.5f, evidence = "")
        val result = engine.reason(detection)

        assertEquals("Mystery Defect", result.title)
        assertEquals("Verify against the approved project specification and method statement.", result.recommendation)
        assertEquals("Verify against the approved project documents.", result.requiredVerification)
    }

    @Test
    fun `project knowledge overrides the default recommendation when present`() {
        val knowledge = object : ProjectKnowledge {
            override fun recommendationFor(defectTypeKey: String) =
                if (defectTypeKey == "excess_mortar") "Per Spec Section 4.2: rework to a 10mm tolerance." else null
            override fun hasSpecFor(defectTypeKey: String) = defectTypeKey == "excess_mortar"
        }
        val detection = DefectDetection("excess_mortar", Trade.MASONRY, box, confidenceScore = 0.9f, evidence = "")
        val result = engine.reason(detection, knowledge)

        assertEquals("Per Spec Section 4.2: rework to a 10mm tolerance.", result.recommendation)
        assertEquals("Confirm against the referenced project specification clause.", result.requiredVerification)
    }

    @Test
    fun `severity is always flagged as an AI suggestion never a final determination`() {
        val detection = DefectDetection("cracks", Trade.MASONRY, box, confidenceScore = 0.95f, evidence = "")
        val result = engine.reason(detection)
        assertTrue(result.severityIsAiSuggested)
    }

    @Test
    fun `low confidence never escalates to a high suggested severity`() {
        val detection = DefectDetection("cracks", Trade.MASONRY, box, confidenceScore = 0.1f, evidence = "")
        val result = engine.reason(detection)
        assertFalse(result.confidenceDisplayText.contains("confirmed", ignoreCase = true))
    }
}
