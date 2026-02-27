package com.llmcompanion.accessibility

import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import com.llmcompanion.utils.Logger

@SuppressLint("AccessibilityPolicy")
class AccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: "Unbekannt"
        val eventType = event.eventType

        Logger.d(this, "Event: Typ=$eventType | App=$packageName")
    }

    override fun onInterrupt() {
        // Wird aufgerufen, wenn das System den Dienst unterbricht
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Logger.i(this, "AgentService wurde erfolgreich mit dem System verbunden!")
    }
}