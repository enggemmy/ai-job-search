package com.defectview.app.data.repository

import com.defectview.app.data.db.dao.ProjectDao
import com.defectview.app.data.mapper.toDomain
import com.defectview.app.data.mapper.toEntity
import com.defectview.domain.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProjectRepository(private val projectDao: ProjectDao) {

    fun observeAll(): Flow<List<Project>> =
        projectDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun search(query: String): Flow<List<Project>> =
        projectDao.search(query).map { list -> list.map { it.toDomain() } }

    fun observeCount(): Flow<Int> = projectDao.observeCount()

    suspend fun getById(id: Long): Project? = projectDao.getById(id)?.toDomain()

    suspend fun create(project: Project): Long = projectDao.insert(project.toEntity())

    suspend fun update(project: Project) = projectDao.update(project.toEntity())

    suspend fun delete(project: Project) = projectDao.delete(project.toEntity())
}
