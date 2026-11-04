package com.aternos.bridge.bridge

import com.aternos.bridge.data.AiExecutionStatus
import com.aternos.bridge.data.AiHistoryEntry
import com.aternos.bridge.data.AternosServer
import com.aternos.bridge.data.ConsoleLogEntry
import com.aternos.bridge.data.ServerStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Singleton reactive event bus and state holder connecting the background [AternosBridgeService],
 * JavaScript [AternosNativeBridge], Compose UI HUD, and the Test Bench Simulator.
 */
object BridgeEventManager {

    private const val MAX_LOGS = 500
    private const val MAX_HISTORY = 200

    private val _consoleLogs = MutableStateFlow<List<ConsoleLogEntry>>(emptyList())
    val consoleLogs: StateFlow<List<ConsoleLogEntry>> = _consoleLogs.asStateFlow()

    private val _aiHistory = MutableStateFlow<List<AiHistoryEntry>>(emptyList())
    val aiHistory: StateFlow<List<AiHistoryEntry>> = _aiHistory.asStateFlow()

    private val _serverStatus = MutableStateFlow(ServerStatus.UNKNOWN)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    private val _isBridgePaused = MutableStateFlow(false)
    val isBridgePaused: StateFlow<Boolean> = _isBridgePaused.asStateFlow()

    private val _isHookAttached = MutableStateFlow(false)
    val isHookAttached: StateFlow<Boolean> = _isHookAttached.asStateFlow()

    private val _isCloudflareDetected = MutableStateFlow(false)
    val isCloudflareDetected: StateFlow<Boolean> = _isCloudflareDetected.asStateFlow()

    private val _currentUrl = MutableStateFlow("https://aternos.org/go/")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _discoveredServers = MutableStateFlow<List<AternosServer>>(emptyList())
    val discoveredServers: StateFlow<List<AternosServer>> = _discoveredServers.asStateFlow()

    private val _diagnostics = MutableSharedFlow<String>(extraBufferCapacity = 50)
    val diagnostics: SharedFlow<String> = _diagnostics.asSharedFlow()

    /**
     * Appends a new raw or formatted line to the live console feed.
     */
    fun addConsoleLog(rawText: String, level: String = "INFO", isAiAction: Boolean = false, isSystemNotice: Boolean = false) {
        val entry = ConsoleLogEntry(
            rawText = rawText,
            level = level,
            isAiAction = isAiAction,
            isSystemNotice = isSystemNotice
        )
        _consoleLogs.update { current ->
            val updated = current + entry
            if (updated.size > MAX_LOGS) updated.takeLast(MAX_LOGS) else updated
        }
    }

    /**
     * Adds an initial pending entry to the AI prompt history.
     */
    fun addAiHistory(entry: AiHistoryEntry) {
        _aiHistory.update { current ->
            val updated = listOf(entry) + current
            if (updated.size > MAX_HISTORY) updated.take(MAX_HISTORY) else updated
        }
    }

    /**
     * Updates an existing AI history record (e.g. after response is computed or command is dispatched/blocked).
     */
    fun updateAiHistory(
        id: String,
        status: AiExecutionStatus,
        responseText: String? = null,
        commandDispatched: String? = null,
        explanation: String? = null,
        blockedReason: String? = null
    ) {
        _aiHistory.update { list ->
            list.map { item ->
                if (item.id == id) {
                    item.copy(
                        status = status,
                        responseText = responseText ?: item.responseText,
                        commandDispatched = commandDispatched ?: item.commandDispatched,
                        explanation = explanation ?: item.explanation,
                        blockedReason = blockedReason ?: item.blockedReason
                    )
                } else item
            }
        }
    }

    fun setServerStatus(status: ServerStatus) {
        _serverStatus.value = status
    }

    fun setBridgePaused(paused: Boolean) {
        _isBridgePaused.value = paused
    }

    fun togglePause() {
        _isBridgePaused.update { !it }
    }

    fun setHookAttached(attached: Boolean) {
        _isHookAttached.value = attached
    }

    fun setCloudflareDetected(detected: Boolean) {
        _isCloudflareDetected.value = detected
    }

    fun setCurrentUrl(url: String) {
        _currentUrl.value = url
    }

    fun setDiscoveredServers(servers: List<AternosServer>) {
        _discoveredServers.value = servers
    }

    fun postDiagnostic(message: String) {
        _diagnostics.tryEmit(message)
        addConsoleLog("[Aternos Bridge Diagnostics] $message", level = "WARN", isSystemNotice = true)
    }

    fun clearLogs() {
        _consoleLogs.value = emptyList()
    }

    fun clearHistory() {
        _aiHistory.value = emptyList()
    }
}
