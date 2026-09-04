package com.defectview.app.data.db

import androidx.room.TypeConverter
import com.defectview.domain.model.AnnotationType
import com.defectview.domain.model.AttachmentType
import com.defectview.domain.model.DefectCategory
import com.defectview.domain.model.DefectPriority
import com.defectview.domain.model.DefectSeverity
import com.defectview.domain.model.DefectStatus
import com.defectview.domain.model.EngineType
import com.defectview.domain.model.LearningQueueStatus
import com.defectview.domain.model.ProjectStatus
import com.defectview.domain.model.Trade
import com.defectview.domain.model.VerificationStatus

/** Room type converters for the domain enums and the two composite value types that appear in
 * entities (annotation point lists, embedding vectors). Everything here is a pure, lossless
 * round-trip - no data is inferred or defaulted silently. */
class Converters {

    @TypeConverter fun tradeToString(v: Trade): String = v.name
    @TypeConverter fun stringToTrade(v: String): Trade = Trade.valueOf(v)

    @TypeConverter fun categoryToString(v: DefectCategory): String = v.name
    @TypeConverter fun stringToCategory(v: String): DefectCategory = DefectCategory.valueOf(v)

    @TypeConverter fun severityToString(v: DefectSeverity): String = v.name
    @TypeConverter fun stringToSeverity(v: String): DefectSeverity = DefectSeverity.valueOf(v)

    @TypeConverter fun priorityToString(v: DefectPriority): String = v.name
    @TypeConverter fun stringToPriority(v: String): DefectPriority = DefectPriority.valueOf(v)

    @TypeConverter fun statusToString(v: DefectStatus): String = v.name
    @TypeConverter fun stringToStatus(v: String): DefectStatus = DefectStatus.valueOf(v)

    @TypeConverter fun projectStatusToString(v: ProjectStatus): String = v.name
    @TypeConverter fun stringToProjectStatus(v: String): ProjectStatus = ProjectStatus.valueOf(v)

    @TypeConverter fun annotationTypeToString(v: AnnotationType): String = v.name
    @TypeConverter fun stringToAnnotationType(v: String): AnnotationType = AnnotationType.valueOf(v)

    @TypeConverter fun attachmentTypeToString(v: AttachmentType): String = v.name
    @TypeConverter fun stringToAttachmentType(v: String): AttachmentType = AttachmentType.valueOf(v)

    @TypeConverter fun engineTypeToString(v: EngineType): String = v.name
    @TypeConverter fun stringToEngineType(v: String): EngineType = EngineType.valueOf(v)

    @TypeConverter fun verificationStatusToString(v: VerificationStatus): String = v.name
    @TypeConverter fun stringToVerificationStatus(v: String): VerificationStatus = VerificationStatus.valueOf(v)

    @TypeConverter fun learningQueueStatusToString(v: LearningQueueStatus): String = v.name
    @TypeConverter fun stringToLearningQueueStatus(v: String): LearningQueueStatus = LearningQueueStatus.valueOf(v)

    @TypeConverter
    fun pointsToString(points: List<Pair<Float, Float>>): String =
        points.joinToString(";") { "${it.first},${it.second}" }

    @TypeConverter
    fun stringToPoints(raw: String): List<Pair<Float, Float>> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").map { pair ->
            val (x, y) = pair.split(",")
            x.toFloat() to y.toFloat()
        }
    }

    @TypeConverter
    fun embeddingToString(embedding: FloatArray): String =
        embedding.joinToString(",")

    @TypeConverter
    fun stringToEmbedding(raw: String): FloatArray {
        if (raw.isBlank()) return FloatArray(0)
        return raw.split(",").map { it.toFloat() }.toFloatArray()
    }
}
