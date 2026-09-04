package com.defectview.app.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.defectview.app.data.db.entity.DefectEntity
import com.defectview.app.data.db.entity.ProjectEntity
import com.defectview.domain.model.DefectCategory
import com.defectview.domain.model.DefectPriority
import com.defectview.domain.model.DefectSeverity
import com.defectview.domain.model.DefectStatus
import com.defectview.domain.model.ProjectStatus
import com.defectview.domain.model.Trade
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Instrumented Room round-trip tests: real enum TypeConverters, foreign keys, and query
 * shapes against an actual SQLite database, not a fake. Requires a device/emulator to run
 * (`./gradlew :app:connectedAndroidTest`) - this sandbox has no Android SDK or emulator, so
 * this file is written and reviewed but has NOT been executed. See NETWORK_LIMITATIONS.md.
 */
@RunWith(AndroidJUnit4::class)
class DefectDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    private suspend fun insertProject(): Long = db.projectDao().insert(
        ProjectEntity(
            projectNumber = "P-001",
            name = "Test Tower",
            client = "Client",
            consultant = "Consultant",
            contractor = "Contractor",
            location = "Riyadh",
            description = "",
            logoPath = null,
            status = ProjectStatus.ACTIVE,
            createdAt = 0L
        )
    )

    private fun defect(projectId: Long, defectId: String) = DefectEntity(
        defectId = defectId,
        projectId = projectId,
        inspectionId = null,
        location = "Level 3",
        title = "Excess Mortar",
        description = "Excess mortar observed",
        category = DefectCategory.WORKMANSHIP,
        trade = Trade.MASONRY,
        severity = DefectSeverity.HIGH,
        severityIsAiSuggested = true,
        recommendation = "Remove and reapply plaster.",
        status = DefectStatus.OPEN,
        responsibleParty = "",
        priority = DefectPriority.NORMAL,
        targetDate = null,
        reportedBy = "Inspector",
        inspectorComments = "",
        closureComments = "",
        beforePhotoPath = null,
        afterPhotoPath = null,
        createdAt = 0L,
        updatedAt = 0L,
        closedAt = null
    )

    @Test
    fun insertAndReadBack_roundTripsEnumsAndFlags() = runBlocking {
        val projectId = insertProject()
        db.defectDao().insert(defect(projectId, "DV-000001"))

        val loaded = db.defectDao().observeForProject(projectId).first().single()
        assertEquals(DefectSeverity.HIGH, loaded.severity)
        assertEquals(DefectStatus.OPEN, loaded.status)
        assertEquals(Trade.MASONRY, loaded.trade)
        assertEquals(true, loaded.severityIsAiSuggested)
    }

    @Test
    fun getLastDefectId_returnsMostRecentlyInsertedId() = runBlocking {
        val projectId = insertProject()
        assertNull(db.defectDao().getLastDefectId())

        db.defectDao().insert(defect(projectId, "DV-000001"))
        db.defectDao().insert(defect(projectId, "DV-000002"))

        assertEquals("DV-000002", db.defectDao().getLastDefectId())
    }

    @Test
    fun deletingProject_cascadesToItsDefects() = runBlocking {
        val projectId = insertProject()
        db.defectDao().insert(defect(projectId, "DV-000001"))

        val project = db.projectDao().getById(projectId)!!
        db.projectDao().delete(project)

        assertEquals(0, db.defectDao().observeForProject(projectId).first().size)
    }

    @Test
    fun observeCountByStatus_reflectsOnlyMatchingRows() = runBlocking {
        val projectId = insertProject()
        db.defectDao().insert(defect(projectId, "DV-000001").copy(status = DefectStatus.OPEN))
        db.defectDao().insert(defect(projectId, "DV-000002").copy(status = DefectStatus.CLOSED))

        assertEquals(1, db.defectDao().observeCountByStatus(DefectStatus.OPEN).first())
        assertEquals(1, db.defectDao().observeCountByStatus(DefectStatus.CLOSED).first())
    }
}
