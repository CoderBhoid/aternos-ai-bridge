package com.aternos.bridge.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aternos.bridge.bridge.BridgeEventManager
import com.aternos.bridge.data.ConsoleLogEntry
import com.aternos.bridge.ui.theme.BorderOutline
import com.aternos.bridge.ui.theme.LightCard
import com.aternos.bridge.ui.theme.MonospaceConsole
import com.aternos.bridge.ui.theme.PrimaryOrange
import com.aternos.bridge.ui.theme.TerminalAiPrefix
import com.aternos.bridge.ui.theme.TerminalBg
import com.aternos.bridge.ui.theme.TerminalCmdHighlight
import com.aternos.bridge.ui.theme.TerminalError
import com.aternos.bridge.ui.theme.TerminalText
import com.aternos.bridge.ui.theme.TerminalTimestamp
import com.aternos.bridge.ui.theme.TerminalUser
import com.aternos.bridge.ui.theme.TerminalWarn
import com.aternos.bridge.ui.theme.TextMuted
import com.aternos.bridge.ui.theme.TextPrimary
import com.aternos.bridge.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConsoleLogView(
    logs: List<ConsoleLogEntry>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var autoscroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val filteredLogs = remember(logs, searchQuery) {
        if (searchQuery.isBlank()) logs
        else logs.filter { it.rawText.contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(filteredLogs.size, autoscroll) {
        if (autoscroll && filteredLogs.isNotEmpty()) {
            listState.scrollToItem(filteredLogs.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Controls Bar (Light Surface)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter console...", fontSize = 12.sp, color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = PrimaryOrange, modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryOrange,
                    unfocusedBorderColor = BorderOutline,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = LightCard,
                    unfocusedContainerColor = LightCard
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Auto-scroll toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Scroll", fontSize = 11.sp, color = TextSecondary)
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = autoscroll,
                    onCheckedChange = { autoscroll = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LightCard,
                        checkedTrackColor = PrimaryOrange,
                        uncheckedTrackColor = BorderOutline
                    ),
                    modifier = Modifier.size(width = 36.dp, height = 24.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Copy All
            IconButton(
                onClick = {
                    val fullLog = logs.joinToString("\n") { it.rawText }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Aternos Logs", fullLog))
                    Toast.makeText(context, "Copied ${logs.size} log lines", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }

            // Clear Log
            IconButton(
                onClick = { BridgeEventManager.clearLogs() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }

        // Terminal Container Window
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(TerminalBg)
                .border(1.dp, BorderOutline, RoundedCornerShape(12.dp))
        ) {
            if (filteredLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No logs matching '$searchQuery'" else "Console stream waiting for output...",
                        color = TerminalTimestamp,
                        style = MonospaceConsole
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { entry ->
                        ConsoleRow(entry, timeFormat)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleRow(entry: ConsoleLogEntry, timeFormat: SimpleDateFormat) {
    val textColor = when {
        entry.isAiAction -> TerminalCmdHighlight
        entry.isSystemNotice -> TerminalAiPrefix
        entry.level == "ERROR" -> TerminalError
        entry.level == "WARN" -> TerminalWarn
        entry.rawText.contains("<") && entry.rawText.contains(">") -> TerminalUser
        entry.rawText.startsWith(">") -> TerminalCmdHighlight
        else -> TerminalText
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Text(
            text = timeFormat.format(Date(entry.timestamp)),
            style = MonospaceConsole,
            fontSize = 10.sp,
            color = TerminalTimestamp,
            modifier = Modifier.width(52.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = entry.rawText,
            style = MonospaceConsole,
            fontSize = 11.sp,
            color = textColor,
            lineHeight = 15.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
