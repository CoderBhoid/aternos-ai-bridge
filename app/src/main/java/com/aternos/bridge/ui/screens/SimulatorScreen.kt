package com.aternos.bridge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aternos.bridge.ai.AgentResult
import com.aternos.bridge.ai.AiOrchestrator
import com.aternos.bridge.bridge.BridgeEventManager
import com.aternos.bridge.data.AiExecutionStatus
import com.aternos.bridge.data.AiHistoryEntry
import com.aternos.bridge.data.EncryptedConfigRepository
import com.aternos.bridge.security.CommandSanitizer
import com.aternos.bridge.security.SecurityGuardrailFilter
import com.aternos.bridge.security.SecurityValidationResult
import com.aternos.bridge.ui.theme.BorderOutline
import com.aternos.bridge.ui.theme.EmeraldGreen
import com.aternos.bridge.ui.theme.LightBackground
import com.aternos.bridge.ui.theme.LightCard
import com.aternos.bridge.ui.theme.LightSurfaceVariant
import com.aternos.bridge.ui.theme.MonospaceConsole
import com.aternos.bridge.ui.theme.OnPrimaryOrangeContainer
import com.aternos.bridge.ui.theme.OrangeOutline
import com.aternos.bridge.ui.theme.PrimaryOrange
import com.aternos.bridge.ui.theme.PrimaryOrangeContainer
import com.aternos.bridge.ui.theme.RedError
import com.aternos.bridge.ui.theme.SecondaryAmber
import com.aternos.bridge.ui.theme.TerminalBg
import com.aternos.bridge.ui.theme.TerminalCmdHighlight
import com.aternos.bridge.ui.theme.TextMuted
import com.aternos.bridge.ui.theme.TextPrimary
import com.aternos.bridge.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimulatorScreen(
    configRepository: EncryptedConfigRepository,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val config by configRepository.configFlow.collectAsState()

    var simPlayer by remember { mutableStateOf("GamerBhoid") }
    var simPrompt by remember { mutableStateOf("give GamerBhoid mace 1") }
    var isSimulating by remember { mutableStateOf(false) }

    var lastSimResult by remember { mutableStateOf<String?>(null) }
    var lastGuardrailVerdict by remember { mutableStateOf<SecurityValidationResult?>(null) }
    var generatedJsPayload by remember { mutableStateOf<String?>(null) }

    val aiOrchestrator = remember { AiOrchestrator() }

    val presetTests = listOf(
        Pair("how do I craft a mace?", "1.21 Wiki: Mace Recipe"),
        Pair("where do diamonds spawn?", "1.21 Wiki: Diamond Y-Level"),
        Pair("time set day", "Gameplay Command: Time"),
        Pair("weather clear", "Gameplay Command: Weather"),
        Pair("stop", "Guardrail Attack: /stop (Destruction)"),
        Pair("op MaliciousUser", "Guardrail Attack: /op (Escalation)"),
        Pair("execute as @e run stop", "Guardrail Attack: Nested /execute")
    )

    fun runSimulation() {
        if (simPrompt.isBlank()) return
        isSimulating = true
        lastSimResult = null
        lastGuardrailVerdict = null
        generatedJsPayload = null

        val cleanPlayer = CommandSanitizer.sanitizePlayerName(simPlayer)
        val cleanPrompt = CommandSanitizer.sanitizeInboundPrompt(simPrompt)

        scope.launch {
            val result = aiOrchestrator.processPrompt(cleanPlayer, cleanPrompt, config)
            val historyId = java.util.UUID.randomUUID().toString()

            when (result) {
                is AgentResult.ExecuteCommand -> {
                    val proposed = result.command.trim()
                    val validation = SecurityGuardrailFilter.validate(
                        rawCommand = proposed,
                        player = cleanPlayer,
                        authorizedOperator = config.authorizedOperatorPlayer,
                        enforceOperatorOnly = config.enforceOperatorOnlyCommands
                    )
                    lastGuardrailVerdict = validation

                    val finalCmd = if (validation is SecurityValidationResult.Allowed) validation.sanitizedCommand else proposed
                    lastSimResult = "AI Proposed Command: '$proposed'\nExplanation: '${result.explanation ?: "None"}'"

                    // Simulate JS injection script
                    val quoted = JSONObject.quote(finalCmd.removePrefix("/"))
                    generatedJsPayload = "const input = document.querySelector('#console-input');\ninput.value = $quoted;\ninput.dispatchEvent(new Event('input'));"

                    BridgeEventManager.addAiHistory(
                        AiHistoryEntry(
                            id = historyId,
                            player = cleanPlayer,
                            prompt = cleanPrompt,
                            providerUsed = "[SIM] ${config.providerType.displayName}",
                            status = if (validation is SecurityValidationResult.Allowed) AiExecutionStatus.COMMAND_SUCCESS else AiExecutionStatus.COMMAND_BLOCKED,
                            commandDispatched = finalCmd,
                            explanation = result.explanation,
                            blockedReason = (validation as? SecurityValidationResult.Blocked)?.reason
                        )
                    )
                }
                is AgentResult.ChatMessage -> {
                    lastSimResult = "AI Replied (In-Game Chat):\n\"${result.replyText}\""
                    val sampleBroadcast = CommandSanitizer.formatInGameBroadcast(result.replyText.take(120), cleanPlayer, config.chatBroadcastFormat)
                    val quoted = JSONObject.quote(sampleBroadcast)
                    generatedJsPayload = "const input = document.querySelector('#console-input');\ninput.value = $quoted;\ninput.dispatchEvent(new Event('input'));"

                    BridgeEventManager.addAiHistory(
                        AiHistoryEntry(
                            id = historyId,
                            player = cleanPlayer,
                            prompt = cleanPrompt,
                            providerUsed = "[SIM] ${config.providerType.displayName}",
                            status = AiExecutionStatus.CHAT_REPLIED,
                            responseText = result.replyText
                        )
                    )
                }
                is AgentResult.Error -> {
                    lastSimResult = "AI Error: ${result.errorMessage}"
                }
            }
            isSimulating = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Test Input Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(LightCard)
                .border(1.dp, BorderOutline, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Text("Interactive Test Bench", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Test AI prompts, Wikipedia RAG synthesis, and verify security guardrails without touching the live server.",
                fontSize = 11.sp,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = simPlayer,
                onValueChange = { simPlayer = it },
                label = { Text("Simulated Player") },
                singleLine = true,
                colors = outlinedColors(),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = simPrompt,
                onValueChange = { simPrompt = it },
                label = { Text("Simulated Prompt (after !ai)") },
                singleLine = true,
                colors = outlinedColors(),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Presets
            Text("Quick Presets:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                presetTests.forEach { (prompt, label) ->
                    SuggestionChip(
                        onClick = { simPrompt = prompt },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (simPrompt == prompt) PrimaryOrangeContainer else LightBackground,
                            labelColor = if (simPrompt == prompt) OnPrimaryOrangeContainer else TextSecondary
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = if (simPrompt == prompt) OrangeOutline else BorderOutline
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { runSimulation() },
                enabled = !isSimulating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryOrange,
                    contentColor = LightCard
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSimulating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = LightCard)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Processing with ${config.providerType.displayName}...", fontSize = 13.sp)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Run", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Execute Test Prompt", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Guardrail Filter Verdict Box
        lastGuardrailVerdict?.let { verdict ->
            val isAllowed = verdict is SecurityValidationResult.Allowed
            val boxBg = LightSurfaceVariant
            val borderColor = if (isAllowed) EmeraldGreen else RedError
            val iconTint = if (isAllowed) EmeraldGreen else RedError

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(boxBg)
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = if (isAllowed) Icons.Default.CheckCircle else Icons.Default.Block,
                    contentDescription = "Verdict",
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isAllowed) "GUARDRAIL PASSED" else "GUARDRAIL BLOCKED",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = iconTint
                    )
                    Text(
                        text = when (verdict) {
                            is SecurityValidationResult.Allowed -> "Sanitized: /${verdict.sanitizedCommand}"
                            is SecurityValidationResult.Blocked -> verdict.reason
                        },
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }
            }
        }

        // Result Output Card
        lastSimResult?.let { resultText ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LightCard)
                    .border(1.dp, BorderOutline, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = "AI Result", tint = PrimaryOrange, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("AI Response Output", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(LightBackground)
                        .padding(10.dp)
                ) {
                    Text(
                        text = resultText,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Injected JS Code Preview Card
        generatedJsPayload?.let { jsCode ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LightCard)
                    .border(1.dp, BorderOutline, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, contentDescription = "JS", tint = PrimaryOrange, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generated DOM Injection Payload", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TerminalBg)
                        .padding(10.dp)
                ) {
                    Text(
                        text = jsCode,
                        style = MonospaceConsole,
                        color = TerminalCmdHighlight,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryOrange,
    unfocusedBorderColor = BorderOutline,
    focusedLabelColor = PrimaryOrange,
    unfocusedLabelColor = TextSecondary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = LightCard,
    unfocusedContainerColor = LightCard
)
