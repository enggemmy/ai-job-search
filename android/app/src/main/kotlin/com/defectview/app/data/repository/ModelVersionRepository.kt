package com.defectview.app.data.repository

import com.defectview.app.data.db.dao.LearningQueueDao
import com.defectview.app.data.db.dao.ModelVersionDao
import com.defectview.app.data.db.entity.ModelVersionEntity
import com.defectview.domain.model.EngineType
import com.defectview.domain.model.LearningQueueStatus

class ModelVersionRepository(
    private val modelVersionDao: ModelVersionDao,
    private val learningQueueDao: LearningQueueDao
) {
    fun observeAll() = modelVersionDao.observeAll()
    fun observeActive() = modelVersionDao.observeActive()

    /** Rolls back to (i.e. reactivates) a previously recorded version. Never re-runs training -
     * this only changes which similarity-search dataset snapshot is considered current. */
    suspend fun activate(versionId: Long) = modelVersionDao.activate(versionId)

    /**
     * The "run local learning update" action (spec section 8): moves every QUEUED_FOR_TRAINING
     * entry to INCLUDED_IN_MODEL and records a new, active [ModelVersionEntity] whose
     * trainingExampleCount is exactly how many verified examples are now part of the dataset.
     *
     * This is NOT model retraining - there is no neural network here to retrain. It is
     * consolidating the verified-example dataset that [com.defectview.domain.learning.SimilaritySearch]
     * draws on into a new, named, rollback-able snapshot. The distinction is deliberate and
     * documented so the UI never implies more than this actually does.
     */
    suspend fun runLocalLearningUpdate(previousExampleCount: Int): ModelVersionEntity {
        val queued = learningQueueDao.getByStatus(LearningQueueStatus.QUEUED_FOR_TRAINING)
        queued.forEach { entry ->
            learningQueueDao.update(entry.copy(status = LearningQueueStatus.INCLUDED_IN_MODEL, processedAt = System.currentTimeMillis()))
        }

        val newVersion = ModelVersionEntity(
            versionLabel = "v${System.currentTimeMillis()}",
            engineType = EngineType.SIMILARITY_SEARCH,
            trainingExampleCount = previousExampleCount + queued.size,
            isActive = true,
            createdAt = System.currentTimeMillis(),
            notes = "Consolidated ${queued.size} newly verified example(s) into the similarity-search dataset."
        )
        modelVersionDao.deactivateAll()
        val id = modelVersionDao.insert(newVersion)
        return newVersion.copy(id = id)
    }
}
