package com.caddie.setup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.caddie.accessibility.CompanionAccessibilityService
import com.caddie.overlay.OverlaySettings

data class SetupUiState(
    val accessibility: Boolean,
    val overlay: Boolean,
    val mic: Boolean,
    /** Study condition: whether the companion pill is shown. Not a permission. */
    val pillEnabled: Boolean = true,
) {
    val allGranted: Boolean get() = accessibility && overlay && mic
}

fun evaluateSetup(context: Context): SetupUiState = SetupUiState(
    accessibility = CompanionAccessibilityService.isConnected(),
    overlay = Settings.canDrawOverlays(context),
    mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        == PackageManager.PERMISSION_GRANTED,
    pillEnabled = OverlaySettings.isPillEnabled(context),
)
