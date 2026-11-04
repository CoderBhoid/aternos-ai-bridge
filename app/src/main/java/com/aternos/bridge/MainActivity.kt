package com.aternos.bridge

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.aternos.bridge.data.EncryptedConfigRepository
import com.aternos.bridge.service.AternosBridgeService
import com.aternos.bridge.ui.navigation.AppNavHost
import com.aternos.bridge.ui.navigation.Routes
import com.aternos.bridge.ui.theme.AternosBridgeTheme
import com.aternos.bridge.ui.theme.Slate950

class MainActivity : ComponentActivity() {

    private val tag = "MainActivity"
    private lateinit var configRepository: EncryptedConfigRepository
    private var isInPipMode by mutableStateOf(false)

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.i(tag, "Notification permission granted: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        configRepository = EncryptedConfigRepository.getInstance(this)
        checkNotificationPermission()

        val config = configRepository.getConfig()
        val startDestination = if (config.apiKey.isNotBlank()) {
            Routes.HUD
        } else {
            Routes.SETUP
        }

        // Start background bridge service
        if (config.apiKey.isNotBlank()) {
            AternosBridgeService.start(this)
        }

        setContent {
            AternosBridgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Slate950
                ) {
                    val navController = rememberNavController()
                    AppNavHost(
                        navController = navController,
                        configRepository = configRepository,
                        isInPipMode = isInPipMode,
                        startDestination = startDestination
                    )
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        Log.d(tag, "Picture-in-Picture mode changed: $isInPictureInPictureMode")
    }
}
