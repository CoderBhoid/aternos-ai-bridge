package com.aternos.bridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aternos.bridge.bridge.BridgeEventManager
import com.aternos.bridge.data.AiExecutionStatus
import com.aternos.bridge.data.AiHistoryEntry
import com.aternos.bridge.ui.theme.BorderOutline
import com.aternos.bridge.ui.theme.EmeraldGreen
import com.aternos.bridge.ui.theme.LightCard
import com.aternos.bridge.ui.theme.LightSurfaceVariant
import com.aternos.bridge.ui.theme.MonospaceConsole
import com.aternos.bridge.ui.theme.PrimaryOrange
import com.aternos.bridge.ui.theme.RedError
import com.aternos.bridge.ui.theme.SecondaryAmber
import com.aternos.bridge.ui.theme.TextMuted
import com.aternos.bridge.ui.theme.TextPrimary
import com.aternos.bridge.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiHistoryView(
    history: List<AiHistoryEntry>,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text(
                text = "AI In-Game Trigger History (${history.size})",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (history.isNotEmpty()) {
                IconButton(onClick = { BridgeEventManager.clearHistory() }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear History", tint = TextMuted)
                }
            }
        }

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LightCard)
                    .border(1.dp, BorderOutline, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No AI prompts recorded yet.\nType '@server <prompt>' or '!ai <prompt>' in Minecraft chat to trigger the assistant.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(history, key = { it.id }) { entry ->
                    AiHistoryCard(entry, timeFormat)
                }
            }
        }
    }
}

@Composable
private fun AiHistoryCard(entry: AiHistoryEntry, timeFormat: SimpleDateFormat) {
    val (statusColor, statusIcon, statusLabel) = when (entry.status) {
        AiExecutionStatus.COMMAND_SUCCESS -> Triple(EmeraldGreen, Icons.Default.CheckCircle, "COMMAND EXECUTED")
        AiExecutionStatus.COMMAND_BLOCKED -> Triple(RedError, Icons.Default.Block, "GUARDRAIL BLOCKED")
        AiExecutionStatus.CHAT_REPLIED -> Triple(PrimaryOrange, Icons.Default.ChatBubbleOutline, "CHAT BROADCAST")
        AiExecutionStatus.PENDING -> Triple(SecondaryAmber, Icons.Default.HourglassEmpty, "PROCESSING")
        AiExecutionStatus.EXECUTING_COMMAND -> Triple(SecondaryAmber, Icons.Default.HourglassEmpty, "EXECUTING")
        AiExecutionStatus.RATE_LIMITED -> Triple(SecondaryAmber, Icons.Default.Block, "RATE LIMITED")
        AiExecutionStatus.ERROR -> Triple(RedError, Icons.Default.Block, "ERROR")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LightCard)
            .border(1.dp, BorderOutline, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Top row: Player, Provider & Timestamp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(LightSurfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = entry.player,
                        color = PrimaryOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = entry.providerUsed,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Text(
                text = timeFormat.format(Date(entry.timestamp)),
                color = TextMuted,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Inbound Prompt
        Text(
            text = "\"${entry.prompt}\"",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Status Badge Row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = statusIcon,
                contentDescription = statusLabel,
                tint = statusColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = statusLabel,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }

        // Optional details: Command or Chat Response
        if (!entry.commandDispatched.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(LightSurfaceVariant)
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, contentDescription = "cmd", tint = PrimaryOrange, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "/${entry.commandDispatched}",
                        style = MonospaceConsole,
                        color = PrimaryOrange,
                        fontSize = 11.sp
                    )
                }
            }
        }

        entry.responseText?.let { response ->
            if (response.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = response,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        entry.blockedReason?.let { reason ->
            if (reason.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reason: $reason",
                    color = RedError,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
