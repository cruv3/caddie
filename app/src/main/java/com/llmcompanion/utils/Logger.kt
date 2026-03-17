package com.llmcompanion.utils

import android.util.Log

object Logger {

    private const val PREFIX = "LLM_"

    fun d(obj: Any, message: String) {
        Log.d(PREFIX + obj::class.java.simpleName, message)
    }

    fun e(obj: Any, message: String, throwable: Throwable? = null) {
        Log.e(PREFIX + obj::class.java.simpleName, message, throwable)
    }

    fun i(obj: Any, message: String) {
        Log.i(PREFIX + obj::class.java.simpleName, message)
    }

    fun w(obj: Any, message: String) {
        Log.w(PREFIX + obj::class.java.simpleName, message)
    }
}