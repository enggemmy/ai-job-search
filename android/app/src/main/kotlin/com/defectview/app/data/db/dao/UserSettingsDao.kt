package com.defectview.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.defectview.app.data.db.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingsDao {
    @Upsert
    suspend fun upsert(settings: UserSettingsEntity)

    @Query("SELECT * FROM user_settings WHERE id = ${UserSettingsEntity.SINGLETON_ID}")
    fun observe(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = ${UserSettingsEntity.SINGLETON_ID}")
    suspend fun get(): UserSettingsEntity?
}
