package com.defectview.app.data.repository

import com.defectview.app.data.db.dao.LearningQueueDao
import com.defectview.app.data.db.dao.VerifiedExampleDao
import com.defectview.app.data.db.entity.LearningQueueEntity
import com.defectview.app.data.db.entity.VerifiedExampleEntity
import com.defectview.domain.model.LearningQueueStatus

class VerifiedExampleRepository(
    private val verifiedExampleDao: VerifiedExampleDao,
    private val learningQueueDao: LearningQueueDao
) {
    fun observeAll() = verifiedExampleDao.observeAll()
    fun observeCount() = verifiedExampleDao.observeCount()
    fun observeCountByTrade() = verifiedExampleDao.observeCountByTrade()

    suspend fun allForSimilaritySearch() = verifiedExampleDao.getAllForSimilaritySearch()

    /**
     * Records one verified example and immediately enqueues it for a future learning update
     * (spec section 4C/8: only verified examples enter the dataset, never a raw AI prediction -
     * this is called after the inspector has approved or corrected an AI-originated defect, not
     * automatically from every photo).
     */
    suspend fun recordAndEnqueue(entry: VerifiedExampleEntity): Long {
        val id = verifiedExampleDao.insert(entry)
        learningQueueDao.insert(
            LearningQueueEntity(
                verifiedExampleId = id,
                status = LearningQueueStatus.PENDING,
                enqueuedAt = System.currentTimeMillis(),
                processedAt = null
            )
        )
        return id
    }
}
