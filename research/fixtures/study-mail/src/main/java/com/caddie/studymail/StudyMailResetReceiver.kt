package com.caddie.studymail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StudyMailResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StudyMailActivity.ACTION_RESET) return
        context.getSharedPreferences(StudyMailActivity.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
