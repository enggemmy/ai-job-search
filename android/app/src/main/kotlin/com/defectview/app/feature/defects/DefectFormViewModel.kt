package com.defectview.app.feature.defects

import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.defectview.app.data.db.entity.VerifiedExampleEntity
import com.defectview.app.data.repository.AnnotationRepository
import com.defectview.app.data.repository.AttachmentRepository
import com.defectview.app.data.repository.DefectRepository
import com.defectview.app.data.repository.VerifiedExampleRepository
import com.defectview.app.data.storage.ImageStorageManager
import com.defectview.app.feature.editor.AnnotationDraft
import com.defectview.app.feature.editor.AnnotationRenderer
import com.defectview.app.vision.cropTo
import com.defectview.app.vision.toImageSample
import com.defectview.domain.model.AttachmentType
import com.defectview.domain.model.Defect
import com.defectview.domain.model.DefectCategory
import com.defectview.domain.model.DefectDetection
import com.defectview.domain.model.DefectPriority
import com.defectview.domain.model.DefectSeverity
import com.defectview.domain.model.DefectStatus
import com.defectview.domain.model.Trade
import com.defectview.domain.model.VerificationStatus
import com.defectview.domain.vision.SimpleFeatureExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DefectFormViewModel(
    private val defectRepository: DefectRepository,
    private val annotationRepository: AnnotationRepository,
    private val attachmentRepository: AttachmentRepository,
    private val imageStorageManager: ImageStorageManager,
    private val verifiedExampleRepository: VerifiedExampleRepository,
    private val visionEngineModelVersion: String
) : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState

    fun create(
        projectId: Long,
        inspectionId: Long?,
        location: String,
        originalPhotoPath: String,
        annotations: List<AnnotationDraft>,
        title: String,
        description: String,
        category: DefectCategory,
        trade: Trade,
        severity: DefectSeverity,
        severityIsAiSuggested: Boolean,
        recommendation: String,
        responsibleParty: String,
        priority: DefectPriority,
        reportedBy: String,
        inspectorComments: String,
        sourceDetection: DefectDetection? = null
    ) {
        if (title.isBlank()) {
            _saveState.value = SaveState.Error("Defect title is required.")
            return
        }
        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            val now = System.currentTimeMillis()
            val created = defectRepository.create(
                Defect(
                    defectId = "", // assigned by the repository
                    projectId = projectId,
                    inspectionId = inspectionId,
                    location = location,
                    title = title.trim(),
                    description = description.trim(),
                    category = category,
                    trade = trade,
                    severity = severity,
                    severityIsAiSuggested = severityIsAiSuggested,
                    recommendation = recommendation.trim(),
                    status = DefectStatus.OPEN,
                    responsibleParty = responsibleParty.trim(),
                    priority = priority,
                    targetDate = null,
                    reportedBy = reportedBy.ifBlank { "Site Inspector" },
                    inspectorComments = inspectorComments.trim(),
                    closureComments = "",
                    beforePhotoPath = originalPhotoPath,
                    afterPhotoPath = null,
                    createdAt = now,
                    updatedAt = now,
                    closedAt = null
                )
            )

            annotationRepository.replaceAllForDefect(created.id, annotations)
            attachmentRepository.attachToDefect(created.id, originalPhotoPath, AttachmentType.ORIGINAL_PHOTO)

            if (annotations.isNotEmpty()) {
                val annotatedPath = withContext(Dispatchers.Default) {
                    val source = BitmapFactory.decodeFile(originalPhotoPath)
                    val rendered = AnnotationRenderer.render(source, annotations)
                    imageStorageManager.saveBitmap(rendered, prefix = "annotated")
                }
                attachmentRepository.attachToDefect(created.id, annotatedPath, AttachmentType.ANNOTATED_PHOTO)
            }

            if (sourceDetection != null) {
                recordVerifiedExample(sourceDetection, originalPhotoPath, category, trade, title)
            }

            _saveState.value = SaveState.Saved(created)
        }
    }

    /**
     * Turns the just-saved defect into a verified example (spec section 4C/8): the inspector
     * either kept the AI's trade/category suggestion (APPROVED) or changed it (CORRECTED) -
     * either way, this is real inspector-confirmed ground truth, never a raw prediction stored
     * as fact. Rejections never reach here at all (the inspector would simply not save a defect
     * from a suggestion they disagreed with).
     */
    private suspend fun recordVerifiedExample(
        detection: DefectDetection,
        originalPhotoPath: String,
        finalCategory: DefectCategory,
        finalTrade: Trade,
        finalTitle: String
    ) {
        val status = if (finalTrade == detection.trade) VerificationStatus.APPROVED else VerificationStatus.CORRECTED
        val (croppedPath, embedding) = withContext(Dispatchers.Default) {
            val source = BitmapFactory.decodeFile(originalPhotoPath)
            val cropped = source.cropTo(detection.boundingBox)
            val path = imageStorageManager.saveBitmap(cropped, prefix = "verified_crop")
            path to SimpleFeatureExtractor.extract(cropped.toImageSample())
        }

        verifiedExampleRepository.recordAndEnqueue(
            VerifiedExampleEntity(
                originalImagePath = originalPhotoPath,
                croppedImagePath = croppedPath,
                aiPredictionHint = detection.defectCategoryHint,
                aiPredictionConfidence = detection.confidenceScore,
                correction = if (status == VerificationStatus.CORRECTED) "Trade changed from ${detection.trade} to $finalTrade" else null,
                finalLabel = finalTitle,
                boxLeft = detection.boundingBox.left,
                boxTop = detection.boundingBox.top,
                boxRight = detection.boundingBox.right,
                boxBottom = detection.boundingBox.bottom,
                trade = finalTrade,
                category = finalCategory,
                embedding = embedding,
                verificationStatus = status,
                modelVersion = visionEngineModelVersion,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }

    sealed class SaveState {
        data object Idle : SaveState()
        data object Saving : SaveState()
        data class Saved(val defect: Defect) : SaveState()
        data class Error(val message: String) : SaveState()
    }

    class Factory(
        private val defectRepository: DefectRepository,
        private val annotationRepository: AnnotationRepository,
        private val attachmentRepository: AttachmentRepository,
        private val imageStorageManager: ImageStorageManager,
        private val verifiedExampleRepository: VerifiedExampleRepository,
        private val visionEngineModelVersion: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DefectFormViewModel(
                defectRepository,
                annotationRepository,
                attachmentRepository,
                imageStorageManager,
                verifiedExampleRepository,
                visionEngineModelVersion
            ) as T
    }
}
