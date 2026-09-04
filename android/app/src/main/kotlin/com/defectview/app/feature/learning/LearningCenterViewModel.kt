package com.defectview.app.feature.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.defectview.app.data.db.dao.TradeCount
import com.defectview.app.data.db.entity.LearningQueueEntity
import com.defectview.app.data.db.entity.ModelVersionEntity
import com.defectview.app.data.db.entity.VerifiedExampleEntity
import com.defectview.app.data.repository.LearningQueueRepository
import com.defectview.app.data.repository.ModelVersionRepository
import com.defectview.app.data.repository.VerifiedExampleRepository
import com.defectview.domain.model.LearningQueueStatus
import com.defectview.domain.model.VerificationStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LearningCenterState(
    val totalVerifiedExamples: Int = 0,
    val approvedCount: Int = 0,
    val correctedCount: Int = 0,
    val examplesByTrade: List<Pair<String, Int>> = emptyList(),
    val pendingQueue: List<LearningQueueEntity> = emptyList(),
    val queuedForTraining: List<LearningQueueEntity> = emptyList(),
    val modelVersions: List<ModelVersionEntity> = emptyList(),
    val activeVersion: ModelVersionEntity? = null
)

class LearningCenterViewModel(
    private val verifiedExampleRepository: VerifiedExampleRepository,
    private val learningQueueRepository: LearningQueueRepository,
    private val modelVersionRepository: ModelVersionRepository
) : ViewModel() {

    val state: StateFlow<LearningCenterState> = combine(
        verifiedExampleRepository.observeAll(),
        verifiedExampleRepository.observeCountByTrade(),
        learningQueueRepository.observeByStatus(LearningQueueStatus.PENDING),
        learningQueueRepository.observeByStatus(LearningQueueStatus.QUEUED_FOR_TRAINING),
        modelVersionRepository.observeAll(),
        modelVersionRepository.observeActive()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allExamples = values[0] as List<VerifiedExampleEntity>
        @Suppress("UNCHECKED_CAST")
        val byTrade = values[1] as List<TradeCount>
        @Suppress("UNCHECKED_CAST")
        val pending = values[2] as List<LearningQueueEntity>
        @Suppress("UNCHECKED_CAST")
        val queued = values[3] as List<LearningQueueEntity>
        @Suppress("UNCHECKED_CAST")
        val versions = values[4] as List<ModelVersionEntity>
        val active = values[5] as ModelVersionEntity?

        LearningCenterState(
            totalVerifiedExamples = allExamples.size,
            approvedCount = allExamples.count { it.verificationStatus == VerificationStatus.APPROVED },
            correctedCount = allExamples.count { it.verificationStatus == VerificationStatus.CORRECTED },
            examplesByTrade = byTrade.map { it.trade.name.replace('_', ' ') to it.count },
            pendingQueue = pending,
            queuedForTraining = queued,
            modelVersions = versions,
            activeVersion = active
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LearningCenterState())

    fun queueForTraining(entry: LearningQueueEntity) {
        viewModelScope.launch { learningQueueRepository.transition(entry, LearningQueueStatus.QUEUED_FOR_TRAINING) }
    }

    fun discard(entry: LearningQueueEntity) {
        viewModelScope.launch { learningQueueRepository.transition(entry, LearningQueueStatus.DISCARDED) }
    }

    fun runLocalLearningUpdate() {
        viewModelScope.launch {
            val previousCount = state.value.activeVersion?.trainingExampleCount ?: 0
            modelVersionRepository.runLocalLearningUpdate(previousCount)
        }
    }

    fun rollbackTo(versionId: Long) {
        viewModelScope.launch { modelVersionRepository.activate(versionId) }
    }

    class Factory(
        private val verifiedExampleRepository: VerifiedExampleRepository,
        private val learningQueueRepository: LearningQueueRepository,
        private val modelVersionRepository: ModelVersionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LearningCenterViewModel(verifiedExampleRepository, learningQueueRepository, modelVersionRepository) as T
    }
}
