package com.defectview.app.feature.defects

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.defectview.app.data.repository.AttachmentRepository
import com.defectview.app.data.repository.DefectRepository
import com.defectview.app.data.repository.ProjectRepository
import com.defectview.app.pdf.DefectViewPdfGenerator
import com.defectview.domain.model.Defect
import com.defectview.domain.model.DefectStatus
import com.defectview.domain.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DefectDetailViewModel(
    private val defectId: Long,
    private val defectRepository: DefectRepository,
    private val projectRepository: ProjectRepository,
    private val attachmentRepository: AttachmentRepository,
    private val appContext: Context
) : ViewModel() {

    private val _defect = MutableStateFlow<Defect?>(null)
    val defect: StateFlow<Defect?> = _defect

    private var project: Project? = null

    val attachments = attachmentRepository.observeForDefect(defectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _reportState = MutableStateFlow<ReportState>(ReportState.Idle)
    val reportState: StateFlow<ReportState> = _reportState

    init {
        viewModelScope.launch {
            val loaded = defectRepository.getById(defectId)
            _defect.value = loaded
            project = loaded?.let { projectRepository.getById(it.projectId) }
        }
    }

    private fun refresh() {
        viewModelScope.launch { _defect.value = defectRepository.getById(defectId) }
    }

    fun transitionStatus(newStatus: DefectStatus) {
        val current = _defect.value ?: return
        viewModelScope.launch {
            defectRepository.updateStatus(current, newStatus, closedAt = if (newStatus == DefectStatus.CLOSED) System.currentTimeMillis() else null)
            refresh()
        }
    }

    fun setAfterPhoto(path: String) {
        val current = _defect.value ?: return
        viewModelScope.launch {
            defectRepository.update(current.copy(afterPhotoPath = path, updatedAt = System.currentTimeMillis()))
            refresh()
        }
    }

    /** Records the inspector's closure comments and marks the record verified + closed in one step (spec section 5E). */
    fun verifyAndClose(closureComments: String) {
        val current = _defect.value ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            defectRepository.update(
                current.copy(
                    status = DefectStatus.CLOSED,
                    closureComments = closureComments.trim(),
                    updatedAt = now,
                    closedAt = now
                )
            )
            refresh()
        }
    }

    fun generateReport() {
        val current = _defect.value ?: return
        val currentProject = project ?: return
        viewModelScope.launch {
            _reportState.value = ReportState.Generating
            try {
                val file = withContext(Dispatchers.Default) {
                    val reportsDir = File(appContext.filesDir, "reports").apply { mkdirs() }
                    val output = File(reportsDir, "${current.defectId}.pdf")
                    DefectViewPdfGenerator.generate(output, currentProject, current, attachments.value)
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
        private val defectId: Long,
        private val defectRepository: DefectRepository,
        private val projectRepository: ProjectRepository,
        private val attachmentRepository: AttachmentRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DefectDetailViewModel(defectId, defectRepository, projectRepository, attachmentRepository, appContext) as T
    }
}
