package com.caddie.app.studyportal

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.caddie.study.gateway.StudyAppResetTarget
import com.caddie.study.gateway.StudyDeviceResetDriver
import com.caddie.study.gateway.StudyResetResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Executes verified study-app resets without Python or ADB. */
class AndroidStudyDeviceResetDriver(
    context: Context,
    private val acknowledgementTimeoutMillis: Long = 3_000,
    private val recreationSettleMillis: Long = 150,
) : StudyDeviceResetDriver {
    private val appContext = context.applicationContext

    override fun sendReset(target: StudyAppResetTarget): StudyResetResult {
        val action = target.resetAction ?: return StudyResetResult(completed = true)
        val completed = CountDownLatch(1)
        var received = StudyResetResult(completed = false)
        val callbackThread = HandlerThread("study-reset-${target.packageName}").apply { start() }
        return try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    received = StudyResetResult(
                        completed = true,
                        resultCode = resultCode,
                        resultData = resultData,
                    )
                    completed.countDown()
                }
            }
            appContext.sendOrderedBroadcast(
                Intent(action)
                    .setPackage(target.packageName)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
                null,
                receiver,
                Handler(callbackThread.looper),
                Activity.RESULT_CANCELED,
                null,
                null,
            )
            if (completed.await(acknowledgementTimeoutMillis, TimeUnit.MILLISECONDS)) {
                received
            } else {
                Log.e(TAG, "Reset acknowledgement timed out for ${target.packageName}")
                StudyResetResult(completed = false)
            }
        } catch (error: RuntimeException) {
            Log.e(TAG, "Reset broadcast failed for ${target.packageName}", error)
            StudyResetResult(completed = false)
        } finally {
            callbackThread.quitSafely()
        }
    }

    override fun recreate(packageName: String): Boolean {
        val launch = appContext.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false.also { Log.e(TAG, "Study app is not launchable: $packageName") }
        return try {
            launch.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION,
            )
            appContext.startActivity(launch)
            Thread.sleep(recreationSettleMillis)
            true
        } catch (error: RuntimeException) {
            Log.e(TAG, "Failed to recreate study app $packageName", error)
            false
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    override fun openHome(): Boolean = try {
        appContext.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (error: RuntimeException) {
        Log.e(TAG, "Failed to return to Android Home", error)
        false
    }

    private companion object {
        const val TAG = "StudyDeviceReset"
    }
}
