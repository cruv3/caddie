package com.llm_smartphone_v2.setup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.llm_smartphone_v2.accessibility.CompanionAccessibilityService

data class SetupUiState(
    val accessibility: Boolean,
    val overlay: Boolean,
    val mic: Boolean,
) {
    val allGranted: Boolean get() = accessibility && overlay && mic
}

fun evaluateSetup(context: Context): SetupUiState = SetupUiState(
    accessibility = CompanionAccessibilityService.isConnected(),
    overlay = Settings.canDrawOverlays(context),
    mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        == PackageManager.PERMISSION_GRANTED,
)
