package com.defectview.app.feature.defects

import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.defectview.app.data.repository.AnnotationRepository
import com.defectview.app.data.repository.AttachmentRepository
import com.defectview.app.data.repository.DefectRepository
import com.defectview.app.data.storage.ImageStorageManager
import com.defectview.app.feature.editor.AnnotationDraft
import com.defectview.app.feature.editor.AnnotationRenderer
import com.defectview.domain.model.AttachmentType
import com.defectview.domain.model.Defect
import com.defectview.domain.model.DefectCategory
import com.defectview.domain.model.DefectPriority
import com.defectview.domain.model.DefectSeverity
import com.defectview.domain.model.DefectStatus
import com.defectview.domain.model.Trade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DefectFormViewModel(
    private val defectRepository: DefectRepository,
    private val annotationRepository: AnnotationRepository,
    private val attachmentRepository: AttachmentRepository,
    private val imageStorageManager: ImageStorageManager
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
        inspectorComments: String
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

            _saveState.value = SaveState.Saved(created)
        }
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
        private val imageStorageManager: ImageStorageManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DefectFormViewModel(defectRepository, annotationRepository, attachmentRepository, imageStorageManager) as T
    }
}
