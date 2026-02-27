package com.llmcompanion.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.net.toUri

object Permissions {

    /**
     * Prüft, ob die App über anderen Apps gezeichnet werden darf.
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Schickt den Nutzer direkt zu dem Screen, wo er das Overlay für UNSERE App aktivieren kann.
     */
    fun requestOverlayPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri()
        )
        // WICHTIG: FLAG_ACTIVITY_NEW_TASK, falls wir es nicht aus einer Activity heraus aufrufen
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Prüft, ob unser AgentService in den Barrierefreiheitseinstellungen aktiviert ist.
     */
    fun hasAccessibilityPermission(context: Context, serviceClass: Class<*>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        for (service in enabledServices) {
            val componentInfo = service.resolveInfo.serviceInfo
            if (componentInfo.packageName == context.packageName && componentInfo.name == serviceClass.name) {
                return true
            }
        }
        return false
    }

    /**
     * Öffnet die allgemeinen Barrierefreiheitseinstellungen.
     * Leider kann man hier nicht direkt zur eigenen App springen, der Nutzer muss sie in der Liste suchen.
     */
    fun requestAccessibilityPermission(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}