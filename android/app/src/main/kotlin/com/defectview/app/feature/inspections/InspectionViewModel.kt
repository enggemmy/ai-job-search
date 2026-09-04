package com.defectview.app.feature.inspections

import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.defectview.app.data.repository.AIAnalysisRepository
import com.defectview.app.data.repository.InspectionRepository
import com.defectview.app.data.repository.ProjectRepository
import com.defectview.app.vision.toImageSample
import com.defectview.domain.model.DefectDetection
import com.defectview.domain.model.Inspection
import com.defectview.domain.model.Trade
import com.defectview.domain.vision.VisionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InspectionViewModel(
    private val inspectionRepository: InspectionRepository,
    projectRepository: ProjectRepository,
    private val visionEngine: VisionEngine,
    private val aiAnalysisRepository: AIAnalysisRepository
) : ViewModel() {

    val projects = projectRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState

    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState

    fun inspectionsForProject(projectId: Long) = inspectionRepository.observeForProject(projectId)

    /** Runs the local vision engine on a captured/imported photo. Nothing is persisted yet -
     * the inspection this analysis belongs to doesn't have a row id until [save] succeeds. */
    fun analyzePhoto(photoPath: String) {
        viewModelScope.launch {
            _analysisState.value = AnalysisState.Analyzing
            val detections = withContext(Dispatchers.Default) {
                val bitmap = BitmapFactory.decodeFile(photoPath) ?: return@withContext emptyList()
                visionEngine.analyze(bitmap.toImageSample())
            }
            _analysisState.value = AnalysisState.Done(detections)
        }
    }

    fun resetAnalysisState() {
        _analysisState.value = AnalysisState.Idle
    }

    fun save(
        projectId: Long?,
        location: String,
        area: String,
        trade: Trade,
        notes: String,
        inspectorName: String,
        analyzedPhotoPath: String? = null
    ) {
        if (projectId == null) {
            _saveState.value = SaveState.Error("Select a project first.")
            return
        }
        if (location.isBlank()) {
            _saveState.value = SaveState.Error("Location is required.")
            return
        }
        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            val inspection = Inspection(
                projectId = projectId,
                location = location.trim(),
                area = area.trim(),
                trade = trade,
                notes = notes.trim(),
                inspectorName = inspectorName.ifBlank { "Unnamed inspector" },
                createdAt = System.currentTimeMillis()
            )
            val id = inspectionRepository.create(inspection)

            val analysis = _analysisState.value
            if (analyzedPhotoPath != null && analysis is AnalysisState.Done) {
                aiAnalysisRepository.record(
                    inspectionId = id,
                    photoPath = analyzedPhotoPath,
                    engineInfo = visionEngine.info,
                    detections = analysis.detections,
                    analyzedAt = System.currentTimeMillis()
                )
            }

            _saveState.value = SaveState.Saved(id)
        }
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }

    sealed class SaveState {
        data object Idle : SaveState()
        data object Saving : SaveState()
        data class Saved(val inspectionId: Long) : SaveState()
        data class Error(val message: String) : SaveState()
    }

    sealed class AnalysisState {
        data object Idle : AnalysisState()
        data object Analyzing : AnalysisState()
        data class Done(val detections: List<DefectDetection>) : AnalysisState()
    }

    class Factory(
        private val inspectionRepository: InspectionRepository,
        private val projectRepository: ProjectRepository,
        private val visionEngine: VisionEngine,
        private val aiAnalysisRepository: AIAnalysisRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            InspectionViewModel(inspectionRepository, projectRepository, visionEngine, aiAnalysisRepository) as T
    }
}
