package com.defectview.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Single-row table (always id = 1) holding device-local, non-project preferences. */
@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val inspectorName: String,
    val languageTag: String,
    val useDarkTheme: Boolean?,
    val activeModelVersionId: Long?
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
