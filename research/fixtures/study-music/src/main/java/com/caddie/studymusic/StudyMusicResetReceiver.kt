package com.caddie.studymusic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StudyMusicResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StudyMusicActivity.ACTION_RESET) return

        val cleared = context.getSharedPreferences(StudyMusicActivity.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        if (!cleared) return

        setResultCode(RESET_SUCCESS_RESULT_CODE)
        setResultData(RESET_SUCCESS_RESULT_DATA)
    }

    companion object {
        const val RESET_SUCCESS_RESULT_CODE = 1207
        const val RESET_SUCCESS_RESULT_DATA = "music_reset_ok"
    }
}
