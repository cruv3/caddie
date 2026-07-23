package com.caddie.studytelegram

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StudyTelegramResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StudyTelegramActivity.ACTION_RESET) return
        context.getSharedPreferences(StudyTelegramActivity.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
