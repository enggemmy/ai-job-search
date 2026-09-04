package com.defectview.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.defectview.app.data.repository.DefectRepository
import com.defectview.app.data.repository.InspectionRepository
import com.defectview.app.data.repository.ProjectRepository
import com.defectview.domain.model.DefectSeverity
import com.defectview.domain.model.DefectStatus
import com.defectview.domain.model.Inspection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardStats(
    val totalProjects: Int = 0,
    val openDefects: Int = 0,
    val highSeverityOpenDefects: Int = 0,
    val inProgressDefects: Int = 0,
    val pendingVerificationDefects: Int = 0,
    val closedDefects: Int = 0,
    val recentInspections: List<Inspection> = emptyList()
)

class DashboardViewModel(
    projectRepository: ProjectRepository,
    inspectionRepository: InspectionRepository,
    defectRepository: DefectRepository
) : ViewModel() {

    val stats: StateFlow<DashboardStats> = combine(
        projectRepository.observeCount(),
        defectRepository.observeCountByStatus(DefectStatus.OPEN),
        defectRepository.observeOpenCountBySeverity(DefectSeverity.HIGH),
        defectRepository.observeCountByStatus(DefectStatus.IN_PROGRESS),
        defectRepository.observeCountByStatus(DefectStatus.PENDING_VERIFICATION),
        defectRepository.observeCountByStatus(DefectStatus.CLOSED),
        inspectionRepository.observeRecent(5)
    ) { values ->
        DashboardStats(
            totalProjects = values[0] as Int,
            openDefects = values[1] as Int,
            highSeverityOpenDefects = values[2] as Int,
            inProgressDefects = values[3] as Int,
            pendingVerificationDefects = values[4] as Int,
            closedDefects = values[5] as Int,
            @Suppress("UNCHECKED_CAST")
            recentInspections = values[6] as List<Inspection>
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    class Factory(
        private val projectRepository: ProjectRepository,
        private val inspectionRepository: InspectionRepository,
        private val defectRepository: DefectRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DashboardViewModel(projectRepository, inspectionRepository, defectRepository) as T
    }
}
