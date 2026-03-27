package com.example.zerohaus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF16A34A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF15803D),
    secondary = Color(0xFF2563EB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E40AF),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color.White,
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE5E7EB),
    outlineVariant = Color(0xFFD1D5DB),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF15803D),
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = Color(0xFF93B5F8),
    onSecondary = Color(0xFF0A1D3D),
    secondaryContainer = Color(0xFF1E3A5F),
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = Color(0xFFB794F4),
    onTertiary = Color(0xFF1A0A2E),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF1A1C23),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF23252E),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF374151),
    outlineVariant = Color(0xFF4B5563),
    error = Color(0xFFF87171),
    onError = Color(0xFF3B0A0A),
    errorContainer = Color(0xFF501313),
    onErrorContainer = Color(0xFFFECACA)
)

@Composable
fun ZeroHausTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}