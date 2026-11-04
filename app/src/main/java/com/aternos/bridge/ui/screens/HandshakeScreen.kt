package com.aternos.bridge.ui.screens

import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aternos.bridge.bridge.AternosConsoleClient
import com.aternos.bridge.bridge.BridgeEventManager
import com.aternos.bridge.bridge.WebViewHolder
import com.aternos.bridge.data.AternosServer
import com.aternos.bridge.data.EncryptedConfigRepository
import com.aternos.bridge.ui.components.CloudflareAlertBanner
import com.aternos.bridge.ui.theme.BorderOutline
import com.aternos.bridge.ui.theme.EmeraldGreen
import com.aternos.bridge.ui.theme.LightBackground
import com.aternos.bridge.ui.theme.LightCard
import com.aternos.bridge.ui.theme.LightSurfaceVariant
import com.aternos.bridge.ui.theme.PrimaryOrange
import com.aternos.bridge.ui.theme.TextMuted
import com.aternos.bridge.ui.theme.TextPrimary
import com.aternos.bridge.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandshakeScreen(
    configRepository: EncryptedConfigRepository,
    onNavigateToHud: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUrl by BridgeEventManager.currentUrl.collectAsState()
    val isCloudflareDetected by BridgeEventManager.isCloudflareDetected.collectAsState()
    val discoveredServers by BridgeEventManager.discoveredServers.collectAsState()

    var progress by remember { mutableFloatStateOf(0f) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showServerPickerDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            WebViewHolder.detachFromParent()
        }
    }

    if (discoveredServers.size > 1) {
        showServerPickerDialog = true
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Aternos Login & Handshake",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = currentUrl.take(45).ifBlank { "https://aternos.org/go/" },
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { webViewRef?.goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { webViewRef?.goForward() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", tint = TextSecondary)
                        }
                        IconButton(onClick = {
                            webViewRef?.clearCache(true)
                            webViewRef?.loadUrl("https://aternos.org/go/")
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = TextSecondary)
                        }
                        Button(
                            onClick = onNavigateToHud,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryOrange,
                                contentColor = LightCard
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = "HUD", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open HUD", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = LightCard
                    )
                )
                if (progress in 0.01f..0.99f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = PrimaryOrange,
                        trackColor = BorderOutline,
                        modifier = Modifier.fillMaxWidth().height(3.dp)
                    )
                }
            }
        },
        containerColor = LightBackground
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Interactive canonical WebView container
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        val wv = WebViewHolder.getOrCreateWebView(ctx)
                        webViewRef = wv

                        wv.webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress / 100f
                            }

                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {
                                val newWebView = WebView(view!!.context).apply {
                                    AternosConsoleClient.applyAntiBotWebSettings(this)
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                                            val targetUrl = req?.url?.toString() ?: return false
                                            view.loadUrl(targetUrl)
                                            return true
                                        }
                                    }
                                }
                                val transport = resultMsg?.obj as? WebView.WebViewTransport
                                transport?.webView = newWebView
                                resultMsg?.sendToTarget()
                                return true
                            }
                        }

                        (wv.parent as? ViewGroup)?.removeView(wv)
                        addView(
                            wv,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                },
                update = { container ->
                    val wv = WebViewHolder.getOrCreateWebView(container.context)
                    webViewRef = wv
                    if (wv.parent != container) {
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        container.addView(
                            wv,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Cloudflare challenge banner overlay
            if (isCloudflareDetected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    CloudflareAlertBanner(
                        onSolveClicked = {
                            BridgeEventManager.setCloudflareDetected(false)
                        }
                    )
                }
            }

            // Multi-Server Picker Dialog
            if (showServerPickerDialog && discoveredServers.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { showServerPickerDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dns, contentDescription = "Servers", tint = PrimaryOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Aternos Server", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column {
                            Text(
                                text = "Multiple Minecraft servers found on your Aternos account. Tap one to navigate directly to its console:",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.height(240.dp)
                            ) {
                                items(discoveredServers) { server ->
                                    ServerSelectionCard(
                                        server = server,
                                        onClick = {
                                            showServerPickerDialog = false
                                            selectServerOnWeb(webViewRef, server)
                                        }
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showServerPickerDialog = false }) {
                            Text("Close", color = PrimaryOrange, fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = LightCard,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ServerSelectionCard(
    server: AternosServer,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(LightSurfaceVariant)
            .border(1.dp, BorderOutline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.name.ifBlank { "Minecraft Server" },
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = server.address.ifBlank { "aternos.me" },
                color = TextMuted,
                fontSize = 12.sp
            )
        }
        if (server.software.isNotBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(BorderOutline)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = server.software,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun selectServerOnWeb(webView: WebView?, server: AternosServer) {
    val script = """
        (function() {
            try {
                const targetId = "${server.id}";
                let targetCard = document.querySelector('[data-id="' + targetId + '"]') || document.getElementById(targetId);
                if (!targetCard) {
                    const allCards = document.querySelectorAll('.server-body, .server-card, .serverbox, .servers .server');
                    for (let card of allCards) {
                        if (card.textContent.includes("${server.name}") || card.textContent.includes("${server.address}")) {
                            targetCard = card;
                            break;
                        }
                    }
                }
                if (targetCard) {
                    const link = targetCard.querySelector('a') || targetCard;
                    link.click();
                    return "CLICKED";
                }
                return "NOT_FOUND";
            } catch(e) {
                return "ERR: " + e.message;
            }
        })();
    """.trimIndent()
    webView?.evaluateJavascript(script, null)
}
