package com.caddie.studynotes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StudyNotesResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StudyNotesActivity.ACTION_RESET) return
        val saved = context.getSharedPreferences(StudyNotesActivity.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        if (!saved) return

        setResultCode(RESET_SUCCESS_RESULT_CODE)
        setResultData(RESET_SUCCESS_RESULT_DATA)
    }

    companion object {
        const val RESET_SUCCESS_RESULT_CODE = 1206
        const val RESET_SUCCESS_RESULT_DATA = "notes_reset_ok"
    }
}
