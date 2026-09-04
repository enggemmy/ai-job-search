package com.defectview.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.defectview.app.data.db.entity.ModelVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelVersionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(version: ModelVersionEntity): Long

    @Update
    suspend fun update(version: ModelVersionEntity)

    @Query("SELECT * FROM model_versions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ModelVersionEntity>>

    @Query("SELECT * FROM model_versions WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<ModelVersionEntity?>

    @Query("UPDATE model_versions SET isActive = 0")
    suspend fun deactivateAll()

    /** Rolls back to [versionId]: deactivates every version, then reactivates the chosen one. */
    @Transaction
    suspend fun activate(versionId: Long) {
        deactivateAll()
        setActive(versionId, true)
    }

    @Query("UPDATE model_versions SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)
}
