package com.defectview.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.defectview.app.data.db.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(attachment: AttachmentEntity): Long

    @Delete
    suspend fun delete(attachment: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE defectId = :defectId")
    fun observeForDefect(defectId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE inspectionId = :inspectionId")
    fun observeForInspection(inspectionId: Long): Flow<List<AttachmentEntity>>
}
