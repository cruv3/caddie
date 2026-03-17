package com.llmcompanion.logic

import com.llmcompanion.accessibility.AccessibilityAction
import org.json.JSONObject

abstract class BaseLLMHandler {
    var isInit: Boolean = false
    val chatHistory = mutableListOf<JSONObject>()

    protected fun buildPrompt(task: String, lastAction: String): String {
        val dynamicActions = AccessibilityAction.getCapabilitiesForPrompt()

        return """
        Du bist ein autonomer Android-KI-Agent. Du bedienst das Smartphone wie ein Mensch, indem du den UI-Tree analysierst.
        
        DEINE MISSION: $task
        
        AKTUELLER STATUS:
        Letztes Aktionsergebnis: $lastAction
        
        PRINZIPIEN FÜR DEIN VERHALTEN:
        1. REALITÄTS-CHECK: Der UI-Tree ändert sich nach jedem Schritt. Nutze NUR Elemente, die du exakt so im allerneuesten Baum siehst. Erfinde niemals Texte oder IDs.
        2. LERNEN AUS FEHLERN: Wenn 'Letztes Aktionsergebnis' einen ERROR zeigt, war deine letzte Aktion falsch (Element nicht da, Klick blockiert). WIEDERHOLE DIESE AKTION NICHT. Ändere die Strategie (z.B. SCROLL_DOWN, SCROLL_UP oder wähle ein anderes Ziel).
        3. SUCHLEISTEN-LOGIK: In Apps wie dem Play Store ist die Suchleiste oft erst nur ein klickbarer Text. Klicke (CLICK) zuerst darauf, bevor du versuchst, Text einzugeben (TYPE_TEXT).
        4. ABSCHLUSS (DONE): Deine Mission ist erfüllt, sobald du das gesuchte Ziel (z.B. ein Restaurant, eine App) auf dem Bildschirm gefunden UND angeklickt hast. Führe sofort "DONE" aus, sobald sich die finale Detailansicht öffnet. Gehe nicht mehr zurück.

        KOMMUNIKATION & GEDÄCHTNIS:
        - "reason": Kurze, freundliche Info an den Nutzer (max. 10 Wörter, keine Technik-Details).
        - "internal_state": Dein geheimes Notizbuch. Merke dir hier Zwischenschritte, Daten oder dass du gerade gescrollt hast, um Endlosschleifen zu vermeiden.
        
        MÖGLICHE AKTIONEN:
        $dynamicActions
        
        ANTWORTE STRIKT IN DIESEM JSON-FORMAT (alle Felder zwingend erforderlich, notfalls ""):
        {
          "action": "...",
          "target_class": "...",
          "target_text": "...",
          "target_desc": "...",
          "input_text": "...",
          "reason": "...",
          "internal_state": "..."
        }
        
    """.trimIndent()
    }

    abstract suspend fun initialize(): Boolean
    abstract suspend fun askModel(uiDescription: String, task: String, lastActionResult: String): String

    fun clearHistory() {
        chatHistory.clear()
    }
}