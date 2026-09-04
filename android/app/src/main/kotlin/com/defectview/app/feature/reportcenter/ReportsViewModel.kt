package com.defectview.app.feature.reportcenter

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.defectview.app.data.repository.DefectRepository
import com.defectview.app.data.repository.ProjectRepository
import com.defectview.app.pdf.DefectRegisterPdfGenerator
import com.defectview.domain.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ReportsViewModel(
    private val projectRepository: ProjectRepository,
    private val defectRepository: DefectRepository,
    private val appContext: Context
) : ViewModel() {

    val projects = projectRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _reportState = MutableStateFlow<ReportState>(ReportState.Idle)
    val reportState: StateFlow<ReportState> = _reportState

    fun generateDefectRegister(project: Project) {
        viewModelScope.launch {
            _reportState.value = ReportState.Generating
            try {
                val defects = defectRepository.observeForProject(project.id).first()
                val file = withContext(Dispatchers.Default) {
                    val reportsDir = File(appContext.filesDir, "reports").apply { mkdirs() }
                    val output = File(reportsDir, "defect_register_${project.projectNumber}.pdf")
                    DefectRegisterPdfGenerator.generate(output, project, defects)
                    output
                }
                _reportState.value = ReportState.Ready(file.absolutePath)
            } catch (e: Exception) {
                _reportState.value = ReportState.Error(e.message ?: "Could not generate the report.")
            }
        }
    }

    fun resetReportState() {
        _reportState.value = ReportState.Idle
    }

    sealed class ReportState {
        data object Idle : ReportState()
        data object Generating : ReportState()
        data class Ready(val filePath: String) : ReportState()
        data class Error(val message: String) : ReportState()
    }

    class Factory(
        private val projectRepository: ProjectRepository,
        private val defectRepository: DefectRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReportsViewModel(projectRepository, defectRepository, appContext) as T
    }
}
