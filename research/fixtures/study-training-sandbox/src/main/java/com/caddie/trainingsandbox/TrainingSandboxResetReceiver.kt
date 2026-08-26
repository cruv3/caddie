package com.caddie.trainingsandbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TrainingSandboxResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TrainingSandboxActivity.ACTION_RESET) return
        val cleared = context
            .getSharedPreferences(TrainingSandboxActivity.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        if (!cleared) return

        setResultCode(RESET_SUCCESS_RESULT_CODE)
        setResultData(RESET_SUCCESS_RESULT_DATA)
    }

    companion object {
        const val RESET_SUCCESS_RESULT_CODE = 1208
        const val RESET_SUCCESS_RESULT_DATA = "training_sandbox_reset_ok"
    }
}
