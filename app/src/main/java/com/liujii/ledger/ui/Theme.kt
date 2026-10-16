package com.liujii.ledger.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.liujii.ledger.data.AppearanceSettings

val MistBlue = Color(0xFFEAF1F7)
val DeepSlate = Color(0xFF253442)
val Mint = Color(0xFF2FAE83)
val Coral = Color(0xFFE76F64)
val Night = Color(0xFF10171D)
val Paper = Color(0xFFF9FBFC)

private val LightColors = lightColorScheme(
    primary = DeepSlate, secondary = Color(0xFF197653), tertiary = Color(0xFFB34840),
    background = MistBlue, surface = Paper, onPrimary = Color.White,
    secondaryContainer=Color(0xFFDCEFE8),onSecondaryContainer=DeepSlate,
    onBackground = DeepSlate, onSurface = DeepSlate,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C9D8), secondary = Color(0xFF69D0AA), tertiary = Color(0xFFFFA49A),
    background = Night, surface = Color(0xFF182229), onPrimary = Night,
    secondaryContainer=Color(0xFF28473C),onSecondaryContainer=Color(0xFFE6EDF2),
    onBackground = Color(0xFFE6EDF2), onSurface = Color(0xFFE6EDF2),
)

@Composable
fun LiuJiTheme(settings: AppearanceSettings, content: @Composable () -> Unit) {
    val dark = settings.themeMode == "dark" || (settings.themeMode == "system" && isSystemInDarkTheme())
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = androidx.compose.material3.Typography(), content = content)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
