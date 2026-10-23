package com.liujii.ledger.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liujii.ledger.data.themePreset
import com.liujii.ledger.data.shouldAnimate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.liujii.ledger.data.AppearanceSettings

val MistBlue = Color(0xFFEAF1F7)
val DeepSlate = Color(0xFF253442)
val Mint = Color(0xFF2FAE83)
val Coral = Color(0xFFE76F64)
val Night = Color(0xFF10171D)
val Paper = Color(0xFFF9FBFC)

private val LightColors = themeColorScheme(themePreset("mint"),false)

private val DarkColors = themeColorScheme(themePreset("mint"),true)

private val LedgerTypography=Typography().let { base->base.copy(
    headlineMedium=base.headlineMedium.copy(fontSize=24.sp,lineHeight=32.sp),
    titleLarge=base.titleLarge.copy(fontSize=20.sp,lineHeight=28.sp),
    titleMedium=base.titleMedium.copy(fontSize=18.sp,lineHeight=26.sp),
    bodyLarge=base.bodyLarge.copy(fontSize=16.sp,lineHeight=24.sp),
    bodyMedium=base.bodyMedium.copy(fontSize=16.sp,lineHeight=24.sp),
    bodySmall=base.bodySmall.copy(fontSize=13.sp,lineHeight=20.sp),
    labelLarge=base.labelLarge.copy(fontSize=14.sp,lineHeight=20.sp),
    labelSmall=base.labelSmall.copy(fontSize=13.sp,lineHeight=18.sp),
)}
private val LedgerShapes=Shapes(extraSmall=RoundedCornerShape(8.dp),small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(20.dp),extraLarge=RoundedCornerShape(24.dp))

@Composable
fun LiuJiTheme(settings: AppearanceSettings, content: @Composable () -> Unit) {
    val dark = settings.themeMode == "dark" || (settings.themeMode == "system" && isSystemInDarkTheme())
    val motion=shouldAnimate(settings.motionEnabled,systemAnimationScale())
    val target=remember(settings.themeId,dark){if(settings.themeId=="mint"){if(dark)DarkColors else LightColors}else themeColorScheme(themePreset(settings.themeId),dark)}
    // 明暗切换直接换色，防止黑白同时插值时短暂变成灰字灰底；同明暗内换主题仍渐变。
    val colors=key(dark){animatedThemeColors(target,motion)}
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                if(Build.VERSION.SDK_INT<35){
                    // 新系统的系统栏由边到边背景承接，旧系统显式使用当前主题颜色。
                    @Suppress("DEPRECATION")
                    window.statusBarColor=colors.background.toArgb()
                    @Suppress("DEPRECATION")
                    window.navigationBarColor=colors.surface.toArgb()
                }
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    CompositionLocalProvider(LocalMotionEnabled provides motion,LocalDarkAppearance provides dark) {
        MaterialTheme(colorScheme=colors,typography=LedgerTypography,shapes=LedgerShapes,content=content)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
