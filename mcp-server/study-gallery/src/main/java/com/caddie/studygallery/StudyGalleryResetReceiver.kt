package com.caddie.studygallery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StudyGalleryResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StudyGalleryActivity.ACTION_RESET) return
        context.getSharedPreferences(StudyGalleryActivity.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }
}
