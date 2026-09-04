package com.defectview.app.data.repository

import com.defectview.app.data.db.dao.InspectionDao
import com.defectview.app.data.mapper.toDomain
import com.defectview.app.data.mapper.toEntity
import com.defectview.domain.model.Inspection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class InspectionRepository(private val inspectionDao: InspectionDao) {

    fun observeForProject(projectId: Long): Flow<List<Inspection>> =
        inspectionDao.observeForProject(projectId).map { list -> list.map { it.toDomain() } }

    fun observeRecent(limit: Int = 10): Flow<List<Inspection>> =
        inspectionDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    fun observeCount(): Flow<Int> = inspectionDao.observeCount()

    suspend fun getById(id: Long): Inspection? = inspectionDao.getById(id)?.toDomain()

    suspend fun create(inspection: Inspection): Long = inspectionDao.insert(inspection.toEntity())

    suspend fun update(inspection: Inspection) = inspectionDao.update(inspection.toEntity())
}
