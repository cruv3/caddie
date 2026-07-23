package com.caddie.studynotes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StudyNotesResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StudyNotesActivity.ACTION_RESET) return
        context.getSharedPreferences(StudyNotesActivity.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }
}
