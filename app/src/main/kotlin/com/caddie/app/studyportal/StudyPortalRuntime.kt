package com.caddie.app.studyportal

import android.content.Context
import com.caddie.study.gateway.NativeStudyGateway
import com.caddie.study.gateway.NativeStudyReadiness
import com.caddie.study.gateway.VerifiedStudyDeviceResetter
import com.caddie.study.portal.PortalService
import com.caddie.study.portal.StudyPortalApi
import com.caddie.study.runtime.coordinator.ArmedTrialCoordinator
import com.caddie.study.runtime.matrix.StudyMatrix
import com.caddie.study.runtime.model.ParticipantConfig
import com.caddie.study.runtime.spec.SpecLoader
import com.caddie.study.runtime.mode.StudyModeArbiter
import com.caddie.study.store.StudyRoomDatabase
import com.caddie.study.store.StudyStore
import com.caddie.study.export.StudyBackupWriter
import com.caddie.study.runtime.audit.ControlledErrorRecorderSlot
import com.caddie.study.runtime.audit.StudyRuntimeEventRecorderSlot
import java.io.Closeable

/** Owns the native study portal dependencies for one Android service process. */
class StudyPortalRuntime private constructor(
    val api: StudyPortalApi,
    private val gateway: NativeStudyGateway,
    private val database: StudyRoomDatabase,
    private val recorderOwner: Any,
    private val recorderSlot: ControlledErrorRecorderSlot,
    private val runtimeEventRecorderSlot: StudyRuntimeEventRecorderSlot,
) : Closeable {

    override fun close() {
        gateway.close()
        recorderSlot.disconnect(recorderOwner)
        runtimeEventRecorderSlot.disconnect(recorderOwner)
        database.close()
    }

    companion object {
        const val SYNTHETIC_READINESS_PARTICIPANT = "TEST-NATIVE-READINESS"

        fun create(
            context: Context,
            database: StudyRoomDatabase,
            coordinator: ArmedTrialCoordinator,
            modeArbiter: StudyModeArbiter = StudyModeArbiter(coordinator),
            readinessProvider: () -> NativeStudyReadiness = {
                NativeStudyReadiness.Unavailable
            },
            controlledErrorRecorder: ControlledErrorRecorderSlot,
            runtimeEventRecorder: StudyRuntimeEventRecorderSlot = StudyRuntimeEventRecorderSlot(),
            cancelPendingConfirmation: (String) -> Unit = {},
            stopActiveRun: () -> Boolean = { false },
            runtimeIdle: () -> Boolean = { true },
            reserveRuntime: () -> AutoCloseable? = { AutoCloseable {} },
            awaitRuntimeIdle: () -> Boolean = runtimeIdle,
        ): StudyPortalRuntime {
            val specs = SpecLoader().loadAllFromAssets(context.assets)
            val configs = withSyntheticReadinessParticipant(StudyMatrix.generateMatrix(specs))
            val deviceDriver = AndroidStudyDeviceResetDriver(context)
            val gateway = NativeStudyGateway(
                coordinator = coordinator,
                configs = configs,
                specs = specs,
                readinessProvider = {
                    readinessProvider().copy(studySpecsLoaded = specs.isNotEmpty())
                },
                cancelPendingConfirmation = cancelPendingConfirmation,
                deviceResetter = VerifiedStudyDeviceResetter(deviceDriver),
                normalPracticePreparer = deviceDriver::openHome,
                modeArbiter = modeArbiter,
                stopActiveRun = stopActiveRun,
                runtimeIdle = runtimeIdle,
                reserveRuntime = reserveRuntime,
                awaitRuntimeIdle = awaitRuntimeIdle,
            )
            val store = StudyStore(database)
            val owner = Any()
            controlledErrorRecorder.connect(owner) { event -> store.recordControlledError(event) }
            runtimeEventRecorder.connect(owner) { event -> store.recordRuntimeEvent(event) }
            val api = PortalService(
                store = store,
                gateway = gateway,
                backups = StudyBackupWriter(
                    root = java.io.File(context.noBackupFilesDir, "study-backups"),
                    legacyRoots = listOf(java.io.File(context.filesDir, "study-backups")),
                ),
            )
            return StudyPortalRuntime(
                api,
                gateway,
                database,
                owner,
                controlledErrorRecorder,
                runtimeEventRecorder,
            )
        }

        /** Adds an isolated rehearsal identity with P01's frozen six-task assignment. */
        internal fun withSyntheticReadinessParticipant(
            configs: Map<String, ParticipantConfig>,
        ): Map<String, ParticipantConfig> {
            val baseline = configs.getValue("P01")
            return configs + (
                SYNTHETIC_READINESS_PARTICIPANT to baseline.copy(
                    participantId = SYNTHETIC_READINESS_PARTICIPANT,
                )
            )
        }

    }
}
