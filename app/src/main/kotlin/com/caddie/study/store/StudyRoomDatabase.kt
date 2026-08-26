package com.caddie.study.store

import androidx.room.Database
import androidx.room.AutoMigration
import androidx.room.RoomDatabase
import com.caddie.study.store.entity.*

@Database(
    entities = [
        StudySessionEntity::class,
        StudyDraftEntity::class,
        StudyResponseEntity::class,
        StudyObservationEntity::class,
        StudyTrialMarkerEntity::class,
        StudyInterviewNoteEntity::class,
        StudyConsentEntity::class,
        StudyCourseBonusEntity::class,
        StudyAuditEntity::class,
    ],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
    exportSchema = true,
)
abstract class StudyRoomDatabase : RoomDatabase() {
    abstract fun sessionDao(): StudySessionDao
    abstract fun draftDao(): StudyDraftDao
    abstract fun responseDao(): StudyResponseDao
    abstract fun observationDao(): StudyObservationDao
    abstract fun interviewDao(): StudyInterviewDao
    abstract fun consentDao(): StudyConsentDao
    abstract fun courseBonusDao(): StudyCourseBonusDao
    abstract fun auditDao(): StudyAuditDao
    abstract fun deletionDao(): StudyDeletionDao
}
