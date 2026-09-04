package com.defectview.app.data.repository

import com.defectview.app.data.db.dao.DefectDao
import com.defectview.app.data.mapper.toDomain
import com.defectview.app.data.mapper.toEntity
import com.defectview.domain.id.DefectIdGenerator
import com.defectview.domain.model.Defect
import com.defectview.domain.model.DefectSeverity
import com.defectview.domain.model.DefectStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefectRepository(private val defectDao: DefectDao) {

    fun observeAll(): Flow<List<Defect>> =
        defectDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeForProject(projectId: Long): Flow<List<Defect>> =
        defectDao.observeForProject(projectId).map { list -> list.map { it.toDomain() } }

    fun observeForInspection(inspectionId: Long): Flow<List<Defect>> =
        defectDao.observeForInspection(inspectionId).map { list -> list.map { it.toDomain() } }

    fun observeByStatus(status: DefectStatus): Flow<List<Defect>> =
        defectDao.observeByStatus(status).map { list -> list.map { it.toDomain() } }

    fun observeOpenBySeverity(severity: DefectSeverity): Flow<List<Defect>> =
        defectDao.observeOpenBySeverity(severity).map { list -> list.map { it.toDomain() } }

    fun search(query: String): Flow<List<Defect>> =
        defectDao.search(query).map { list -> list.map { it.toDomain() } }

    fun observeCountByStatus(status: DefectStatus): Flow<Int> = defectDao.observeCountByStatus(status)

    fun observeOpenCountBySeverity(severity: DefectSeverity): Flow<Int> = defectDao.observeOpenCountBySeverity(severity)

    suspend fun getById(id: Long): Defect? = defectDao.getById(id)?.toDomain()

    /**
     * Generates the next DV-###### id and inserts the defect. This app has a single local
     * writer (no multi-device sync yet - spec section 11), so a plain read-then-insert is safe;
     * if concurrent writers are ever introduced this must move inside a Room @Transaction.
     */
    suspend fun create(defect: Defect): Defect {
        val lastId = defectDao.getLastDefectId()
        val lastSequence = lastId?.let { DefectIdGenerator.parseSequence(it) } ?: 0L
        val defectId = DefectIdGenerator.next(lastSequence)
        val withId = defect.copy(defectId = defectId)
        val rowId = defectDao.insert(withId.toEntity())
        return withId.copy(id = rowId)
    }

    suspend fun update(defect: Defect) = defectDao.update(defect.toEntity())

    suspend fun updateStatus(defect: Defect, newStatus: DefectStatus, closedAt: Long? = null) =
        defectDao.update(defect.copy(status = newStatus, updatedAt = System.currentTimeMillis(), closedAt = closedAt).toEntity())

    suspend fun delete(defect: Defect) = defectDao.delete(defect.toEntity())
}
