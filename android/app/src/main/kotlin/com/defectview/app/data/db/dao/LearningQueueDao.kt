package com.defectview.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.defectview.app.data.db.entity.LearningQueueEntity
import com.defectview.domain.model.LearningQueueStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningQueueDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: LearningQueueEntity): Long

    @Update
    suspend fun update(entry: LearningQueueEntity)

    @Query("SELECT * FROM learning_queue WHERE status = :status ORDER BY enqueuedAt ASC")
    fun observeByStatus(status: LearningQueueStatus): Flow<List<LearningQueueEntity>>

    @Query("SELECT * FROM learning_queue WHERE status = :status ORDER BY enqueuedAt ASC")
    suspend fun getByStatus(status: LearningQueueStatus): List<LearningQueueEntity>

    @Query("SELECT * FROM learning_queue ORDER BY enqueuedAt DESC")
    fun observeAll(): Flow<List<LearningQueueEntity>>
}
