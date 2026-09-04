package com.defectview.app.feature.inspections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.defectview.app.data.repository.InspectionRepository
import com.defectview.app.data.repository.ProjectRepository
import com.defectview.domain.model.Inspection
import com.defectview.domain.model.Trade
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InspectionViewModel(
    private val inspectionRepository: InspectionRepository,
    projectRepository: ProjectRepository
) : ViewModel() {

    val projects = projectRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState

    fun inspectionsForProject(projectId: Long) = inspectionRepository.observeForProject(projectId)

    fun save(
        projectId: Long?,
        location: String,
        area: String,
        trade: Trade,
        notes: String,
        inspectorName: String
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

    class Factory(
        private val inspectionRepository: InspectionRepository,
        private val projectRepository: ProjectRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            InspectionViewModel(inspectionRepository, projectRepository) as T
    }
}
