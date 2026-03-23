package com.llmcompanion.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.llmcompanion.accessibility.AccessibilityNodeHelper.findBestNode
import com.llmcompanion.accessibility.AccessibilityNodeHelper.findFirstScrollableNode
import com.llmcompanion.logic.AgentManager
import com.llmcompanion.utils.Logger
import org.json.JSONObject

object AccessibilityAction {

    enum class AgentCapability(val description: String) {
        CLICK("Klickt auf ein Element basierend auf Text oder ID."),
        TYPE_TEXT("Tippt Text in ein Eingabefeld ein (benötigt 'input_text')."),
        SCROLL_DOWN("Scrollt auf dem aktuellen Bildschirm nach unten."),
        SCROLL_UP("Scrollt auf dem aktuellen Bildschirm nach oben."),
        BACK("Geht einen Schritt zurück."),
        HOME("Kehrt zum Homescreen zurück."),
        OPEN_APP("Öffnet eine App (benötigt den Paketnamen in 'input_text', z.B. 'com.android.settings')."),
        DONE("Beendet die Aufgabe, wenn das Ziel erreicht wurde."),
        WAIT("Wartet kurz, falls der Bildschirm noch lädt, ein Ladebalken sichtbar ist oder der Inhalt verarbeitet wird."),
    }

    fun getCapabilitiesForPrompt(): String {
        return AgentCapability.entries.joinToString("\n") { "- \"${it.name}\": ${it.description}" }
    }

    fun executeCommand(service: AccessibilityService, rootNode: AccessibilityNodeInfo, rawResponse: String): String {
        if (rawResponse.isBlank()) {
            return "ERROR: Die KI hat eine leere Antwort gesendet. Bitte antworte im JSON-Format."
        }

        val jsonString = rawResponse.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()


        return try {
            val json = JSONObject(jsonString)
            val actionStr = json.optString("action", "").uppercase()
            val targetClass = json.optString("target_class", "")
            val targetText = json.optString("target_text", "")
            val targetDesc = json.optString("target_desc", "")
            val inputText = json.optString("input_text", "")

            val action = try { AgentCapability.valueOf(actionStr) } catch (e: Exception) {
                return "ERROR: Unbekannte Aktion '$actionStr'. Bitte nutze nur die erlaubten Capabilities."
            }

            Logger.i(this, "Führe aus: ${action.name} | Suche Ziel: [Class: '$targetClass', Text: '$targetText', Desc: '$targetDesc'] | Grund: ${json.optString("reason")}")

            when (action) {
                AgentCapability.BACK -> {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    "SUCCESS: 'Zurück' ausgeführt."
                }
                AgentCapability.HOME -> {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    "SUCCESS: 'Home' ausgeführt."
                }
                AgentCapability.OPEN_APP -> {
                    openApp(service, inputText)
                }
                AgentCapability.DONE -> {
                    AgentManager.stopMission()
                    "SUCCESS: Mission beendet."
                }
                AgentCapability.WAIT -> {
                    AgentManager.screenChangedSignal.trySend(Unit)
                    "SUCCESS: Agent wartet auf UI."
                }
                AgentCapability.SCROLL_DOWN, AgentCapability.SCROLL_UP -> {
                    var node = findBestNode(rootNode, targetText, targetDesc, targetClass)

                    if (node != null) {
                        Logger.d(this, "Scroll-Ziel gefunden: Class=${node.className}, Text='${node.text}'")
                    } else {
                        node = findFirstScrollableNode(rootNode)
                        if (node != null) {
                            Logger.d(this, "Kein LLM-Ziel gefunden. Nutze Fallback-Scroll-Container: Class=${node.className}")
                        }
                    }

                    val isForward = action == AgentCapability.SCROLL_DOWN

                    Logger.d(this, "Nutze direkten physischen Swipe für SCROLL!")
                    dispatchSwipe(service, isForward)
                    Thread.sleep(500)
                    "SUCCESS: Physischer Swipe ausgeführt."
                }
                AgentCapability.CLICK, AgentCapability.TYPE_TEXT -> {
                    val node = findBestNode(rootNode, targetText, targetDesc, targetClass)
                    if (node != null) {
                        val bounds = android.graphics.Rect()
                        node.getBoundsInScreen(bounds)
                        Logger.d(this, "Aktion-Ziel gefunden: Class=${node.className}, Text='${node.text}', Bounds=${bounds.toShortString()}")

                        when (action) {
                            AgentCapability.CLICK -> {
                                if (performClick(service, node)) "SUCCESS: Element physisch geklickt."
                                else "ERROR: Koordinaten ungültig."
                            }
                            AgentCapability.TYPE_TEXT -> {
                                if (performSetText(node, inputText)) "SUCCESS: Text eingetippt."
                                else "ERROR: Text konnte nicht in das Feld geschrieben werden."
                            }
                            else -> "ERROR: Unbekannter Fehler."
                        }
                    } else {
                        "ERROR: Element für ${action.name} nicht gefunden (Class: $targetClass, Text: $targetText, Desc: $targetDesc)."
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(this, "Fehler in executeCommand", e)
            "ERROR: Interner Fehler beim Ausführen: ${e.message}"
        }
    }

    private fun openApp(service: AccessibilityService, packageName: String): String {
        if (packageName.isEmpty()) return "ERROR: Kein Package Name für OPEN_APP geliefert."
        return try {
            val launchIntent = service.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                service.startActivity(launchIntent)
                "SUCCESS: App $packageName wurde gestartet."
            } else {
                "ERROR: App mit Package '$packageName' ist auf diesem Gerät nicht installiert."
            }
        } catch (e: Exception) {
            "ERROR: App-Start fehlgeschlagen: ${e.message}"
        }
    }

    private fun performClick(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        Logger.i(this, "Nutze Force-Touch (dispatchTouch) für verlässlichen Klick.")
        dispatchTouch(service, node)
        return true
    }

    private fun performSetText(node: AccessibilityNodeInfo, textToType: String): Boolean {
        if (textToType.isEmpty()) return false
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
        }

        val isTextSet = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        if (isTextSet) {
            Thread.sleep(200)
            val isEnterPressed = node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            if (isEnterPressed) {
                Logger.i(this, "Text '$textToType' getippt und IME_ENTER (Suchen) erfolgreich ausgelöst!")
            } else {
                Logger.w(this, "Text getippt, aber das Feld unterstützt kein IME_ENTER.")
            }
        }

        return isTextSet
    }

    private fun dispatchTouch(service: AccessibilityService, node: AccessibilityNodeInfo) {
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)

        val x = bounds.centerX().toFloat()
        val y = bounds.centerY().toFloat()

        if (x <= 0 && y <= 0) {
            Logger.e(this, "Kann nicht klicken: Ungültige Koordinaten ($x, $y)")
            return
        }

        val path = android.graphics.Path()
        path.moveTo(x, y)

        val gestureBuilder = android.accessibilityservice.GestureDescription.Builder()
        // 50ms ist ein normaler, schneller Fingertipp
        gestureBuilder.addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50))

        service.dispatchGesture(gestureBuilder.build(), object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                Logger.i(this@AccessibilityAction, "Physischer Touch bei [$x | $y] auf Element ${node.className} ausgeführt!")
            }
            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                Logger.e(this@AccessibilityAction, "Touch-Geste abgebrochen.")
            }
        }, null)
    }

    private fun dispatchSwipe(service: AccessibilityService, forward: Boolean): Boolean {
        val displayMetrics = service.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        val startX = screenWidth / 2

        val startY = if (forward) screenHeight * 0.7f else screenHeight * 0.3f
        val endY = if (forward) screenHeight * 0.3f else screenHeight * 0.7f

        val path = android.graphics.Path()
        path.moveTo(startX, startY)
        path.lineTo(startX, endY)

        val gestureBuilder = android.accessibilityservice.GestureDescription.Builder()
        gestureBuilder.addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 300))

        service.dispatchGesture(gestureBuilder.build(), object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                Logger.i(this@AccessibilityAction, "Physischer Swipe ausgeführt (Start: $startY, End: $endY)!")
            }
        }, null)

        return true
    }
}