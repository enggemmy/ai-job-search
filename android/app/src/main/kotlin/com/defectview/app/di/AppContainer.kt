package com.defectview.app.di

import android.content.Context
import com.defectview.app.data.db.AppDatabase
import com.defectview.app.data.repository.AIAnalysisRepository
import com.defectview.app.data.repository.AnnotationRepository
import com.defectview.app.data.repository.AttachmentRepository
import com.defectview.app.data.repository.DefectRepository
import com.defectview.app.data.repository.InspectionRepository
import com.defectview.app.data.repository.ProjectRepository
import com.defectview.app.data.storage.ImageStorageManager
import com.defectview.domain.vision.ClassicalVisionEngine
import com.defectview.domain.vision.VisionEngine

/**
 * Deliberately simple, hand-written composition root instead of a DI framework (Hilt/Koin) -
 * the app has a handful of repositories and no complex scoping needs, so a framework would be
 * pure overhead (spec section 15: "avoid unnecessary dependencies", "avoid overengineering").
 */
class AppContainer(context: Context) {
    private val database = AppDatabase.getInstance(context)

    val imageStorageManager = ImageStorageManager(context)

    val projectRepository = ProjectRepository(database.projectDao())
    val inspectionRepository = InspectionRepository(database.inspectionDao())
    val defectRepository = DefectRepository(database.defectDao())
    val attachmentRepository = AttachmentRepository(database.attachmentDao())
    val annotationRepository = AnnotationRepository(database.annotationDao())
    val aiAnalysisRepository = AIAnalysisRepository(database.aiAnalysisDao())

    /** Swappable at this single point when a trained LiteRT/ONNX model becomes available - the
     * rest of the app depends only on the [VisionEngine] interface (spec section 3/4A). */
    val visionEngine: VisionEngine = ClassicalVisionEngine()
}
