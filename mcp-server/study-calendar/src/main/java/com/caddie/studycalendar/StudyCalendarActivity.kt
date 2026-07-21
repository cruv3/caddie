package com.caddie.studycalendar

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class StudyCalendarActivity : AppCompatActivity() {
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
