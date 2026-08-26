package com.caddie.studycalendar

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import java.util.Locale

class StudyModesActivity : AppCompatActivity() {
    private enum class Screen { SUMMARY, EDITOR }

    private var screen = Screen.SUMMARY
    private var pendingStartMinutes: Int? = null
    private var renderingEditor = false

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = getColor(R.color.calendar_background)
        window.navigationBarColor = getColor(R.color.calendar_background)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        setContentView(R.layout.activity_study_modes)
        applySystemBarInsets()
        bindInteractions()
        restoreState(savedInstanceState)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SCREEN, screen.name)
        currentEditorStart()?.let { outState.putInt(STATE_PENDING_START, it) }
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return
        screen = savedInstanceState.getString(STATE_SCREEN)
            ?.let { saved -> Screen.entries.firstOrNull { it.name == saved } }
            ?: Screen.SUMMARY
        pendingStartMinutes = savedInstanceState.getInt(STATE_PENDING_START, -1)
            .takeIf { it in 0 until (23 * 60) }
    }

    private fun applySystemBarInsets() {
        val root = findViewById<View>(R.id.modes_root)
        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialLeft + insets.left,
                initialTop + insets.top,
                initialRight + insets.right,
                initialBottom + insets.bottom,
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun bindInteractions() {
        findViewById<View>(R.id.create_dnd_rule).setOnClickListener {
            screen = Screen.EDITOR
            pendingStartMinutes = storedRangeOrNull()?.let(::parseRangeStart)
            render()
        }
        findViewById<EditText>(R.id.dnd_start_time).doAfterTextChanged { value ->
            if (!renderingEditor && screen == Screen.EDITOR) {
                pendingStartMinutes = parseTime(value?.toString().orEmpty())
                refreshDerivedEndTime()
            }
        }
        findViewById<View>(R.id.save_dnd_rule).setOnClickListener { saveRule() }
    }

    private fun saveRule() {
        if (screen != Screen.EDITOR) return
        val selectedStart = currentEditorStart() ?: return
        val selectedRange = range(selectedStart)
        val saved = getSharedPreferences(StudyCalendarActivity.PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_DND_RANGE, selectedRange)
            .commit()
        if (!saved) return
        screen = Screen.SUMMARY
        render()
    }

    private fun render() {
        findViewById<View>(R.id.modes_summary_screen).visibility =
            if (screen == Screen.SUMMARY) View.VISIBLE else View.GONE
        findViewById<View>(R.id.modes_editor_screen).visibility =
            if (screen == Screen.EDITOR) View.VISIBLE else View.GONE

        if (screen == Screen.SUMMARY) renderSummary() else renderEditor()
    }

    private fun renderSummary() {
        val stored = storedRangeOrNull()
        findViewById<TextView>(R.id.modes_rule_summary).text = stored
            ?.let { "Prüfungsregel\nMorgen · $it" }
            ?: getString(R.string.no_dnd_rule)
        findViewById<TextView>(R.id.create_dnd_rule).setText(
            if (stored == null) R.string.create_dnd_rule else R.string.edit_dnd_rule,
        )
    }

    private fun renderEditor() {
        renderingEditor = true
        findViewById<EditText>(R.id.dnd_start_time).setText(
            pendingStartMinutes?.let(::formatTime).orEmpty(),
        )
        renderingEditor = false
        refreshDerivedEndTime()
    }

    private fun refreshDerivedEndTime() {
        findViewById<TextView>(R.id.dnd_end_time).text =
            pendingStartMinutes?.let { formatTime(it + 60) }.orEmpty()
        findViewById<TextView>(R.id.save_dnd_rule).apply {
            isEnabled = pendingStartMinutes != null
            alpha = if (isEnabled) 1f else 0.45f
        }
    }

    private fun currentEditorStart(): Int? =
        parseTime(findViewById<EditText>(R.id.dnd_start_time).text.toString())

    private fun storedRangeOrNull(): String? {
        val stored = try {
            getSharedPreferences(StudyCalendarActivity.PREFS, MODE_PRIVATE)
                .getString(KEY_DND_RANGE, null)
        } catch (_: ClassCastException) {
            null
        }
        return stored?.takeIf { parseRangeStart(it) != null }
    }

    private fun parseRangeStart(value: String): Int? =
        value.substringBefore('–').let(::parseTime)

    private fun parseTime(value: String): Int? {
        val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(value.trim()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..22 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    private fun formatTime(minutes: Int): String =
        "%02d:%02d".format(Locale.GERMAN, (minutes / 60) % 24, minutes % 60)

    private fun range(startMinutes: Int): String =
        "${formatTime(startMinutes)}–${formatTime(startMinutes + 60)} Uhr"

    companion object {
        const val KEY_DND_RANGE = "dnd_range"
        const val CORRECT_DND_RANGE = "10:00–11:00 Uhr"
        const val CONTROLLED_ERROR_DND_RANGE = "12:00–13:00 Uhr"
        private const val STATE_SCREEN = "screen"
        private const val STATE_PENDING_START = "pending_start"
    }
}
