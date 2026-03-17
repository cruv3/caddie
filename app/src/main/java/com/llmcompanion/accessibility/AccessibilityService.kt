package com.llmcompanion.accessibility

import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import com.llmcompanion.logic.AgentManager
import com.llmcompanion.utils.Logger
import android.content.Intent
import android.provider.Settings
import com.llmcompanion.overlay.AvatarService
import com.llmcompanion.utils.Permissions

@SuppressLint("AccessibilityPolicy")
class AccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !AgentManager.isRunning) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            AgentManager.screenChangedSignal.trySend(Unit)
        }

    }

    override fun onInterrupt() {
        AgentManager.stopMission()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AgentManager.attachService(this)

        if (Settings.canDrawOverlays(this)) {
            Logger.i(this, "Overlay-Rechte vorhanden. Starte Avatar direkt aus dem Service!")
            startForegroundService(Intent(this, AvatarService::class.java))
        } else {
            Logger.w(this, "Overlay-Rechte fehlen! Öffne Einstellungen für den Nutzer.")
            Permissions.requestOverlayPermission(this)
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)

        Logger.i(this, "AgentService wurde erfolgreich mit dem System verbunden!")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (AgentManager.activeService == this) {
            AgentManager.detachService()
        }
    }
}