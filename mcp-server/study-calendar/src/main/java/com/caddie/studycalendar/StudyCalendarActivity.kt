package com.caddie.studycalendar

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class StudyCalendarActivity : AppCompatActivity() {
    private enum class Screen { SCHEDULE, DETAIL, EDITOR }

    private var screen = Screen.SCHEDULE
    private var confirmedHour = DEFAULT_MEETING_START_HOUR
    private var pendingHour = DEFAULT_MEETING_START_HOUR
    private var detailIsMeeting = true
    private var chooserVisible = false

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = getColor(R.color.calendar_background)
        window.navigationBarColor = getColor(R.color.calendar_background)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_study_calendar)
        applySystemBarInsets()
        bindSchedule()
        bindInteractions()
        restoreState(savedInstanceState)
        renderScreen()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SCREEN, screen.name)
        outState.putBoolean(STATE_DETAIL_IS_MEETING, detailIsMeeting)
        outState.putInt(STATE_CONFIRMED_HOUR, confirmedHour)
        outState.putInt(STATE_PENDING_HOUR, pendingHour)
        outState.putBoolean(STATE_CHOOSER_VISIBLE, chooserVisible)
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        val storedHour = meetingHour()
        if (savedInstanceState == null) {
            confirmedHour = storedHour
            pendingHour = storedHour
            return
        }

        screen = savedInstanceState.getString(STATE_SCREEN)
            ?.let { saved -> Screen.entries.firstOrNull { it.name == saved } }
            ?: Screen.SCHEDULE
        detailIsMeeting = savedInstanceState.getBoolean(STATE_DETAIL_IS_MEETING, true)
        if (screen == Screen.EDITOR && !detailIsMeeting) screen = Screen.DETAIL
        confirmedHour = validatedHour(savedInstanceState.getInt(STATE_CONFIRMED_HOUR, storedHour))
        pendingHour = validatedHour(savedInstanceState.getInt(STATE_PENDING_HOUR, confirmedHour))
        chooserVisible = screen == Screen.EDITOR &&
            savedInstanceState.getBoolean(STATE_CHOOSER_VISIBLE, false)
        if (!chooserVisible) pendingHour = confirmedHour
    }

    private fun applySystemBarInsets() {
        val root = findViewById<View>(R.id.calendar_root)
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

    private fun bindSchedule() {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val weekdayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.GERMAN)
        val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)

        findViewById<TextView>(R.id.month_title).text = today.format(monthFormatter)
        findViewById<TextView>(R.id.today_weekday).text = today.format(weekdayFormatter)
        findViewById<TextView>(R.id.today_number).text = today.dayOfMonth.toString()
        findViewById<TextView>(R.id.meeting_time).text = range(meetingHour())
        findViewById<TextView>(R.id.tomorrow_weekday).text = tomorrow.format(weekdayFormatter)
        findViewById<TextView>(R.id.tomorrow_number).text = tomorrow.dayOfMonth.toString()
        findViewById<TextView>(R.id.exam_time).text = range(10)
    }

    private fun bindInteractions() {
        findViewById<View>(R.id.meeting_event).setOnClickListener { showDetail(meeting = true) }
        findViewById<View>(R.id.exam_event).setOnClickListener { showDetail(meeting = false) }
        findViewById<View>(R.id.edit_event).setOnClickListener { showEditor() }
        findViewById<View>(R.id.start_time).setOnClickListener {
            pendingHour = confirmedHour
            chooserVisible = true
            refreshHourSelection()
            refreshChooserVisibility()
        }
        findViewById<View>(R.id.hour_15).setOnClickListener { chooseHour(15) }
        findViewById<View>(R.id.hour_16).setOnClickListener { chooseHour(16) }
        findViewById<View>(R.id.confirm_time).setOnClickListener {
            confirmedHour = pendingHour
            chooserVisible = false
            refreshEditorTime()
            refreshChooserVisibility()
        }
        findViewById<View>(R.id.save_event).setOnClickListener { persistMeetingHour() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val timeChoice = findViewById<View>(R.id.time_choice)
                when {
                    timeChoice.visibility == View.VISIBLE -> {
                        pendingHour = confirmedHour
                        chooserVisible = false
                        refreshHourSelection()
                        refreshChooserVisibility()
                    }
                    screen == Screen.EDITOR -> showDetail(meeting = true)
                    screen == Screen.DETAIL -> showSchedule()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    private fun showSchedule() {
        screen = Screen.SCHEDULE
        chooserVisible = false
        renderScreen()
    }

    private fun showDetail(meeting: Boolean) {
        screen = Screen.DETAIL
        detailIsMeeting = meeting
        chooserVisible = false
        renderScreen()
    }

    private fun renderScreen() {
        findViewById<View>(R.id.schedule_screen).visibility =
            if (screen == Screen.SCHEDULE) View.VISIBLE else View.GONE
        findViewById<View>(R.id.detail_screen).visibility =
            if (screen == Screen.DETAIL) View.VISIBLE else View.GONE
        findViewById<View>(R.id.editor_screen).visibility =
            if (screen == Screen.EDITOR) View.VISIBLE else View.GONE

        when (screen) {
            Screen.SCHEDULE -> bindSchedule()
            Screen.DETAIL -> renderDetail()
            Screen.EDITOR -> renderEditor()
        }
        refreshChooserVisibility()
    }

    private fun renderDetail() {
        val meeting = detailIsMeeting
        val date = if (meeting) LocalDate.now() else LocalDate.now().plusDays(1)
        val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
        findViewById<TextView>(R.id.detail_title).setText(
            if (meeting) R.string.meeting_title else R.string.exam_title,
        )
        findViewById<TextView>(R.id.detail_date).text = date.format(dateFormatter)
        findViewById<TextView>(R.id.detail_time).text = range(if (meeting) meetingHour() else 10)
        findViewById<View>(R.id.edit_event).visibility = if (meeting) View.VISIBLE else View.GONE
    }

    private fun showEditor() {
        if (!detailIsMeeting) return
        screen = Screen.EDITOR
        confirmedHour = meetingHour()
        pendingHour = confirmedHour
        chooserVisible = false
        renderScreen()
    }

    private fun renderEditor() {
        refreshEditorTime()
        refreshHourSelection()
    }

    private fun chooseHour(hour: Int) {
        if (hour !in ALLOWED_HOURS || !chooserVisible) return
        pendingHour = hour
        refreshHourSelection()
    }

    private fun refreshHourSelection() {
        findViewById<View>(R.id.hour_15).isSelected = pendingHour == 15
        findViewById<View>(R.id.hour_16).isSelected = pendingHour == 16
    }

    private fun refreshChooserVisibility() {
        findViewById<View>(R.id.time_choice).visibility =
            if (screen == Screen.EDITOR && chooserVisible) View.VISIBLE else View.GONE
    }

    private fun refreshEditorTime() {
        val label = "Beginnt um: %02d:00".format(Locale.GERMAN, confirmedHour)
        findViewById<TextView>(R.id.start_time).apply {
            text = label
            contentDescription = label
        }
    }

    private fun persistMeetingHour() {
        if (chooserVisible || confirmedHour !in ALLOWED_HOURS) return
        val saved = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putInt(KEY_MEETING_START_HOUR, confirmedHour)
            .commit()
        if (saved) showDetail(meeting = true)
    }

    private fun meetingHour(): Int {
        val hour = try {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_MEETING_START_HOUR, DEFAULT_MEETING_START_HOUR)
        } catch (_: ClassCastException) {
            DEFAULT_MEETING_START_HOUR
        }
        return validatedHour(hour)
    }

    private fun validatedHour(hour: Int): Int =
        hour.takeIf { it in ALLOWED_HOURS } ?: DEFAULT_MEETING_START_HOUR

    private fun range(hour: Int): String =
        "%02d:00–%02d:00 Uhr".format(Locale.GERMAN, hour, hour + 1)

    companion object {
        const val ACTION_RESET = "com.caddie.studycalendar.ACTION_RESET"
        const val PREFS = "study_calendar_state"
        const val KEY_MEETING_START_HOUR = "meeting_start_hour"
        const val DEFAULT_MEETING_START_HOUR = 14
        val ALLOWED_HOURS = setOf(14, 15, 16)

        private const val STATE_SCREEN = "screen"
        private const val STATE_DETAIL_IS_MEETING = "detail_is_meeting"
        private const val STATE_CONFIRMED_HOUR = "confirmed_hour"
        private const val STATE_PENDING_HOUR = "pending_hour"
        private const val STATE_CHOOSER_VISIBLE = "chooser_visible"
    }
}
