package com.defectview.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.defectview.domain.model.EngineType

@Entity(tableName = "model_versions")
data class ModelVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val versionLabel: String,
    val engineType: EngineType,
    val trainingExampleCount: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val notes: String
)
