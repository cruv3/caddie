package com.caddie.studyportal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.room.Room
import com.caddie.app.studyportal.StudyPortalRuntime
import com.caddie.app.CaddieApplication
import com.caddie.app.androidRuntimeProcess
import com.caddie.study.store.StudyRoomDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service hosting the embedded study-portal HTTP server.
 *
 * Started/stopped from the in-app settings toggle. Exposes the bound URL
 * via [urlState] so the UI can show the Tailscale-reachable address for an
 * investigator or participant browser.
 */
class StudyPortalService : Service() {

    private var server: StudyPortalServer? = null
    private var runtime: StudyPortalRuntime? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readinessJob: Job? = null

    /**
     * Emits the portal URL once the server is up. null while stopped.
     * UI surfaces this so the investigator can open it in a browser.
     */
    val urlState: StateFlow<String?> get() = _urlState
    private val _urlState = MutableStateFlow<String?>(null)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        val port = StudyPortalSettings.getPort(this)
        val database = Room.databaseBuilder(
            applicationContext,
            StudyRoomDatabase::class.java,
            STUDY_DATABASE_NAME,
        ).build()
        val application = applicationContext as CaddieApplication
        val readinessMonitor = application.studyReadinessMonitor
        readinessJob = serviceScope.launch {
            while (isActive) {
                readinessMonitor.refreshRemoteCapabilities()
                delay(READINESS_REFRESH_MS)
            }
        }
        runtime = StudyPortalRuntime.create(
            applicationContext,
            database,
            androidRuntimeProcess().studyCoordinator,
            modeArbiter = application.studyModeArbiter,
            readinessProvider = {
                readinessMonitor.current(studySpecsLoaded = false)
            },
            controlledErrorRecorder = application.controlledErrorRecorder,
            runtimeEventRecorder = application.studyRuntimeEventRecorder,
            cancelPendingConfirmation = application.confirmationGate::cancelPending,
            stopActiveRun = application.runtimeProcess::stopActiveRun,
            runtimeIdle = { application.runtimeProcess.activeNativeRunId() == null },
            reserveRuntime = application.runtimeProcess::tryReserveStudyPreparation,
            awaitRuntimeIdle = { application.runtimeProcess.awaitNativeIdle() },
        )
        server = StudyPortalServer(applicationContext, port, runtime?.api).also { it.start() }
        _urlState.value = StudyPortalServer.deviceUrl(port)
        Log.i(TAG, "Study portal started on port $port")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        runtime?.close()
        runtime = null
        readinessJob?.cancel()
        readinessJob = null
        serviceScope.cancel()
        _urlState.value = null
        Log.i(TAG, "Study portal stopped")
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Study Portal", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Caddie Study Portal")
            .setContentText("Hosting study portal for investigator access")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    companion object {
        private const val TAG = "StudyPortalService"
        private const val CHANNEL_ID = "study_portal_channel"
        private const val NOTIF_ID = 4201
        private const val STUDY_DATABASE_NAME = "caddie-study.db"
        private const val READINESS_REFRESH_MS = 5_000L

        fun start(context: Context) {
            val intent = Intent(context, StudyPortalService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StudyPortalService::class.java))
        }
    }
}
