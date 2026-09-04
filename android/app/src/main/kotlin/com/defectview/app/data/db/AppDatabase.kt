package com.defectview.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.defectview.app.data.db.dao.AIAnalysisDao
import com.defectview.app.data.db.dao.AnnotationDao
import com.defectview.app.data.db.dao.AttachmentDao
import com.defectview.app.data.db.dao.DefectDao
import com.defectview.app.data.db.dao.InspectionDao
import com.defectview.app.data.db.dao.LearningQueueDao
import com.defectview.app.data.db.dao.ModelVersionDao
import com.defectview.app.data.db.dao.ProjectDao
import com.defectview.app.data.db.dao.ProjectKnowledgeDao
import com.defectview.app.data.db.dao.UserSettingsDao
import com.defectview.app.data.db.dao.VerifiedExampleDao
import com.defectview.app.data.db.entity.AIAnalysisEntity
import com.defectview.app.data.db.entity.AIDetectionEntity
import com.defectview.app.data.db.entity.AnnotationEntity
import com.defectview.app.data.db.entity.AttachmentEntity
import com.defectview.app.data.db.entity.DefectEntity
import com.defectview.app.data.db.entity.InspectionEntity
import com.defectview.app.data.db.entity.LearningQueueEntity
import com.defectview.app.data.db.entity.ModelVersionEntity
import com.defectview.app.data.db.entity.ProjectEntity
import com.defectview.app.data.db.entity.ProjectKnowledgeEntity
import com.defectview.app.data.db.entity.UserSettingsEntity
import com.defectview.app.data.db.entity.VerifiedExampleEntity

@Database(
    entities = [
        ProjectEntity::class,
        InspectionEntity::class,
        DefectEntity::class,
        AnnotationEntity::class,
        AttachmentEntity::class,
        AIAnalysisEntity::class,
        AIDetectionEntity::class,
        VerifiedExampleEntity::class,
        LearningQueueEntity::class,
        ModelVersionEntity::class,
        UserSettingsEntity::class,
        ProjectKnowledgeEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun defectDao(): DefectDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun aiAnalysisDao(): AIAnalysisDao
    abstract fun verifiedExampleDao(): VerifiedExampleDao
    abstract fun learningQueueDao(): LearningQueueDao
    abstract fun modelVersionDao(): ModelVersionDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun projectKnowledgeDao(): ProjectKnowledgeDao

    companion object {
        private const val DATABASE_NAME = "defect_view.db"

        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
    }
}
