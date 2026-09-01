package com.caddie.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.caddie.app.overlay.OverlaySettings
import com.caddie.app.studycontrol.NativeStudyControl
import com.caddie.status.GatewayConnectionStatus

/** Current setup state evaluated from system settings. */
data class SetupUiState(
    val accessibility: Boolean,
    val overlay: Boolean,
    val mic: Boolean,
    val pillEnabled: Boolean = true,
    val portalEnabled: Boolean = false,
) {
    fun isReady(): Boolean = accessibility && overlay && mic
}

fun evaluateSetup(context: Context): SetupUiState = SetupUiState(
    accessibility = com.caddie.app.CompanionAccessibilityService.isConnected(),
    overlay = Settings.canDrawOverlays(context),
    mic = android.content.pm.PackageManager.PERMISSION_GRANTED ==
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO),
    pillEnabled = com.caddie.app.overlay.OverlaySettings.isPillEnabled(context),
    portalEnabled = com.caddie.studyportal.StudyPortalSettings.isEnabled(context),
)

/** Native runtime composition state for display. */
data class RuntimeStatus(
    val nativeExecutionEnabled: Boolean,
    val modelGatewayEnabled: Boolean,
    val contextEngineEnabled: Boolean,
)

/**
 * Main setup screen showing permissions and native runtime status.
 * Mirrors the main branch setup flow but adds native runtime indicators.
 */
@Composable
fun SetupScreen(
    state: SetupUiState,
    gatewayStatus: GatewayConnectionStatus,
    runtimeStatus: RuntimeStatus,
    portalUrl: String?,
    studyMode: NativeStudyControl.Mode?,
    studyFeaturesEnabled: Boolean,
    onAccessibilityClick: () -> Unit,
    onOverlayClick: () -> Unit,
    onMicClick: () -> Unit,
    onOpenStudyControl: () -> Unit,
    onOpenRuntimeSettings: () -> Unit,
    onTogglePill: (Boolean) -> Unit,
    onTogglePortal: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (state.isReady()) {
                AllGreenContent(
                    state = state,
                    gatewayStatus = gatewayStatus,
                    runtimeStatus = runtimeStatus,
                    portalUrl = portalUrl,
                    studyMode = studyMode,
                    studyFeaturesEnabled = studyFeaturesEnabled,
                    onTogglePill = onTogglePill,
                    onTogglePortal = onTogglePortal,
                )
            } else {
                SetupContent(
                    state = state,
                    onAccessibilityClick = onAccessibilityClick,
                    onOverlayClick = onOverlayClick,
                    onMicClick = onMicClick,
                    studyFeaturesEnabled = studyFeaturesEnabled,
                )
            }
            IconButton(
                onClick = if (studyFeaturesEnabled) onOpenStudyControl else onOpenRuntimeSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp),
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = if (studyFeaturesEnabled) "Open Study Control" else "Open Runtime Settings",
                )
            }
        }
    }
}

@Composable
private fun SetupContent(
    state: SetupUiState,
    onAccessibilityClick: () -> Unit,
    onOverlayClick: () -> Unit,
    onMicClick: () -> Unit,
    studyFeaturesEnabled: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Caddie",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Grant the required permissions to begin.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PermissionRow(
                icon = Icons.Filled.Visibility,
                title = "Accessibility",
                description = "So the agent can read and control apps.",
                granted = state.accessibility,
                onClick = onAccessibilityClick,
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            PermissionRow(
                icon = Icons.Outlined.Layers,
                title = "Screen overlay",
                description = "Shows the live display while the agent works.",
                granted = state.overlay,
                onClick = onOverlayClick,
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            PermissionRow(
                icon = Icons.Filled.Mic,
                title = if (studyFeaturesEnabled) "Microphone" else "Voice input",
                description = "Enables wake-word and voice input.",
                granted = state.mic,
                onClick = onMicClick,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !granted, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(20.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (granted) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = "enabled",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "open",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun AllGreenContent(
    state: SetupUiState,
    gatewayStatus: GatewayConnectionStatus,
    runtimeStatus: RuntimeStatus,
    portalUrl: String?,
    studyMode: NativeStudyControl.Mode?,
    studyFeaturesEnabled: Boolean,
    onTogglePill: (Boolean) -> Unit,
    onTogglePortal: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Caddie is ready.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Native runtime active — model connection configurable in settings.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            GatewayConnectionIndicator(status = gatewayStatus)
            Spacer(Modifier.height(8.dp))
            if (studyFeaturesEnabled) {
                Text(
                    text = "Study Control: ${studyMode?.label ?: "Normal"}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }

            // Runtime status indicators
            ElevatedCard(shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Runtime Status",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    RuntimeStatusRow("Native Execution", runtimeStatus.nativeExecutionEnabled)
                    RuntimeStatusRow("Model Gateway", runtimeStatus.modelGatewayEnabled)
                    RuntimeStatusRow("Context Engine", runtimeStatus.contextEngineEnabled)
                }
            }

            Spacer(Modifier.height(20.dp))
            ConditionToggle(
                pillEnabled = state.pillEnabled,
                onTogglePill = onTogglePill,
            )
            if (studyFeaturesEnabled) {
                Spacer(Modifier.height(16.dp))
                PortalToggle(
                    portalEnabled = state.portalEnabled,
                    portalUrl = portalUrl,
                    onTogglePortal = onTogglePortal,
                )
            }
        }
    }
}

@Composable
private fun RuntimeStatusRow(label: String, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) Color(0xFF2E7D32)
                    else MaterialTheme.colorScheme.outlineVariant
                ),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun GatewayConnectionIndicator(status: GatewayConnectionStatus) {
    val indicatorColor = when (status) {
        GatewayConnectionStatus.Connected -> Color(0xFF2E7D32)
        GatewayConnectionStatus.Disconnected -> MaterialTheme.colorScheme.error
        GatewayConnectionStatus.Connecting -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(indicatorColor),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = when (status) {
                GatewayConnectionStatus.Connected -> "Model Gateway"
                GatewayConnectionStatus.Disconnected -> "Gateway Offline"
                GatewayConnectionStatus.Connecting -> "Connecting..."
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ConditionToggle(
    pillEnabled: Boolean,
    onTogglePill: (Boolean) -> Unit,
) {
    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTogglePill(!pillEnabled) }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Layers,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "Companion-Anzeige",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (pillEnabled) {
                        "Pill visible while the agent works."
                    } else {
                        "Baseline — agent runs with no visible display."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = pillEnabled, onCheckedChange = onTogglePill)
        }
    }
}

@Composable
private fun PortalToggle(
    portalEnabled: Boolean,
    portalUrl: String?,
    onTogglePortal: (Boolean) -> Unit,
) {
    ElevatedCard(shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTogglePortal(!portalEnabled) }
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Study-Portal hosten",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (portalEnabled) {
                            "Portal läuft — via Tailscale im Laptop-Browser erreichbar."
                        } else {
                            "Hostet das Studien-Portal auf dem Gerät für Investigator/Probant."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = portalEnabled, onCheckedChange = onTogglePortal)
            }
            if (portalEnabled && portalUrl != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = portalUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
