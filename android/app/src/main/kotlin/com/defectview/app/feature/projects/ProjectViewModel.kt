package com.defectview.app.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.defectview.app.data.repository.ProjectRepository
import com.defectview.domain.model.Project
import com.defectview.domain.model.ProjectStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectViewModel(private val repository: ProjectRepository) : ViewModel() {

    val projects: StateFlow<List<Project>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState

    fun loadProject(id: Long, onLoaded: (Project?) -> Unit) {
        viewModelScope.launch { onLoaded(repository.getById(id)) }
    }

    fun save(
        existingId: Long?,
        projectNumber: String,
        name: String,
        client: String,
        consultant: String,
        contractor: String,
        location: String,
        description: String
    ) {
        if (projectNumber.isBlank() || name.isBlank()) {
            _saveState.value = SaveState.Error("Project number and name are required.")
            return
        }
        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            val project = Project(
                id = existingId ?: 0,
                projectNumber = projectNumber.trim(),
                name = name.trim(),
                client = client.trim(),
                consultant = consultant.trim(),
                contractor = contractor.trim(),
                location = location.trim(),
                description = description.trim(),
                logoPath = null,
                status = ProjectStatus.ACTIVE,
                createdAt = System.currentTimeMillis()
            )
            if (existingId == null) {
                repository.create(project)
            } else {
                repository.update(project)
            }
            _saveState.value = SaveState.Saved
        }
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }

    sealed class SaveState {
        data object Idle : SaveState()
        data object Saving : SaveState()
        data object Saved : SaveState()
        data class Error(val message: String) : SaveState()
    }

    class Factory(private val repository: ProjectRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProjectViewModel(repository) as T
    }
}
