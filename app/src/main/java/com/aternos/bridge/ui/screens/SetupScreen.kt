package com.aternos.bridge.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aternos.bridge.ai.AiOrchestrator
import com.aternos.bridge.ai.AiProviderType
import com.aternos.bridge.data.AppConfig
import com.aternos.bridge.data.EncryptedConfigRepository
import com.aternos.bridge.ui.theme.BorderOutline
import com.aternos.bridge.ui.theme.EmeraldGreen
import com.aternos.bridge.ui.theme.LightBackground
import com.aternos.bridge.ui.theme.LightCard
import com.aternos.bridge.ui.theme.LightSurfaceVariant
import com.aternos.bridge.ui.theme.OnPrimaryOrangeContainer
import com.aternos.bridge.ui.theme.OrangeOutline
import com.aternos.bridge.ui.theme.PrimaryOrange
import com.aternos.bridge.ui.theme.PrimaryOrangeContainer
import com.aternos.bridge.ui.theme.RedError
import com.aternos.bridge.ui.theme.SecondaryAmber
import com.aternos.bridge.ui.theme.TextMuted
import com.aternos.bridge.ui.theme.TextPrimary
import com.aternos.bridge.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    configRepository: EncryptedConfigRepository,
    onSaveAndConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by configRepository.configFlow.collectAsState()

    var selectedProvider by remember(config.provider) { mutableStateOf(config.providerType) }
    var apiKey by remember(config.apiKey) { mutableStateOf(config.apiKey) }
    var modelName by remember(config.modelName) { mutableStateOf(config.effectiveModel) }
    var customBaseUrl by remember(config.customBaseUrl) { mutableStateOf(config.customBaseUrl) }
    var triggerPrefix by remember(config.triggerPrefix) { mutableStateOf(config.triggerPrefix) }
    var operatorName by remember(config.authorizedOperatorPlayer) { mutableStateOf(config.authorizedOperatorPlayer) }
    var enforceOperatorOnly by remember(config.enforceOperatorOnlyCommands) { mutableStateOf(config.enforceOperatorOnlyCommands) }
    var chatFormat by remember(config.chatBroadcastFormat) { mutableStateOf(config.chatBroadcastFormat) }
    var rateLimitSec by remember(config.rateLimitSeconds) { mutableStateOf(config.rateLimitSeconds.toString()) }

    var isKeyVisible by remember { mutableStateOf(false) }
    var isProviderDropdownExpanded by remember { mutableStateOf(false) }

    var isTestingKey by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<String>?>(null) }

    fun testConnection() {
        isTestingKey = true
        testResult = null
        val tempConfig = config.copy(
            provider = selectedProvider.id,
            apiKey = apiKey.trim(),
            modelName = modelName.trim(),
            customBaseUrl = customBaseUrl.trim()
        )
        scope.launch {
            val orchestrator = AiOrchestrator()
            val res = orchestrator.validateConnection(tempConfig)
            testResult = res
            isTestingKey = false
        }
    }

    fun saveConfig() {
        val parsedRateLimit = rateLimitSec.toIntOrNull()?.coerceIn(1, 120) ?: 10
        val updated = config.copy(
            provider = selectedProvider.id,
            apiKey = apiKey.trim(),
            modelName = modelName.trim(),
            customBaseUrl = customBaseUrl.trim(),
            triggerPrefix = triggerPrefix.trim().ifBlank { "@server" },
            authorizedOperatorPlayer = operatorName.trim().ifBlank { "GamerBhoid" },
            enforceOperatorOnlyCommands = enforceOperatorOnly,
            chatBroadcastFormat = chatFormat.trim().ifBlank { "say [AI] {message}" },
            rateLimitSeconds = parsedRateLimit
        )
        configRepository.saveConfig(updated)
        Toast.makeText(context, "Configuration saved successfully", Toast.LENGTH_SHORT).show()
        onSaveAndConnect()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryOrange),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "AI",
                                tint = LightCard,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Aternos AI Bridge",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Engine Setup & Security Config",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LightCard
                )
            )
        },
        containerColor = LightBackground
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: AI Provider Card
            ConfigCard(title = "AI Intelligence Engine", icon = Icons.Default.Psychology) {
                // Provider Dropdown
                ExposedDropdownMenuBox(
                    expanded = isProviderDropdownExpanded,
                    onExpandedChange = { isProviderDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedProvider.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("AI Provider") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isProviderDropdownExpanded) },
                        colors = outlinedTextFieldColors(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isProviderDropdownExpanded,
                        onDismissRequest = { isProviderDropdownExpanded = false },
                        modifier = Modifier.background(LightCard)
                    ) {
                        AiProviderType.entries.forEach { p ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(p.displayName, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Text("Default: ${p.defaultModel}", fontSize = 11.sp, color = TextMuted)
                                    }
                                },
                                onClick = {
                                    selectedProvider = p
                                    modelName = p.defaultModel
                                    customBaseUrl = if (p.requiresCustomEndpoint) p.defaultBaseUrl else ""
                                    isProviderDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // API Key Field (if needed)
                if (selectedProvider != AiProviderType.OLLAMA_LOCAL) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("${selectedProvider.displayName} API Key") },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = "Key", tint = PrimaryOrange) },
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle visibility",
                                    tint = TextSecondary
                                )
                            }
                        },
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        colors = outlinedTextFieldColors(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (selectedProvider.apiKeyPortalUrl.isNotBlank()) {
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Text(
                                text = "Get API Key ↗",
                                color = PrimaryOrange,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(selectedProvider.apiKeyPortalUrl))
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Model Name
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Model Identifier") },
                    singleLine = true,
                    colors = outlinedTextFieldColors(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Recommended Models Chips
                if (selectedProvider.popularModels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Popular Models:", fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        selectedProvider.popularModels.take(4).forEach { m ->
                            SuggestionChip(
                                onClick = { modelName = m },
                                label = { Text(m, fontSize = 11.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (modelName == m) PrimaryOrangeContainer else LightBackground,
                                    labelColor = if (modelName == m) OnPrimaryOrangeContainer else TextSecondary
                                ),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = if (modelName == m) OrangeOutline else BorderOutline
                                )
                            )
                        }
                    }
                }

                // Custom Base URL
                if (selectedProvider.requiresCustomEndpoint) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customBaseUrl,
                        onValueChange = { customBaseUrl = it },
                        label = { Text("Endpoint Base URL") },
                        placeholder = { Text("https://api.openai.com/v1") },
                        singleLine = true,
                        colors = outlinedTextFieldColors(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Test Connection Button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { testConnection() },
                        enabled = !isTestingKey,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PrimaryOrange
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(PrimaryOrange)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isTestingKey) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PrimaryOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing...", fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Test", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Validate Connection", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Test Result Status Box
                testResult?.let { res ->
                    Spacer(modifier = Modifier.height(10.dp))
                    res.fold(
                        onSuccess = { msg ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(LightSurfaceVariant)
                                    .border(1.dp, EmeraldGreen, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(msg, color = EmeraldGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        },
                        onFailure = { err ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(LightSurfaceVariant)
                                    .border(1.dp, RedError, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Icon(Icons.Default.Error, contentDescription = "Error", tint = RedError, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(err.message ?: "Authentication failed", color = RedError, fontSize = 12.sp)
                            }
                        }
                    )
                }
            }

            // Section 2: In-Game Trigger & Security Rules
            ConfigCard(title = "Minecraft Bridge & Security", icon = Icons.Default.Security) {
                OutlinedTextField(
                    value = triggerPrefix,
                    onValueChange = { triggerPrefix = it },
                    label = { Text("Chat Trigger Prefix") },
                    placeholder = { Text("@server") },
                    singleLine = true,
                    colors = outlinedTextFieldColors(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = operatorName,
                    onValueChange = { operatorName = it },
                    label = { Text("Authorized Operator Username") },
                    singleLine = true,
                    colors = outlinedTextFieldColors(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enforce Operator-Only Commands", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Restricts gamemode, give, tp, weather, time to operator player only.", color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = enforceOperatorOnly,
                        onCheckedChange = { enforceOperatorOnly = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = LightCard,
                            checkedTrackColor = PrimaryOrange,
                            uncheckedTrackColor = BorderOutline
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = rateLimitSec,
                    onValueChange = { rateLimitSec = it },
                    label = { Text("Rate Limit Per Player (seconds)") },
                    singleLine = true,
                    colors = outlinedTextFieldColors(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Section 3: Broadcast Template Card
            ConfigCard(title = "In-Game Chat Broadcast", icon = Icons.Default.Info) {
                OutlinedTextField(
                    value = chatFormat,
                    onValueChange = { chatFormat = it },
                    label = { Text("In-Game Broadcast Format") },
                    placeholder = { Text("say [AI] {message}") },
                    singleLine = true,
                    colors = outlinedTextFieldColors(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Supported tokens: {message}, {player}. Defaults to 'say [AI] {message}' so all players can see it.",
                    color = TextMuted,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Live Preview Box
                Text("Chat Preview:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(LightSurfaceVariant)
                        .border(1.dp, BorderOutline, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = chatFormat
                            .replace("{message}", "Hello from AI assistant!")
                            .replace("{player}", operatorName.ifBlank { "Player" }),
                        color = PrimaryOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Save & Connect Button
            Button(
                onClick = { saveConfig() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryOrange,
                    contentColor = LightCard
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Save & Open Handshake", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConfigCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LightCard)
            .border(1.dp, BorderOutline, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = title, tint = PrimaryOrange, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
        }
        Spacer(modifier = Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryOrange,
    unfocusedBorderColor = BorderOutline,
    focusedLabelColor = PrimaryOrange,
    unfocusedLabelColor = TextSecondary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = LightCard,
    unfocusedContainerColor = LightCard
)
