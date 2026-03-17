package com.llmcompanion.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.llmcompanion.utils.Logger

object AccessibilityNodeHelper {
    /**
     * Erstellt eine Text-Repräsentation des UI-Trees für das LLM.
     */
    fun parseUiTree(node: AccessibilityNodeInfo?, depth: Int = 0): String {
        if (node == null) return ""
        if (!node.isVisibleToUser) return ""

        val builder = StringBuilder()

        if (depth == 0) {
            val packageName = node.packageName?.toString() ?: "Unbekannt"
            builder.append("📱 AKTIVE APP-PACKAGE: $packageName\n")
        }

        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val id = node.viewIdResourceName?.substringAfter("id/") ?: ""
        val isClickable = node.isClickable

        var stateInfo = ""
        if (node.isCheckable) {
            val isCurrentlyChecked = node.checked == AccessibilityNodeInfo.CHECKED_STATE_TRUE
            stateInfo = if (isCurrentlyChecked) {
                " | ZUSTAND: [AKTIVIERT / AN]"
            } else {
                " | ZUSTAND: [DEAKTIVIERT / AUS]"
            }
        }

        if (text.isNotEmpty() || desc.isNotEmpty() || isClickable || stateInfo.isNotEmpty()) {
            val indent = "  ".repeat(depth)
            val className = node.className?.toString()?.substringAfterLast(".") ?: "View"
            builder.append("$indent- [$className] ")

            if (text.isNotEmpty()) builder.append("Text: '$text' | ")
            if (desc.isNotEmpty()) builder.append("Desc: '$desc' | ")
            if (id.isNotEmpty()) builder.append("ID: '$id' | ")

            builder.append("Clickable: $isClickable")

            builder.append("$stateInfo\n")
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                builder.append(parseUiTree(it, depth + 1))
            }
        }
        return builder.toString()
    }

    /**
     * Sucht nach dem besten Ziel-Knoten basierend auf LLM-Kriterien.
     * Nutzt jetzt den Fallback für EditText, falls kein Match gefunden wurde.
     */
    fun findBestNode(root: AccessibilityNodeInfo, targetText: String, targetDesc: String, targetClass: String): AccessibilityNodeInfo? {
        val allMatches = mutableListOf<AccessibilityNodeInfo>()
        findAllMatches(root, targetText, targetDesc, targetClass, allMatches)

        if (allMatches.isEmpty()) {
            if (targetClass.contains("EditText", ignoreCase = true)) {
                Logger.i(this, "Kein Treffer für '$targetDesc'. Suche das erste sichtbare Eingabefeld...")
                return findFirstVisibleEditText(root)
            }
            return null
        }

        val filtered = allMatches.filter { match ->
            val id = match.viewIdResourceName ?: ""
            val pId = match.parent?.viewIdResourceName ?: ""
            !(id.contains("action_bar") || id.contains("toolbar") ||
                    pId.contains("action_bar") || pId.contains("toolbar"))
        }

        val bestMatch = filtered.firstOrNull() ?: allMatches[0]

        // Bounds Check
        val bounds = Rect()
        bestMatch.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            Logger.w(this, "Bounds ungültig für '${bestMatch.className}'. Nutze Parent.")
            return bestMatch.parent ?: bestMatch
        }

        return bestMatch
    }

    private fun findAllMatches(node: AccessibilityNodeInfo, text: String, desc: String, targetClass: String, results: MutableList<AccessibilityNodeInfo>) {
        val nodeText = node.text?.toString() ?: ""
        val nodeDesc = node.contentDescription?.toString() ?: ""
        val nodeClassName = node.className?.toString() ?: ""

        val matchesText = text.isNotEmpty() && nodeText.contains(text, ignoreCase = true)
        val matchesDesc = desc.isNotEmpty() && nodeDesc.contains(desc, ignoreCase = true)

        val matchesClass = targetClass.isNotEmpty() && nodeClassName.contains(targetClass, ignoreCase = true)

        if (matchesText || matchesDesc || (text.isEmpty() && desc.isEmpty() && matchesClass)) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { findAllMatches(it, text, desc, targetClass, results) }
        }
    }

    /**
     * Sucht rekursiv nach oben, bis ein klickbares Element gefunden wird.
     */
    fun findClickableParent(node: AccessibilityNodeInfo?, maxDepth: Int = 5): AccessibilityNodeInfo? {
        var current = node
        var depth = 0
        while (current != null && depth < maxDepth) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }

    /**
     * Findet das erste Eingabefeld, das der Nutzer gerade sehen kann.
     */
    private fun findFirstVisibleEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.contains("EditText", ignoreCase = true) == true && node.isVisibleToUser) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = node.getChild(i)?.let { findFirstVisibleEditText(it) }
            if (found != null) return found
        }
        return null
    }

    /**
     * Findet das erste Element auf dem Bildschirm, das scrollbar ist.
     */
    fun findFirstScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable && node.isVisibleToUser) return node

        for (i in 0 until node.childCount) {
            val found = findFirstScrollableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    fun hasLoadingIndicator(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val className = node.className?.toString() ?: ""

        if (className.contains("ProgressBar") || className.contains("ProgressIndicator")) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 100) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            if (hasLoadingIndicator(node.getChild(i))) return true
        }
        return false
    }
}