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
    private var selectedHour = DEFAULT_MEETING_START_HOUR
    private var detailIsMeeting = true

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
        selectedHour = meetingHour()
        showSchedule()
    }

    private fun applySystemBarInsets() {
        val schedule = findViewById<android.view.View>(R.id.schedule_screen)
        val initialLeft = schedule.paddingLeft
        val initialTop = schedule.paddingTop
        val initialRight = schedule.paddingRight
        val initialBottom = schedule.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(schedule) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialLeft + insets.left,
                initialTop + insets.top,
                initialRight + insets.right,
                initialBottom + insets.bottom,
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(schedule)
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
            findViewById<View>(R.id.time_choice).visibility = View.VISIBLE
        }
        findViewById<View>(R.id.hour_15).setOnClickListener { chooseHour(15) }
        findViewById<View>(R.id.hour_16).setOnClickListener { chooseHour(16) }
        findViewById<View>(R.id.confirm_time).setOnClickListener {
            findViewById<View>(R.id.time_choice).visibility = View.GONE
            refreshEditorTime()
        }
        findViewById<View>(R.id.save_event).setOnClickListener { persistMeetingHour() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val timeChoice = findViewById<View>(R.id.time_choice)
                when {
                    timeChoice.visibility == View.VISIBLE -> timeChoice.visibility = View.GONE
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
        findViewById<View>(R.id.schedule_screen).visibility = View.VISIBLE
        findViewById<View>(R.id.detail_screen).visibility = View.GONE
        findViewById<View>(R.id.editor_screen).visibility = View.GONE
        findViewById<View>(R.id.time_choice).visibility = View.GONE
        bindSchedule()
    }

    private fun showDetail(meeting: Boolean) {
        screen = Screen.DETAIL
        detailIsMeeting = meeting
        findViewById<View>(R.id.schedule_screen).visibility = View.GONE
        findViewById<View>(R.id.detail_screen).visibility = View.VISIBLE
        findViewById<View>(R.id.editor_screen).visibility = View.GONE
        findViewById<View>(R.id.time_choice).visibility = View.GONE

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
        check(detailIsMeeting)
        screen = Screen.EDITOR
        selectedHour = meetingHour()
        findViewById<View>(R.id.schedule_screen).visibility = View.GONE
        findViewById<View>(R.id.detail_screen).visibility = View.GONE
        findViewById<View>(R.id.editor_screen).visibility = View.VISIBLE
        findViewById<View>(R.id.time_choice).visibility = View.GONE
        refreshEditorTime()
        refreshHourSelection()
    }

    private fun chooseHour(hour: Int) {
        selectedHour = hour
        refreshHourSelection()
    }

    private fun refreshHourSelection() {
        findViewById<View>(R.id.hour_15).isSelected = selectedHour == 15
        findViewById<View>(R.id.hour_16).isSelected = selectedHour == 16
    }

    private fun refreshEditorTime() {
        val label = "Beginnt um: %02d:00".format(Locale.GERMAN, selectedHour)
        findViewById<TextView>(R.id.start_time).apply {
            text = label
            contentDescription = label
        }
    }

    private fun persistMeetingHour() {
        require(selectedHour in setOf(15, 16))
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putInt(KEY_MEETING_START_HOUR, selectedHour)
            .commit()
        showDetail(meeting = true)
    }

    private fun meetingHour(): Int {
        val hour = try {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_MEETING_START_HOUR, DEFAULT_MEETING_START_HOUR)
        } catch (_: ClassCastException) {
            DEFAULT_MEETING_START_HOUR
        }
        return hour.takeIf { it in 0..22 } ?: DEFAULT_MEETING_START_HOUR
    }

    private fun range(hour: Int): String =
        "%02d:00–%02d:00 Uhr".format(Locale.GERMAN, hour, hour + 1)

    companion object {
        const val ACTION_RESET = "com.caddie.studycalendar.ACTION_RESET"
        const val PREFS = "study_calendar_state"
        const val KEY_MEETING_START_HOUR = "meeting_start_hour"
        const val DEFAULT_MEETING_START_HOUR = 14
    }
}
