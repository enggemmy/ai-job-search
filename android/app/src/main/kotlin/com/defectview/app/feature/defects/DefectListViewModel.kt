package com.defectview.app.feature.defects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.defectview.app.data.repository.DefectRepository
import com.defectview.domain.model.Defect
import com.defectview.domain.model.DefectSeverity
import com.defectview.domain.model.DefectStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DefectFilter(
    val query: String = "",
    val status: DefectStatus? = null,
    val severity: DefectSeverity? = null
)

/** Search/filter is applied client-side over the live [DefectRepository.observeAll] stream -
 * the defect list is small enough (a single project's worth of on-device records, not a
 * server-scale table) that this stays instant and keeps the query logic in one obvious place
 * instead of juggling two live Flow sources. */
class DefectListViewModel(repository: DefectRepository) : ViewModel() {

    private val _filter = MutableStateFlow(DefectFilter())
    val filter: StateFlow<DefectFilter> = _filter

    val defects: StateFlow<List<Defect>> = combine(repository.observeAll(), _filter) { all, f ->
        all.filter { defect ->
            (f.query.isBlank() ||
                defect.title.contains(f.query, ignoreCase = true) ||
                defect.description.contains(f.query, ignoreCase = true) ||
                defect.defectId.contains(f.query, ignoreCase = true)) &&
                (f.status == null || defect.status == f.status) &&
                (f.severity == null || defect.severity == f.severity)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(query: String) {
        _filter.value = _filter.value.copy(query = query)
    }

    fun setStatusFilter(status: DefectStatus?) {
        _filter.value = _filter.value.copy(status = status)
    }

    fun setSeverityFilter(severity: DefectSeverity?) {
        _filter.value = _filter.value.copy(severity = severity)
    }

    class Factory(private val repository: DefectRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DefectListViewModel(repository) as T
    }
}
