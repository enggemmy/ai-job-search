package com.defectview.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.defectview.app.data.db.entity.ProjectKnowledgeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectKnowledgeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: ProjectKnowledgeEntity): Long

    @Delete
    suspend fun delete(entry: ProjectKnowledgeEntity)

    @Query("SELECT * FROM project_knowledge WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeForProject(projectId: Long): Flow<List<ProjectKnowledgeEntity>>

    @Query("SELECT * FROM project_knowledge WHERE projectId = :projectId")
    suspend fun getAllForProject(projectId: Long): List<ProjectKnowledgeEntity>
}
