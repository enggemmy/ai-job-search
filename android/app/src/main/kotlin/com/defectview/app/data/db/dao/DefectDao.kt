package com.defectview.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.defectview.app.data.db.entity.DefectEntity
import com.defectview.domain.model.DefectSeverity
import com.defectview.domain.model.DefectStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DefectDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(defect: DefectEntity): Long

    @Update
    suspend fun update(defect: DefectEntity)

    @Delete
    suspend fun delete(defect: DefectEntity)

    @Query("SELECT * FROM defects WHERE id = :id")
    suspend fun getById(id: Long): DefectEntity?

    @Query("SELECT defectId FROM defects ORDER BY id DESC LIMIT 1")
    suspend fun getLastDefectId(): String?

    @Query("SELECT * FROM defects ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DefectEntity>>

    @Query("SELECT * FROM defects WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeForProject(projectId: Long): Flow<List<DefectEntity>>

    @Query("SELECT * FROM defects WHERE inspectionId = :inspectionId ORDER BY createdAt DESC")
    fun observeForInspection(inspectionId: Long): Flow<List<DefectEntity>>

    @Query("SELECT * FROM defects WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: DefectStatus): Flow<List<DefectEntity>>

    @Query("SELECT * FROM defects WHERE severity = :severity AND status != 'CLOSED' ORDER BY createdAt DESC")
    fun observeOpenBySeverity(severity: DefectSeverity): Flow<List<DefectEntity>>

    @Query("""
        SELECT * FROM defects
        WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR defectId LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
    """)
    fun search(query: String): Flow<List<DefectEntity>>

    @Query("SELECT COUNT(*) FROM defects WHERE status = :status")
    fun observeCountByStatus(status: DefectStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM defects WHERE severity = :severity AND status != 'CLOSED'")
    fun observeOpenCountBySeverity(severity: DefectSeverity): Flow<Int>
}
