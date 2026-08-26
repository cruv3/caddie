package com.caddie.study.runtime.executor

import com.caddie.study.runtime.model.StudyStep

/**
 * Abstract interface between the trial executor and the Android device.
 *
 * Ported from `caddie.study.executor.StudyBackendProtocol`. A real backend
 * talks to the AccessibilityService; a [FakeStudyBackend] for tests records
 * calls and returns canned UI elements.
 *
 * All methods are synchronous and may block (real I/O on device). The
 * executor calls them from a background coroutine.
 */
interface StudyBackend {

    /** Return the current accessibility element tree as a list of element maps. */
    fun listElements(): List<Map<String, Any?>>

    /** True only when the current tree contains all declared evidence for this step. */
    fun isStepAlreadySatisfied(step: StudyStep): Boolean {
        val evidence = step.alreadySatisfied ?: return false
        return listElements().any { element ->
            val visible = element["visible"] == true
            val enabled = element["enabled"] == true
            val foreground = element["window_foreground"]?.let { it == true } ?: true
            if (!visible || !enabled || !foreground) return@any false

            fun value(vararg keys: String): String = keys
                .asSequence()
                .mapNotNull { key -> element[key]?.toString() }
                .firstOrNull()
                .orEmpty()
                .trim()

            (evidence.packageName == null ||
                value("package_name", "package") == evidence.packageName) &&
                (evidence.resourceId == null ||
                    value("resource_id") == evidence.resourceId) &&
                (evidence.text == null ||
                    value("text").equals(evidence.text, ignoreCase = true))
        }
    }

    /** Launch an application by package (or package/component) name. */
    fun openApp(packageName: String): Map<String, Any?>

    /** Open a URL in the default browser. */
    fun openUrl(url: String): Map<String, Any?>

    /** Tap a UI element by its accessibility index. */
    fun tapElement(index: Int): Map<String, Any?>

    /** Scroll the screen. Direction is "up"/"down"/"left"/"right". */
    fun scroll(direction: String, amount: Double = 0.6): Map<String, Any?>

    /** Press a hardware/soft key: "BACK"/"HOME"/"RECENT". */
    fun pressButton(button: String): Map<String, Any?>

    /** Close only the software keyboard without navigating away from the current screen. */
    fun dismissInputMethod(): Map<String, Any?>

    /** Type text into the focused input field. */
    fun typeText(text: String, submit: Boolean = false): Map<String, Any?>

    /** Replace all text in the focused input field. */
    fun replaceText(text: String, submit: Boolean = false): Map<String, Any?>
}

/** Extracts the package portion from a "package/component" app target. */
fun targetPackage(appTarget: String): String = appTarget.split("/", limit = 2)[0]
