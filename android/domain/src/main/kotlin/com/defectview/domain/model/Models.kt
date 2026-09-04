package com.defectview.domain.model

/** Trades recognized by the inspection workflow. Extend as new trades are onboarded. */
enum class Trade {
    MASONRY,
    ARCHITECTURAL_FINISHES,
    ELECTRICAL,
    MECHANICAL,
    PLUMBING,
    CIVIL_STRUCTURAL,
    GENERAL
}

enum class DefectCategory {
    WORKMANSHIP,
    MATERIAL,
    DESIGN,
    SAFETY,
    INCOMPLETE_WORK,
    CLEANLINESS,
    OTHER
}

enum class DefectSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}

enum class DefectPriority {
    URGENT,
    HIGH,
    NORMAL,
    LOW
}

enum class DefectStatus {
    OPEN,
    IN_PROGRESS,
    PENDING_VERIFICATION,
    CLOSED,
    REJECTED
}

enum class ProjectStatus {
    ACTIVE,
    ON_HOLD,
    COMPLETED,
    ARCHIVED
}

data class Project(
    val id: Long = 0,
    val projectNumber: String,
    val name: String,
    val client: String,
    val consultant: String,
    val contractor: String,
    val location: String,
    val description: String = "",
    val logoPath: String? = null,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val createdAt: Long
)

data class Inspection(
    val id: Long = 0,
    val projectId: Long,
    val location: String,
    val area: String,
    val trade: Trade,
    val notes: String = "",
    val inspectorName: String,
    val createdAt: Long
)

/**
 * A defect record. [id] is the persistence row id; [defectId] is the human-facing,
 * sequential identifier (e.g. DV-000152) produced by [com.defectview.domain.id.DefectIdGenerator].
 */
data class Defect(
    val id: Long = 0,
    val defectId: String,
    val projectId: Long,
    val inspectionId: Long?,
    val location: String,
    val title: String,
    val description: String,
    val category: DefectCategory,
    val trade: Trade,
    val severity: DefectSeverity,
    val severityIsAiSuggested: Boolean = false,
    val recommendation: String,
    val status: DefectStatus = DefectStatus.OPEN,
    val responsibleParty: String = "",
    val priority: DefectPriority = DefectPriority.NORMAL,
    val targetDate: Long? = null,
    val reportedBy: String,
    val inspectorComments: String = "",
    val closureComments: String = "",
    val beforePhotoPath: String? = null,
    val afterPhotoPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val closedAt: Long? = null
)

enum class AnnotationType {
    CIRCLE,
    RECTANGLE,
    ARROW,
    FREEHAND,
    TEXT_CALLOUT
}

/** A single editable annotation drawn over a defect photo. Coordinates are normalized (0f..1f) to the source image. */
data class DefectAnnotation(
    val id: Long = 0,
    val defectId: Long,
    val type: AnnotationType,
    val label: String = "",
    val colorArgb: Long,
    val points: List<Pair<Float, Float>>,
    val strokeWidth: Float = 4f,
    val createdByAi: Boolean = false,
    val orderIndex: Int = 0
)

enum class AttachmentType {
    ORIGINAL_PHOTO,
    ANNOTATED_PHOTO,
    BEFORE_PHOTO,
    AFTER_PHOTO,
    DOCUMENT
}

data class Attachment(
    val id: Long = 0,
    val defectId: Long?,
    val inspectionId: Long?,
    val type: AttachmentType,
    val filePath: String,
    val createdAt: Long
)
