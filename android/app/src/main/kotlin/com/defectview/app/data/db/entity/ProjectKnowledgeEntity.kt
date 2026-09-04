package com.defectview.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single piece of project-specific reference material (a spec clause, a method statement
 * excerpt, an ITP note, a material/sample approval) the reasoning engine may cite when
 * generating a recommendation (spec section 10). [linkedDefectTypeKey], when set, matches a
 * [com.defectview.domain.taxonomy.DefectTaxonomy] key so the engine can find an exact override;
 * left null, the entry is just project reference material browsable in the app.
 *
 * Absence of a matching row is exactly what makes the reasoning engine fall back to "Verify
 * against the approved project specification and method statement." - nothing here is ever
 * invented on the app's behalf.
 */
@Entity(
    tableName = "project_knowledge",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId"), Index("linkedDefectTypeKey")]
)
data class ProjectKnowledgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val title: String,
    val content: String,
    val linkedDefectTypeKey: String?,
    val createdAt: Long
)
