package com.defectview.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.defectview.app.data.db.entity.InspectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InspectionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(inspection: InspectionEntity): Long

    @Update
    suspend fun update(inspection: InspectionEntity)

    @Delete
    suspend fun delete(inspection: InspectionEntity)

    @Query("SELECT * FROM inspections WHERE id = :id")
    suspend fun getById(id: Long): InspectionEntity?

    @Query("SELECT * FROM inspections WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeForProject(projectId: Long): Flow<List<InspectionEntity>>

    @Query("SELECT * FROM inspections ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<InspectionEntity>>

    @Query("SELECT COUNT(*) FROM inspections")
    fun observeCount(): Flow<Int>
}
