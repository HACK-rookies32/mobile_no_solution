package com.hack.kidhack.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

private val KidhackColorScheme = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = BrandLight,
    onPrimaryContainer = BrandDark,
    secondary = BrandDark,
    onSecondary = Color.White,
    background = Surface,
    onBackground = Text,
    surface = Card,
    onSurface = Text,
    surfaceVariant = Surface,
    onSurfaceVariant = Muted,
    outline = Border,
    error = Danger,
    onError = Color.White,
)

@Composable
fun KidhackTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKidhackColors provides KidhackColors.Light) {
        MaterialTheme(
            colorScheme = KidhackColorScheme,
            typography = KidhackTypography,
            content = content,
        )
    }
}

val MaterialTheme.kidhackColors: KidhackColors
    @Composable
    @ReadOnlyComposable
    get() = LocalKidhackColors.current
