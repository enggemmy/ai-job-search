package com.defectview.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.defectview.domain.model.ProjectStatus

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectNumber: String,
    val name: String,
    val client: String,
    val consultant: String,
    val contractor: String,
    val location: String,
    val description: String,
    val logoPath: String?,
    val status: ProjectStatus,
    val createdAt: Long
)
