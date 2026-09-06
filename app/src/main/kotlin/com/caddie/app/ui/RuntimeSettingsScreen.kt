package com.caddie.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.caddie.app.gateway.GatewayRuntimeSettings
import com.caddie.status.GatewayConnectionStatus
import com.caddie.tool.mcp.client.McpServerStatus
import com.caddie.tool.mcp.config.McpServerSettings
import com.caddie.app.AgentLanguage

/** Shows the non-secret MCP endpoints whose tools Caddie may discover. */
@Composable
fun RuntimeSettingsScreen(
    language: AgentLanguage,
    settings: List<McpServerSettings>,
    statuses: Map<String, McpServerStatus>,
    error: String?,
    onSave: (List<McpServerSettings>) -> Unit,
    onLanguageChanged: (AgentLanguage) -> Unit,
    onRetry: (String) -> Unit,
    gatewaySettings: GatewayRuntimeSettings,
    gatewayStatus: GatewayConnectionStatus,
    gatewayMessage: String?,
    onSaveGateway: (GatewayRuntimeSettings, String?) -> Unit,
    onTestGateway: () -> Unit,
    onSubmitNormalTask: (String) -> Unit,
    onClose: () -> Unit,
    onPersonalContext: () -> Unit = {},
) {
    var adding by remember { mutableStateOf(false) }
    var serverId by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var requiredForStudy by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var gatewayUrl by remember(gatewaySettings.baseUrl) { mutableStateOf(gatewaySettings.baseUrl) }
    var modelId by remember(gatewaySettings.modelId) { mutableStateOf(gatewaySettings.modelId) }
    var thinkingEnabled by remember(gatewaySettings.thinkingEnabled) { mutableStateOf(gatewaySettings.thinkingEnabled) }
    var bearerToken by remember { mutableStateOf("") }
    var clearCredential by remember { mutableStateOf(false) }
    var taskText by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Runtime settings", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "MCP servers publish the tools the agent can use.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClose) { Text("Done") }
            }

            Text(
                "Only server ID and endpoint are stored here. Credentials are never saved in this settings document.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Agent language", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Applies immediately to speech recognition and normal-agent responses. Study tasks remain fixed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AgentLanguage.entries.forEach { option ->
                            FilterChip(
                                selected = language == option,
                                onClick = { onLanguageChanged(option) },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
            }

            (error ?: validationError)?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            ModelConnectionCard(
                url = gatewayUrl,
                modelId = modelId,
                thinkingEnabled = thinkingEnabled,
                bearerToken = bearerToken,
                credentialConfigured = gatewaySettings.credentialConfigured,
                clearCredential = clearCredential,
                status = gatewayStatus,
                message = gatewayMessage,
                onUrlChange = { gatewayUrl = it },
                onModelIdChange = { modelId = it },
                onThinkingChange = { thinkingEnabled = it },
                onBearerTokenChange = { bearerToken = it },
                onClearCredentialChange = { clearCredential = it },
                onSave = {
                    val candidate = GatewayRuntimeSettings(
                        baseUrl = gatewayUrl,
                        modelId = modelId,
                        thinkingEnabled = thinkingEnabled,
                    )
                    validationError = candidate.validationMessage()
                    if (validationError == null) {
                        onSaveGateway(candidate, when {
                            clearCredential -> ""
                            bearerToken.isNotBlank() -> bearerToken
                            else -> null
                        })
                        bearerToken = ""
                        clearCredential = false
                    }
                },
                onTest = onTestGateway,
            )

            NormalTaskCard(
                task = taskText,
                enabled = gatewaySettings.isConfigured,
                onTaskChange = { taskText = it },
                onSubmit = {
                    val trimmed = taskText.trim()
                    if (trimmed.isNotBlank()) {
                        onSubmitNormalTask(trimmed)
                        taskText = ""
                    }
                },
            )

            Button(onClick = onPersonalContext) { Text("Personal memory") }

            settings.forEach { setting ->
                McpServerCard(
                    setting = setting,
                    status = statuses[setting.serverId],
                    onChange = { changed ->
                        onSave(settings.map { if (it.serverId == changed.serverId) changed else it })
                    },
                    onDelete = { onSave(settings.filterNot { it.serverId == setting.serverId }) },
                    onRetry = { onRetry(setting.serverId) },
                )
            }

            if (adding) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = serverId,
                            onValueChange = { serverId = it },
                            label = { Text("Server ID") },
                            supportingText = { Text("Letters, numbers, _ and -") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = endpoint,
                            onValueChange = { endpoint = it },
                            label = { Text("Streamable HTTP endpoint") },
                            placeholder = { Text("https://server.example/mcp") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ToggleRow(
                            title = "Required for study",
                            checked = requiredForStudy,
                            onCheckedChange = { requiredForStudy = it },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val candidate = runCatching {
                                        McpServerSettings(
                                            serverId = serverId.trim(),
                                            endpoint = endpoint.trim(),
                                            requiredForStudy = requiredForStudy,
                                        )
                                    }.getOrElse {
                                        validationError = it.message
                                        return@Button
                                    }
                                    if (settings.any { it.serverId == candidate.serverId }) {
                                        validationError = "Server ID already exists"
                                        return@Button
                                    }
                                    validationError = null
                                    onSave(settings + candidate)
                                    serverId = ""
                                    endpoint = ""
                                    requiredForStudy = false
                                    adding = false
                                },
                            ) { Text("Save server") }
                            TextButton(onClick = { adding = false }) { Text("Cancel") }
                        }
                    }
                }
            } else {
                Button(onClick = { adding = true }) { Text("Add MCP server") }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModelConnectionCard(
    url: String,
    modelId: String,
    thinkingEnabled: Boolean,
    bearerToken: String,
    credentialConfigured: Boolean,
    clearCredential: Boolean,
    status: GatewayConnectionStatus,
    message: String?,
    onUrlChange: (String) -> Unit,
    onModelIdChange: (String) -> Unit,
    onThinkingChange: (Boolean) -> Unit,
    onBearerTokenChange: (String) -> Unit,
    onClearCredentialChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Model connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Connect any OpenAI-compatible endpoint. The optional token stays encrypted on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text("Server URL") },
                placeholder = { Text("https://model.example") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = modelId,
                onValueChange = onModelIdChange,
                label = { Text("Model ID") },
                placeholder = { Text("my-tool-calling-model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = bearerToken,
                onValueChange = onBearerTokenChange,
                label = { Text(if (credentialConfigured) "Bearer token (leave blank to keep)" else "Bearer token (optional)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ToggleRow("Enable model thinking", thinkingEnabled, onThinkingChange)
            if (credentialConfigured) {
                ToggleRow("Remove saved token", clearCredential, onClearCredentialChange)
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                when (status) {
                    GatewayConnectionStatus.Connected -> "Connection: ready"
                    GatewayConnectionStatus.Connecting -> "Connection: checking…"
                    GatewayConnectionStatus.Disconnected -> "Connection: not verified"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave) { Text("Save connection") }
                TextButton(onClick = onTest) { Text("Test saved connection") }
            }
        }
    }
}

@Composable
private fun NormalTaskCard(
    task: String,
    enabled: Boolean,
    onTaskChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Start a task", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Uses the last saved model connection and starts a normal-mode task without the microphone. " +
                    "Study routing and voice command parsing are bypassed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = task,
                onValueChange = onTaskChange,
                label = { Text("What should Caddie do?") },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onSubmit, enabled = enabled && task.isNotBlank()) { Text("Start normal task") }
            if (!enabled) {
                Text("Save a model connection first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun McpServerCard(
    setting: McpServerSettings,
    status: McpServerStatus?,
    onChange: (McpServerSettings) -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(setting.serverId, fontWeight = FontWeight.SemiBold)
                    Text(setting.endpoint, style = MaterialTheme.typography.bodySmall)
                    Text(
                        when {
                            !setting.enabled -> "Disabled"
                            status == McpServerStatus.AVAILABLE -> "Connected — tools available"
                            status == McpServerStatus.UNAVAILABLE -> "Unavailable"
                            else -> "Connecting"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete server")
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ToggleRow(
                title = "Enabled",
                checked = setting.enabled,
                onCheckedChange = { onChange(setting.copy(enabled = it)) },
            )
            ToggleRow(
                title = "Required for study preflight",
                checked = setting.requiredForStudy,
                onCheckedChange = { onChange(setting.copy(requiredForStudy = it)) },
            )
            if (setting.enabled && status == McpServerStatus.UNAVAILABLE) {
                TextButton(onClick = onRetry) { Text("Retry connection") }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
