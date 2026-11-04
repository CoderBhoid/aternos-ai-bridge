package com.aternos.bridge.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Rational
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aternos.bridge.bridge.BridgeEventManager
import com.aternos.bridge.data.EncryptedConfigRepository
import com.aternos.bridge.service.AternosBridgeService
import com.aternos.bridge.ui.components.AiHistoryView
import com.aternos.bridge.ui.components.CloudflareAlertBanner
import com.aternos.bridge.ui.components.ConsoleLogView
import com.aternos.bridge.ui.components.ManualCommandBar
import com.aternos.bridge.ui.components.ServerStatusPill
import com.aternos.bridge.ui.theme.BorderOutline
import com.aternos.bridge.ui.theme.EmeraldGreen
import com.aternos.bridge.ui.theme.LightBackground
import com.aternos.bridge.ui.theme.LightCard
import com.aternos.bridge.ui.theme.PrimaryOrange
import com.aternos.bridge.ui.theme.SecondaryAmber
import com.aternos.bridge.ui.theme.TextMuted
import com.aternos.bridge.ui.theme.TextPrimary
import com.aternos.bridge.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HudScreen(
    configRepository: EncryptedConfigRepository,
    onNavigateToSetup: () -> Unit,
    onNavigateToHandshake: () -> Unit,
    isInPipMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val serverStatus by BridgeEventManager.serverStatus.collectAsState()
    val isPaused by BridgeEventManager.isBridgePaused.collectAsState()
    val isHookAttached by BridgeEventManager.isHookAttached.collectAsState()
    val isCloudflareDetected by BridgeEventManager.isCloudflareDetected.collectAsState()
    val consoleLogs by BridgeEventManager.consoleLogs.collectAsState()
    val aiHistory by BridgeEventManager.aiHistory.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val activity = context as? Activity
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            activity?.enterPictureInPictureMode(params)
        }
    }

    fun sendManualCommand(cmd: String) {
        val intent = Intent(context, AternosBridgeService::class.java).apply {
            action = AternosBridgeService.ACTION_DISPATCH_MANUAL_COMMAND
            putExtra(AternosBridgeService.EXTRA_COMMAND, cmd)
        }
        context.startService(intent)
    }

    Scaffold(
        topBar = {
            if (!isInPipMode) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Aternos AI Bridge",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            ServerStatusPill(status = serverStatus)
                        }
                    },
                    actions = {
                        // Pause / Resume Toggle Button
                        IconButton(onClick = { BridgeEventManager.togglePause() }) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isPaused) "Resume" else "Pause",
                                tint = if (isPaused) SecondaryAmber else EmeraldGreen
                            )
                        }

                        // PiP Button
                        IconButton(onClick = { enterPip() }) {
                            Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = TextSecondary)
                        }

                        // Handshake Button
                        IconButton(onClick = onNavigateToHandshake) {
                            Icon(Icons.Default.Language, contentDescription = "Handshake Web", tint = TextSecondary)
                        }

                        // Settings Button
                        IconButton(onClick = onNavigateToSetup) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = LightCard
                    )
                )
            }
        },
        containerColor = LightBackground
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Cloudflare banner alert
            if (isCloudflareDetected && !isInPipMode) {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    CloudflareAlertBanner(onSolveClicked = onNavigateToHandshake)
                }
            }

            // Status strip
            if (!isInPipMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LightCard)
                        .border(1.dp, BorderOutline)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isHookAttached) EmeraldGreen else SecondaryAmber)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isHookAttached) "Console WebSocket Attached" else "Console Hook Waiting...",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (isPaused) {
                        Text(
                            text = "PAUSED",
                            color = SecondaryAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Tab Navigation
            if (!isInPipMode) {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = LightCard,
                    contentColor = PrimaryOrange,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = PrimaryOrange
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        icon = { Icon(Icons.Default.Terminal, contentDescription = "Logs", modifier = Modifier.size(18.dp)) },
                        text = { Text("Console", fontSize = 12.sp, fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal) },
                        selectedContentColor = PrimaryOrange,
                        unselectedContentColor = TextSecondary
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        icon = { Icon(Icons.Default.History, contentDescription = "History", modifier = Modifier.size(18.dp)) },
                        text = { Text("AI Prompts", fontSize = 12.sp, fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal) },
                        selectedContentColor = PrimaryOrange,
                        unselectedContentColor = TextSecondary
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        icon = { Icon(Icons.Default.BugReport, contentDescription = "Simulator", modifier = Modifier.size(18.dp)) },
                        text = { Text("Test Bench", fontSize = 12.sp, fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal) },
                        selectedContentColor = PrimaryOrange,
                        unselectedContentColor = TextSecondary
                    )
                }
            }

            // Tab Contents
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> ConsoleLogView(logs = consoleLogs)
                    1 -> AiHistoryView(history = aiHistory)
                    2 -> SimulatorScreen(configRepository = configRepository)
                }
            }

            // Manual Command Dispatcher Bar at the bottom
            if (!isInPipMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    ManualCommandBar(onSendCommand = { cmd -> sendManualCommand(cmd) })
                }
            }
        }
    }
}
