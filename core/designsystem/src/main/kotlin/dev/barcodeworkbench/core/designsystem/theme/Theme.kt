package dev.barcodeworkbench.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * A restrained palette. Barcode rendering demands true black on true white for
 * maximum scan contrast, so the surrounding chrome stays neutral and never tints
 * the symbol area.
 */
private val BrandPrimary = Color(0xFF00639B)
private val BrandPrimaryDark = Color(0xFF9BCBFF)

private val LightScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    secondary = Color(0xFF51606F),
    background = Color(0xFFFCFCFF),
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF1A1C1E),
)

private val DarkScheme = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = Color(0xFF003354),
    secondary = Color(0xFFB8C8DA),
    background = Color(0xFF1A1C1E),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E6),
)

/** Which colours to use, independent of the system setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun WorkbenchTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Material You colour extraction, where the platform supports it. */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WorkbenchTypography,
        content = content,
    )
}
