package com.llmcompanion.agent

import com.llmcompanion.action.ActionCatalog
import com.llmcompanion.model.IndexedUiNode
import com.llmcompanion.model.UiBounds
import com.llmcompanion.model.UiNode
import com.llmcompanion.model.UiSnapshot

/**
 * Wandelt einen [UiSnapshot] und eine Aufgabenbeschreibung in die Nachrichtenliste
 * um, die pro Schritt an das LLM gesendet wird.
 *
 * Prompts sind auf DEUTSCH — das Gerät ist auf Deutsch eingestellt.
 */
object PromptBuilder {

    // ─── Systemprompt ──────────────────────────────────────────────────────────

    fun systemPrompt(
        task: String,
        perceptionMode: PerceptionMode = PerceptionMode.ACCESSIBILITY_ONLY,
        useSom: Boolean = false,
    ): String = buildString {
        when (perceptionMode) {
            PerceptionMode.ACCESSIBILITY_ONLY -> {
                appendLine("Du bist ein Android-UI-Agent. Du steuerst ein Smartphone, indem du den")
                appendLine("Accessibility-Baum liest und eine Aktion nach der anderen ausführst.")
            }
            PerceptionMode.VISION_ONLY -> {
                appendLine("Du bist ein Android-UI-Agent. Du siehst einen Screenshot des Smartphones")
                appendLine("und wählst die nächste Aktion anhand des Bildschirminhalts.")
                appendLine("Verwende tap_at oder swipe mit Pixelkoordinaten wenn du auf Bildschirmelemente tippen willst.")
            }
            PerceptionMode.HYBRID -> {
                appendLine("Du bist ein Android-UI-Agent. Dir stehen zwei Informationsquellen zur Verfügung:")
                appendLine("1. Ein Screenshot des Bildschirms (im Bild angehängt)")
                appendLine("2. Eine Liste interaktiver Elemente aus dem Accessibility-Baum (im Text unten)")
                appendLine("Nutze beide Quellen. Bei Widerspruch vertraue dem Screenshot.")
            }
        }
        appendLine()
        appendLine("## Deine Aufgabe")
        appendLine(task)
        appendLine()
        append(ActionCatalog.promptBlock())
        appendLine()
        appendLine()
        appendLine("## Regeln")
        appendLine("1. Antworte NUR mit einem einzigen gültigen JSON-Objekt — keine Erklärung, kein Markdown, kein sonstiger Text.")
        appendLine("2. Wähle die Aktion, die die größten Fortschritte Richtung Ziel macht.")
        appendLine("3. Verwende Element-Text oder Content-Description GENAU so wie im Bildschirm angezeigt — nicht raten.")
        appendLine("4. Antworte erst mit {\"type\":\"done\"} wenn du auf dem Bildschirm SICHER verifiziert hast, dass das Ziel erreicht ist.")
        appendLine("   Schalter zeigen ◉ ZUSTAND=EIN oder ○ ZUSTAND=AUS — prüfe den Zustand vor 'done'.")
        appendLine("5. Wenn die Aufgabe unmöglich ist, antworte mit {\"type\":\"fail\",\"reason\":\"...\"}.")
        appendLine("6. Wiederhole niemals dieselbe fehlgeschlagene Aktion. Versuche einen anderen Weg.")
        appendLine("7. Toggle/Schalter: Klicke nur wenn der aktuelle Zustand falsch ist.")
        appendLine("   Ist ZUSTAND=EIN und du willst AUS → klicke den Schalter.")
        appendLine("   Ist ZUSTAND=AUS und du willst EIN → klicke den Schalter.")
        appendLine("   Ist der Zustand bereits korrekt → {\"type\":\"done\"}.")
        appendLine("8. Nach set_text in einem Suchfeld: Benutze IMMER press_enter um die Suche abzuschicken.")
        appendLine("   Klicke NIEMALS auf Autocomplete-Vorschlaege — die funktionieren nicht zuverlaessig.")
        appendLine("   Reihenfolge: set_text → press_enter → warten → naechste Aktion.")
        appendLine("9. Wenn der Bildschirm sich nach einer Aktion NICHT veraendert hat (gleiche App, gleiche Node-Anzahl),")
        appendLine("   bedeutet das, dass die Aktion nicht gewirkt hat. Versuche eine andere Strategie.")

        if (useSom) {
            appendLine()
            appendLine("Die interaktiven Elemente sind im Screenshot nummeriert.")
            appendLine("Verwende für Klicks: {\"type\": \"click\", \"element\": 3}")
            appendLine("Verwende für Texteingabe: {\"type\": \"set_text\", \"element\": 3, \"text\": \"...\"}")
            appendLine("Rückfall auf \"target\" ist weiterhin erlaubt.")
        }
    }.trimEnd()

    // ─── Userprompt ────────────────────────────────────────────────────────────

    fun userPrompt(
        snapshot: UiSnapshot,
        step: Int,
        lastResult: String? = null,
        perceptionMode: PerceptionMode = PerceptionMode.ACCESSIBILITY_ONLY,
        indexedNodes: List<IndexedUiNode> = emptyList(),
    ): String = buildString {
        appendLine("=== Schritt $step ===")
        appendLine()
        appendLine("App       : ${snapshot.packageName}")
        appendLine("Bildschirm: ${snapshot.screenWidth}×${snapshot.screenHeight}")
        appendLine()

        if (lastResult != null) {
            appendLine("Ergebnis der letzten Aktion: $lastResult")
            appendLine()
        }

        when (perceptionMode) {
            PerceptionMode.VISION_ONLY -> {
                if (indexedNodes.isNotEmpty()) {
                    appendLine("## Interaktive Elemente (nummeriert im Screenshot)")
                    indexedNodes.forEach { (idx, node) ->
                        val label = node.text?.takeIf { it.isNotBlank() }
                            ?: node.contentDescription?.takeIf { it.isNotBlank() }
                            ?: node.className?.substringAfterLast('.') ?: "Element"
                        appendLine("  [$idx] $label")
                    }
                    appendLine()
                }
                appendLine("Ein Screenshot dieses Schritts ist beigefügt. Analysiere ihn und entscheide die nächste Aktion.")
            }
            PerceptionMode.HYBRID -> {
                appendLine("## Interaktive Elemente auf dem Bildschirm")
                if (indexedNodes.isNotEmpty()) {
                    indexedNodes.forEach { (idx, node) -> appendLine("[$idx] ${buildNodeLine(node, snapshot.nodes)}") }
                } else {
                    val interactive = snapshot.nodes.filter {
                        it.isClickable || it.isLongClickable || it.isEditable
                    }
                    if (interactive.isEmpty()) {
                        appendLine("(keine interaktiven Elemente — versuche scroll, back oder home)")
                    } else {
                        interactive.forEach { node -> appendLine(buildNodeLine(node, snapshot.nodes)) }
                    }
                }
                appendLine()
                appendLine("Zusätzlich ist ein Screenshot angehängt. Bei Widerspruch zwischen Liste und Screenshot vertraue dem Screenshot.")
            }
            PerceptionMode.ACCESSIBILITY_ONLY -> {
                appendLine("## Interaktive Elemente auf dem Bildschirm")
                val interactive = snapshot.nodes.filter {
                    it.isClickable || it.isLongClickable || it.isEditable
                }
                if (interactive.isEmpty()) {
                    appendLine("(keine interaktiven Elemente — versuche scroll, back oder home)")
                } else {
                    interactive.forEach { node -> appendLine(buildNodeLine(node, snapshot.nodes)) }
                }
            }
        }

        appendLine()
        appendLine("Was ist deine nächste Aktion? Antworte nur mit JSON.")
    }.trimEnd()

    // ─── Node-Zeile aufbauen ───────────────────────────────────────────────────

    /**
     * Baut eine lesbare Beschreibungszeile für einen interaktiven Node.
     *
     * Zwei wichtige Verbesserungen:
     * 1. Label-Enrichment: Wenn ein Node keinen eigenen Text/Desc hat (bounds-basierte ID),
     *    werden Kindknoten-Texte als Label verwendet. Beispiel:
     *    FrameLayout "n_0_879_1080_1160" → FrameLayout "Dunkles Design verwenden"
     *
     * 2. Toggle-State-Propagation: Wenn ein Container-Node keinen eigenen checked-State hat,
     *    aber ein Kind-Switch/Checkbox hat, wird der Kindstate übernommen und angezeigt.
     *    So sieht der Agent: FrameLayout "Dunkles Design verwenden"  ◉ ZUSTAND=EIN
     */
    private fun buildNodeLine(node: UiNode, allNodes: List<UiNode>): String {
        val cls = node.className?.substringAfterLast('.') ?: "View"

        // 1. Label: eigener Text > eigene Desc > Kindknoten-Text > ID
        val label = when {
            !node.text.isNullOrBlank()               -> node.text!!
            !node.contentDescription.isNullOrBlank() -> node.contentDescription!!
            else -> {
                // Suche Kindknoten mit Text, dessen Bounds innerhalb dieses Nodes liegen
                val childText = allNodes
                    .filter { child ->
                        child !== node &&
                        !child.text.isNullOrBlank() &&
                        node.bounds.contains(child.bounds)
                    }
                    .firstOrNull()?.text
                childText ?: node.id
            }
        }

        // 2. Toggle-Zustand: eigen > Kind-Switch/Checkbox
        val toggleState = when {
            node.isCheckable && node.isChecked  -> "  ◉ ZUSTAND=EIN"
            node.isCheckable && !node.isChecked -> "  ○ ZUSTAND=AUS"
            else -> {
                val childToggle = allNodes.find { child ->
                    child !== node &&
                    child.isCheckable &&
                    node.bounds.contains(child.bounds)
                }
                when {
                    childToggle?.isChecked == true  -> "  ◉ ZUSTAND=EIN"
                    childToggle?.isChecked == false -> "  ○ ZUSTAND=AUS"
                    else -> ""
                }
            }
        }

        val flags = buildList {
            if (node.isClickable)     add("klick")
            if (node.isLongClickable) add("langKlick")
            if (node.isEditable)      add("editierbar")
        }.joinToString(",")

        return "  [$cls] \"$label\"  [$flags]$toggleState  (${node.bounds.left},${node.bounds.top}–${node.bounds.right},${node.bounds.bottom})"
    }

    // ─── Nachrichten-Builder ───────────────────────────────────────────────────

    fun buildMessages(
        task: String,
        snapshot: UiSnapshot,
        step: Int,
        history: List<Pair<String, String>> = emptyList(),
        lastResult: String? = null,
        perceptionMode: PerceptionMode = PerceptionMode.ACCESSIBILITY_ONLY,
        indexedNodes: List<IndexedUiNode> = emptyList(),
    ): List<Pair<String, String>> = buildList {
        add("system" to systemPrompt(task, perceptionMode, useSom = indexedNodes.isNotEmpty()))
        history.takeLast(3).forEach { (userMsg, assistantMsg) ->
            add("user"      to userMsg)
            add("assistant" to assistantMsg)
        }
        add("user" to userPrompt(snapshot, step, lastResult, perceptionMode, indexedNodes))
    }
}

// ─── UiBounds Erweiterung ─────────────────────────────────────────────────────

/** Prüft ob [other] vollständig innerhalb dieser Bounds liegt. */
private fun UiBounds.contains(other: UiBounds): Boolean =
    other.left >= left && other.top >= top &&
    other.right <= right && other.bottom <= bottom
