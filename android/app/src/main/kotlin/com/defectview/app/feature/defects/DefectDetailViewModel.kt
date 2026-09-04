package com.defectview.app.feature.defects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.defectview.app.data.repository.DefectRepository
import com.defectview.domain.model.Defect
import com.defectview.domain.model.DefectStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DefectDetailViewModel(
    private val defectId: Long,
    private val defectRepository: DefectRepository
) : ViewModel() {

    private val _defect = MutableStateFlow<Defect?>(null)
    val defect: StateFlow<Defect?> = _defect

    init {
        viewModelScope.launch { _defect.value = defectRepository.getById(defectId) }
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

    class Factory(private val defectId: Long, private val defectRepository: DefectRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DefectDetailViewModel(defectId, defectRepository) as T
    }
}
