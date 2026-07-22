package com.caddie.studycalendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class StudyCalendarResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StudyCalendarActivity.ACTION_RESET) return
        val saved = context.getSharedPreferences(StudyCalendarActivity.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(
                StudyCalendarActivity.KEY_MEETING_START_HOUR,
                StudyCalendarActivity.DEFAULT_MEETING_START_HOUR,
            )
            .commit()
        if (!saved) Log.e(TAG, "Failed to persist the study calendar reset")
    }

    private companion object {
        const val TAG = "StudyCalendarReset"
    }
}
