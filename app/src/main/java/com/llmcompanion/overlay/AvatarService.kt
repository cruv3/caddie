package com.llmcompanion.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.view.isGone
import com.llmcompanion.R
import com.llmcompanion.logic.AgentManager // 🔥 Neuer Import (die Schaltzentrale)

class AvatarService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var params: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        startForegroundServiceNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = LayoutInflater.from(this).inflate(R.layout.floating_avatar, null)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 400
        }

        windowManager.addView(overlayView, params)

        setupInteractions()
    }

    private fun setupInteractions() {
        val view = overlayView ?: return

        val avatarIcon = view.findViewById<ImageView>(R.id.avatarIcon)
        val inputContainer = view.findViewById<LinearLayout>(R.id.inputContainer)
        val etAgentTask = view.findViewById<EditText>(R.id.etAgentTask)
        val btnStartTask = view.findViewById<Button>(R.id.btnStartTask)
        val btnStopTask = view.findViewById<Button>(R.id.btnStopTask)

        avatarIcon.setOnClickListener {
            if (inputContainer.isGone) {
                inputContainer.visibility = View.VISIBLE
                setFocusable(true)
            } else {
                inputContainer.visibility = View.GONE
                setFocusable(false)
            }
        }

        btnStopTask.setOnClickListener {
            if (AgentManager.isRunning) {
                AgentManager.stopMission()
                inputContainer.visibility = View.GONE
                setFocusable(false)
            }
        }

        btnStartTask.setOnClickListener {
            val task = etAgentTask.text.toString().trim()
            if (task.isNotEmpty()) {
                if (AgentManager.activeService != null) {

                    AgentManager.startMission(task)

                    inputContainer.visibility = View.GONE
                    setFocusable(false)
                    etAgentTask.setText("")
                    Toast.makeText(this@AvatarService, "Agent gestartet!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@AvatarService, "Fehler: Accessibility-Service ist nicht verbunden!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setFocusable(isFocusable: Boolean) {
        if (isFocusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        windowManager.updateViewLayout(overlayView, params)
    }

    @SuppressLint("ForegroundServiceType")
    private fun startForegroundServiceNotification() {
        val channelId = "avatar_service_channel"

        val channel = NotificationChannel(
            channelId,
            "LLM Agent Status",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("LLM Agent ist aktiv")
            .setContentText("Der Avatar schwebt auf deinem Bildschirm.")
            .setSmallIcon(android.R.drawable.ic_menu_help)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (overlayView != null) {
            windowManager.removeView(overlayView)
        }
    }
}