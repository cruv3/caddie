package com.caddie.studycalendar

import android.os.Bundle
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
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
    private var confirmedStartMinutes = DEFAULT_MEETING_START_MINUTES
    private var detailIsMeeting = true
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
        val editorValue = parseTime(findViewById<EditText>(R.id.start_time).text.toString())
        outState.putInt(STATE_CONFIRMED_START, editorValue ?: confirmedStartMinutes)
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            confirmedStartMinutes = meetingStartMinutes()
            return
        }

        screen = savedInstanceState.getString(STATE_SCREEN)
            ?.let { saved -> Screen.entries.firstOrNull { it.name == saved } }
            ?: Screen.SCHEDULE
        detailIsMeeting = savedInstanceState.getBoolean(STATE_DETAIL_IS_MEETING, true)
        val storedStart = selectedEventStartMinutes()
        confirmedStartMinutes = validatedMinutes(
            savedInstanceState.getInt(STATE_CONFIRMED_START, storedStart),
            storedStart,
        )
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
        bindMonthGrid(today)
        findViewById<TextView>(R.id.today_weekday).text = today.format(weekdayFormatter)
        findViewById<TextView>(R.id.today_number).text = today.dayOfMonth.toString()
        findViewById<TextView>(R.id.meeting_time).text = range(meetingStartMinutes())
        findViewById<TextView>(R.id.tomorrow_weekday).text = tomorrow.format(weekdayFormatter)
        findViewById<TextView>(R.id.tomorrow_number).text = tomorrow.dayOfMonth.toString()
        findViewById<TextView>(R.id.exam_time).text = range(examStartMinutes())
    }

    /** Builds a six-week month grid while keeping real event cards in the agenda below. */
    private fun bindMonthGrid(today: LocalDate) {
        val grid = findViewById<GridLayout>(R.id.month_grid)
        val firstOfMonth = today.withDayOfMonth(1)
        val firstVisibleDate = firstOfMonth.minusDays((firstOfMonth.dayOfWeek.value - 1).toLong())

        grid.removeAllViews()
        repeat(MONTH_GRID_DAYS) { index ->
            val date = firstVisibleDate.plusDays(index.toLong())
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(2), dp(2), dp(2), dp(2))
            }
            val dayNumber = TextView(this).apply {
                text = date.dayOfMonth.toString()
                gravity = Gravity.CENTER
                textSize = 13f
                setTypeface(typeface, if (date == today) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(
                    getColor(
                        when {
                            date == today -> R.color.calendar_surface
                            date.month != today.month -> R.color.calendar_border
                            else -> R.color.calendar_text_primary
                        },
                    ),
                )
                if (date == today) setBackgroundResource(R.drawable.bg_date_circle)
            }
            cell.addView(
                dayNumber,
                LinearLayout.LayoutParams(dp(30), dp(30)),
            )

            val eventTitle = when (date) {
                today -> getString(R.string.meeting_title)
                today.plusDays(1) -> getString(R.string.exam_title)
                else -> null
            }
            if (eventTitle != null) {
                cell.addView(
                    TextView(this).apply {
                        text = eventTitle
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(4), 0, dp(4), 0)
                        setTextColor(getColor(R.color.calendar_event_blue))
                        setBackgroundResource(R.drawable.bg_calendar_month_event)
                        textSize = 8f
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(15),
                    ).apply { topMargin = dp(2) },
                )
            }

            grid.addView(
                cell,
                GridLayout.LayoutParams().apply {
                    rowSpec = GridLayout.spec(index / DAYS_PER_WEEK)
                    columnSpec = GridLayout.spec(index % DAYS_PER_WEEK, 1f)
                    width = 0
                    height = dp(54)
                },
            )
        }
    }

    private fun bindInteractions() {
        findViewById<View>(R.id.today_action).setOnClickListener {
            bindSchedule()
            findViewById<ScrollView>(R.id.schedule_scroll).smoothScrollTo(0, 0)
        }
        findViewById<View>(R.id.meeting_event).setOnClickListener { showDetail(meeting = true) }
        findViewById<View>(R.id.exam_event).setOnClickListener { showDetail(meeting = false) }
        findViewById<View>(R.id.edit_event).setOnClickListener { showEditor() }
        findViewById<EditText>(R.id.start_time).doAfterTextChanged { value ->
            if (!renderingEditor && screen == Screen.EDITOR) {
                refreshDerivedEndTime(value?.toString().orEmpty())
            }
        }
        findViewById<View>(R.id.save_event).setOnClickListener { persistSelectedEvent() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
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
        renderScreen()
    }

    private fun showDetail(meeting: Boolean) {
        screen = Screen.DETAIL
        detailIsMeeting = meeting
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
    }

    private fun renderDetail() {
        val meeting = detailIsMeeting
        val date = if (meeting) LocalDate.now() else LocalDate.now().plusDays(1)
        val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
        findViewById<TextView>(R.id.detail_title).setText(
            if (meeting) R.string.meeting_title else R.string.exam_title,
        )
        findViewById<TextView>(R.id.detail_date).text = date.format(dateFormatter)
        findViewById<TextView>(R.id.detail_time).text = range(selectedEventStartMinutes())
        findViewById<View>(R.id.edit_event).visibility = View.VISIBLE
    }

    private fun showEditor() {
        screen = Screen.EDITOR
        confirmedStartMinutes = selectedEventStartMinutes()
        renderScreen()
    }

    private fun renderEditor() {
        findViewById<TextView>(R.id.editor_title).setText(
            if (detailIsMeeting) R.string.edit_meeting else R.string.edit_exam,
        )
        renderingEditor = true
        findViewById<EditText>(R.id.start_time).setText(formatTime(confirmedStartMinutes))
        renderingEditor = false
        refreshDerivedEndTime(formatTime(confirmedStartMinutes))
    }

    private fun refreshDerivedEndTime(startText: String) {
        val start = parseTime(startText)
        findViewById<TextView>(R.id.end_time).text = start?.let { formatTime(it + 60) }.orEmpty()
        findViewById<View>(R.id.save_event).apply {
            isEnabled = start != null
            alpha = if (isEnabled) 1f else 0.45f
        }
    }

    private fun persistSelectedEvent() {
        val start = parseTime(findViewById<EditText>(R.id.start_time).text.toString()) ?: return
        val editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
        if (detailIsMeeting) {
            editor.putInt(KEY_MEETING_START_MINUTES, start).remove(KEY_MEETING_START_HOUR)
        } else {
            editor.putInt(KEY_EXAM_START_MINUTES, start)
        }
        val saved = editor.commit()
        if (saved) {
            confirmedStartMinutes = start
            showDetail(meeting = detailIsMeeting)
        }
    }

    private fun selectedEventStartMinutes(): Int =
        if (detailIsMeeting) meetingStartMinutes() else examStartMinutes()

    private fun meetingStartMinutes(): Int {
        val preferences = getSharedPreferences(PREFS, MODE_PRIVATE)
        val minutes = try {
            if (preferences.contains(KEY_MEETING_START_MINUTES)) {
                preferences.getInt(KEY_MEETING_START_MINUTES, DEFAULT_MEETING_START_MINUTES)
            } else {
                preferences.getInt(KEY_MEETING_START_HOUR, DEFAULT_MEETING_START_HOUR) * 60
            }
        } catch (_: ClassCastException) {
            DEFAULT_MEETING_START_MINUTES
        }
        return validatedMinutes(minutes, DEFAULT_MEETING_START_MINUTES)
    }

    private fun examStartMinutes(): Int {
        val minutes = try {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_EXAM_START_MINUTES, DEFAULT_EXAM_START_MINUTES)
        } catch (_: ClassCastException) {
            DEFAULT_EXAM_START_MINUTES
        }
        return validatedMinutes(minutes, DEFAULT_EXAM_START_MINUTES)
    }

    private fun validatedMinutes(minutes: Int, fallback: Int): Int =
        minutes.takeIf { it in 0 until (23 * 60) } ?: fallback

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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_RESET = "com.caddie.studycalendar.ACTION_RESET"
        const val PREFS = "study_calendar_state"
        const val KEY_MEETING_START_HOUR = "meeting_start_hour"
        const val KEY_MEETING_START_MINUTES = "meeting_start_minutes"
        const val KEY_EXAM_START_MINUTES = "exam_start_minutes"
        const val DEFAULT_MEETING_START_HOUR = 14
        const val DEFAULT_MEETING_START_MINUTES = DEFAULT_MEETING_START_HOUR * 60
        const val DEFAULT_EXAM_START_MINUTES = 10 * 60

        private const val DAYS_PER_WEEK = 7
        private const val MONTH_GRID_DAYS = 42

        private const val STATE_SCREEN = "screen"
        private const val STATE_DETAIL_IS_MEETING = "detail_is_meeting"
        private const val STATE_CONFIRMED_START = "confirmed_start"
    }
}
