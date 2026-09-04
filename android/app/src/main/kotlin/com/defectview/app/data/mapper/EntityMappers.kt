package com.defectview.app.data.mapper

import com.defectview.app.data.db.entity.DefectEntity
import com.defectview.app.data.db.entity.InspectionEntity
import com.defectview.app.data.db.entity.ProjectEntity
import com.defectview.domain.model.Defect
import com.defectview.domain.model.Inspection
import com.defectview.domain.model.Project

fun ProjectEntity.toDomain() = Project(
    id = id,
    projectNumber = projectNumber,
    name = name,
    client = client,
    consultant = consultant,
    contractor = contractor,
    location = location,
    description = description,
    logoPath = logoPath,
    status = status,
    createdAt = createdAt
)

fun Project.toEntity() = ProjectEntity(
    id = id,
    projectNumber = projectNumber,
    name = name,
    client = client,
    consultant = consultant,
    contractor = contractor,
    location = location,
    description = description,
    logoPath = logoPath,
    status = status,
    createdAt = createdAt
)

fun InspectionEntity.toDomain() = Inspection(
    id = id,
    projectId = projectId,
    location = location,
    area = area,
    trade = trade,
    notes = notes,
    inspectorName = inspectorName,
    createdAt = createdAt
)

fun Inspection.toEntity() = InspectionEntity(
    id = id,
    projectId = projectId,
    location = location,
    area = area,
    trade = trade,
    notes = notes,
    inspectorName = inspectorName,
    createdAt = createdAt
)

fun DefectEntity.toDomain() = Defect(
    id = id,
    defectId = defectId,
    projectId = projectId,
    inspectionId = inspectionId,
    location = location,
    title = title,
    description = description,
    category = category,
    trade = trade,
    severity = severity,
    severityIsAiSuggested = severityIsAiSuggested,
    recommendation = recommendation,
    status = status,
    responsibleParty = responsibleParty,
    priority = priority,
    targetDate = targetDate,
    reportedBy = reportedBy,
    inspectorComments = inspectorComments,
    closureComments = closureComments,
    beforePhotoPath = beforePhotoPath,
    afterPhotoPath = afterPhotoPath,
    createdAt = createdAt,
    updatedAt = updatedAt,
    closedAt = closedAt
)

fun Defect.toEntity() = DefectEntity(
    id = id,
    defectId = defectId,
    projectId = projectId,
    inspectionId = inspectionId,
    location = location,
    title = title,
    description = description,
    category = category,
    trade = trade,
    severity = severity,
    severityIsAiSuggested = severityIsAiSuggested,
    recommendation = recommendation,
    status = status,
    responsibleParty = responsibleParty,
    priority = priority,
    targetDate = targetDate,
    reportedBy = reportedBy,
    inspectorComments = inspectorComments,
    closureComments = closureComments,
    beforePhotoPath = beforePhotoPath,
    afterPhotoPath = afterPhotoPath,
    createdAt = createdAt,
    updatedAt = updatedAt,
    closedAt = closedAt
)
