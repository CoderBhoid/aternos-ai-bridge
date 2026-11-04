package com.aternos.bridge.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightOrangeColorScheme = lightColorScheme(
    primary = PrimaryOrange,
    onPrimary = LightCard,
    primaryContainer = PrimaryOrangeContainer,
    onPrimaryContainer = OnPrimaryOrangeContainer,
    secondary = SecondaryAmber,
    onSecondary = LightCard,
    secondaryContainer = SecondaryAmberContainer,
    onSecondaryContainer = SecondaryAmber,
    tertiary = EmeraldGreen,
    onTertiary = LightCard,
    background = LightBackground,
    onBackground = TextPrimary,
    surface = LightCard,
    onSurface = TextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = BorderOutline,
    outlineVariant = OrangeOutline,
    error = RedError,
    onError = LightCard,
    errorContainer = RedErrorContainer,
    onErrorContainer = RedError
)

@Composable
fun AternosBridgeTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightOrangeColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = LightCard.toArgb()
                window.navigationBarColor = LightCard.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = true
                controller.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
