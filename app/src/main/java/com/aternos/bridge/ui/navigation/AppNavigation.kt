package com.aternos.bridge.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.aternos.bridge.data.EncryptedConfigRepository
import com.aternos.bridge.ui.screens.HandshakeScreen
import com.aternos.bridge.ui.screens.HudScreen
import com.aternos.bridge.ui.screens.SetupScreen

object Routes {
    const val SETUP = "setup"
    const val HANDSHAKE = "handshake"
    const val HUD = "hud"
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    configRepository: EncryptedConfigRepository,
    isInPipMode: Boolean,
    startDestination: String = Routes.SETUP,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Routes.SETUP) {
            SetupScreen(
                configRepository = configRepository,
                onSaveAndConnect = {
                    navController.navigate(Routes.HANDSHAKE) {
                        popUpTo(Routes.SETUP) { inclusive = false }
                    }
                }
            )
        }

        composable(Routes.HANDSHAKE) {
            HandshakeScreen(
                configRepository = configRepository,
                onNavigateToHud = {
                    navController.navigate(Routes.HUD) {
                        popUpTo(Routes.HANDSHAKE) { inclusive = false }
                    }
                }
            )
        }

        composable(Routes.HUD) {
            HudScreen(
                configRepository = configRepository,
                onNavigateToSetup = {
                    navController.navigate(Routes.SETUP)
                },
                onNavigateToHandshake = {
                    navController.navigate(Routes.HANDSHAKE)
                },
                isInPipMode = isInPipMode
            )
        }
    }
}
