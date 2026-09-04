package com.defectview.app.data.repository

import com.defectview.app.data.db.dao.ProjectKnowledgeDao
import com.defectview.app.data.db.entity.ProjectKnowledgeEntity
import com.defectview.domain.reasoning.ProjectKnowledge

class ProjectKnowledgeRepository(private val dao: ProjectKnowledgeDao) {

    fun observeForProject(projectId: Long) = dao.observeForProject(projectId)

    suspend fun add(projectId: Long, title: String, content: String, linkedDefectTypeKey: String?) {
        dao.insert(
            ProjectKnowledgeEntity(
                projectId = projectId,
                title = title.trim(),
                content = content.trim(),
                linkedDefectTypeKey = linkedDefectTypeKey?.trim()?.ifBlank { null },
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun delete(entry: ProjectKnowledgeEntity) = dao.delete(entry)

    /**
     * Loads every knowledge entry for [projectId] once and wraps it as an in-memory
     * [ProjectKnowledge] for the reasoning engine - the domain interface is synchronous by
     * design (it's called mid-computation, not mid-suspend), so the Room read happens up front.
     */
    suspend fun loadForReasoning(projectId: Long): ProjectKnowledge {
        val entries = dao.getAllForProject(projectId)
        val byDefectType = entries
            .filter { it.linkedDefectTypeKey != null }
            .associateBy { it.linkedDefectTypeKey }
        return object : ProjectKnowledge {
            override fun recommendationFor(defectTypeKey: String): String? =
                byDefectType[defectTypeKey]?.content
            override fun hasSpecFor(defectTypeKey: String): Boolean =
                byDefectType.containsKey(defectTypeKey)
        }
    }
}
