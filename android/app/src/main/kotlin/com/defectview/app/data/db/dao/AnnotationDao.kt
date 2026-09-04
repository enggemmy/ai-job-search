package com.defectview.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.defectview.app.data.db.entity.AnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(annotation: AnnotationEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(annotations: List<AnnotationEntity>): List<Long>

    @Update
    suspend fun update(annotation: AnnotationEntity)

    @Delete
    suspend fun delete(annotation: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE defectId = :defectId")
    suspend fun deleteAllForDefect(defectId: Long)

    @Query("SELECT * FROM annotations WHERE defectId = :defectId ORDER BY orderIndex ASC")
    fun observeForDefect(defectId: Long): Flow<List<AnnotationEntity>>
}
