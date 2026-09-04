package com.defectview.app.data.repository

import com.defectview.app.data.db.dao.LearningQueueDao
import com.defectview.app.data.db.entity.LearningQueueEntity
import com.defectview.domain.learning.LearningQueueStateMachine
import com.defectview.domain.model.LearningQueueStatus

class LearningQueueRepository(private val learningQueueDao: LearningQueueDao) {

    fun observeByStatus(status: LearningQueueStatus) = learningQueueDao.observeByStatus(status)
    fun observeAll() = learningQueueDao.observeAll()

    /** Applies [LearningQueueStateMachine]'s legal-transition rule before writing - never lets
     * the UI push an entry through an invalid state (e.g. DISCARDED back to PENDING). */
    suspend fun transition(entry: LearningQueueEntity, to: LearningQueueStatus) {
        val newStatus = LearningQueueStateMachine.transition(entry.status, to)
        learningQueueDao.update(
            entry.copy(status = newStatus, processedAt = if (newStatus != LearningQueueStatus.PENDING) System.currentTimeMillis() else null)
        )
    }
}
