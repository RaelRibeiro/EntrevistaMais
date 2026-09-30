package com.example.entrevistador.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AzulMarinho = Color(0xFF1B3A6B)
private val AzulMarinhoClaro = Color(0xFF2C5AA0)
private val Areia = Color(0xFF7A5C2E)
private val AreiaClaro = Color(0xFFB08A4A)

private val Claro = lightColorScheme(
    primary = AzulMarinho,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E2F7),
    onPrimaryContainer = Color(0xFF0A1F3D),
    secondary = Areia,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E6CC),
    onSecondaryContainer = Color(0xFF3A2A0C),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE8EAEE),
    onSurfaceVariant = Color(0xFF44474B),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF74777B),
)

private val Escuro = darkColorScheme(
    primary = Color(0xFFA6C8FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF1B3A6B),
    onPrimaryContainer = Color(0xFFD5E2F7),
    secondary = Color(0xFFE3C58C),
    onSecondary = Color(0xFF3F2E05),
    secondaryContainer = Color(0xFF5A4415),
    onSecondaryContainer = Color(0xFFF6E6CC),
    background = Color(0xFF111316),
    onBackground = Color(0xFFE2E2E5),
    surface = Color(0xFF1A1C1F),
    onSurface = Color(0xFFE2E2E5),
    surfaceVariant = Color(0xFF2A2D31),
    onSurfaceVariant = Color(0xFFC3C6CA),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF8D9199),
)

@Composable
fun EntrevistadorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val cores = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val contexto = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(contexto) else dynamicLightColorScheme(contexto)
        }
        darkTheme -> Escuro
        else -> Claro
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val janela = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            janela.statusBarColor = cores.background.toArgb()
            WindowCompat.getInsetsController(janela, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = cores,
        typography = Tipografia,
        content = content,
    )
}
