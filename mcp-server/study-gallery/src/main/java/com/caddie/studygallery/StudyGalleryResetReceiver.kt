package com.caddie.studygallery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StudyGalleryResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StudyGalleryActivity.ACTION_RESET) return
        val saved = context.getSharedPreferences(StudyGalleryActivity.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        if (!saved) return

        setResultCode(RESET_SUCCESS_RESULT_CODE)
        setResultData(RESET_SUCCESS_RESULT_DATA)
    }

    companion object {
        const val RESET_SUCCESS_RESULT_CODE = 1205
        const val RESET_SUCCESS_RESULT_DATA = "gallery_reset_ok"
    }
}
