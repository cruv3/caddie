package com.caddie.studycalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StudyCalendarResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StudyCalendarActivity.ACTION_RESET) return
        context.getSharedPreferences(StudyCalendarActivity.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(
                StudyCalendarActivity.KEY_MEETING_START_HOUR,
                StudyCalendarActivity.DEFAULT_MEETING_START_HOUR,
            )
            .commit()
    }
}
