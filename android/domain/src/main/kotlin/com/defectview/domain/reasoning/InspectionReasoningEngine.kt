package com.defectview.domain.reasoning

import com.defectview.domain.confidence.ConfidenceClassifier
import com.defectview.domain.model.ConfidenceLevel
import com.defectview.domain.model.DefectCategory
import com.defectview.domain.model.DefectDetection
import com.defectview.domain.model.DefectSeverity
import com.defectview.domain.model.Trade
import com.defectview.domain.taxonomy.DefectTaxonomy

/** A verification question the inspector should answer before accepting the suggestion as-is. */
data class InspectionQuestion(val text: String)

/**
 * A structured, editable draft of a defect record produced from one raw [DefectDetection].
 * Nothing here is final: every field is a starting point the inspector reviews, corrects, or
 * discards in the Defect Record screen (spec section 4B / 7 - the inspector always approves the
 * final record).
 */
data class ReasoningResult(
    val title: String,
    val description: String,
    val category: DefectCategory,
    val trade: Trade,
    val suggestedSeverity: DefectSeverity,
    val severityIsAiSuggested: Boolean,
    val recommendation: String,
    val confidenceLevel: ConfidenceLevel,
    val confidenceDisplayText: String,
    val evidence: String,
    val questions: List<InspectionQuestion>,
    val requiredVerification: String
)

/**
 * Rule-based mapping from a raw vision detection + optional project knowledge to a structured
 * inspection record draft. This is deliberately NOT a black box: every output field traces to
 * either the detection itself, the static [DefectTaxonomy], or a supplied [ProjectKnowledge]
 * fact - never an invented project requirement (spec section 10).
 */
class InspectionReasoningEngine {

    fun reason(detection: DefectDetection, projectKnowledge: ProjectKnowledge? = null): ReasoningResult {
        val taxonomyEntry = DefectTaxonomy.find(detection.defectCategoryHint)
        val confidenceLevel = ConfidenceClassifier.classify(detection.confidenceScore)

        val title = taxonomyEntry?.displayName ?: humanizeHint(detection.defectCategoryHint)
        val category = taxonomyEntry?.defaultCategory ?: DefectCategory.OTHER
        val trade = taxonomyEntry?.trade ?: detection.trade

        val recommendation = projectKnowledge
            ?.recommendationFor(detection.defectCategoryHint)
            ?: taxonomyEntry?.defaultRecommendation
            ?: "Verify against the approved project specification and method statement."

        val suggestedSeverity = suggestSeverity(confidenceLevel, category)

        val description = buildString {
            append(title)
            append(" observed")
            if (detection.evidence.isNotBlank()) {
                append(" - evidence: ")
                append(detection.evidence)
            }
            append(".")
        }

        return ReasoningResult(
            title = title,
            description = description,
            category = category,
            trade = trade,
            suggestedSeverity = suggestedSeverity,
            severityIsAiSuggested = true,
            recommendation = recommendation,
            confidenceLevel = confidenceLevel,
            confidenceDisplayText = ConfidenceClassifier.displayText(confidenceLevel),
            evidence = detection.evidence,
            questions = defaultQuestions(category),
            requiredVerification = if (projectKnowledge?.hasSpecFor(detection.defectCategoryHint) == true) {
                "Confirm against the referenced project specification clause."
            } else {
                "Verify against the approved project documents."
            }
        )
    }

    private fun suggestSeverity(confidence: ConfidenceLevel, category: DefectCategory): DefectSeverity {
        return when {
            category == DefectCategory.SAFETY -> DefectSeverity.CRITICAL
            confidence == ConfidenceLevel.HIGH && category == DefectCategory.WORKMANSHIP -> DefectSeverity.HIGH
            confidence == ConfidenceLevel.HIGH -> DefectSeverity.MEDIUM
            confidence == ConfidenceLevel.MEDIUM -> DefectSeverity.MEDIUM
            else -> DefectSeverity.LOW
        }
    }

    private fun defaultQuestions(category: DefectCategory): List<InspectionQuestion> = listOf(
        InspectionQuestion("Does this match an approved sample or reference finish?"),
        InspectionQuestion("Is this within the tolerance permitted by the specification?"),
        InspectionQuestion("Has this location been previously flagged in an earlier inspection?")
    ).let { base ->
        if (category == DefectCategory.INCOMPLETE_WORK) {
            base + InspectionQuestion("What is the contractor's committed completion date for this item?")
        } else base
    }

    private fun humanizeHint(hint: String): String =
        hint.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}

/**
 * Local, project-specific reference material (specs, method statements, ITPs) the reasoning
 * engine may cite. Absence of a fact must fall back to "verify against the approved project
 * documents" rather than inventing a requirement.
 */
interface ProjectKnowledge {
    fun recommendationFor(defectTypeKey: String): String?
    fun hasSpecFor(defectTypeKey: String): Boolean
}
