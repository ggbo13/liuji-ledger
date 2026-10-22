package com.liujii.ledger.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.liujii.ledger.data.ThemePreset

fun themeColorScheme(preset: ThemePreset, dark: Boolean): ColorScheme {
    val accent=Color(if(dark)preset.darkAccent else preset.lightAccent)
    val bg=Color(if(dark)preset.darkStart else preset.lightStart)
    val end=Color(if(dark)preset.darkEnd else preset.lightEnd)
    val surface=if(dark)lerp(bg,Color.White,.045f) else lerp(Color.White,end,.12f)
    val foreground=if(dark)Color(0xFFE6EDF2) else DeepSlate
    val muted=if(dark)Color(0xFFB6C4CE) else Color(0xFF52616B)
    val income=if(dark)Color(0xFF69D0AA) else Color(0xFF197653)
    val expense=if(dark)Color(0xFFFFA49A) else Color(0xFFB34840)
    val base=if(dark)darkColorScheme() else lightColorScheme()
    // 显式覆盖所有表面与容器色，避免 Material 默认紫色混入官方主题。
    return base.copy(
        primary=accent,onPrimary=if(dark)bg else Color.White,
        primaryContainer=lerp(surface,accent,if(dark).22f else .13f),onPrimaryContainer=foreground,
        inversePrimary=Color(if(dark)preset.lightAccent else preset.darkAccent),
        secondary=income,onSecondary=if(dark)bg else Color.White,
        secondaryContainer=lerp(surface,income,.16f),onSecondaryContainer=foreground,
        tertiary=expense,onTertiary=if(dark)bg else Color.White,
        tertiaryContainer=lerp(surface,expense,.16f),onTertiaryContainer=foreground,
        background=bg,onBackground=foreground,surface=surface,onSurface=foreground,
        surfaceVariant=lerp(surface,end,.42f),onSurfaceVariant=muted,
        surfaceTint=accent,inverseSurface=if(dark)Color(0xFFE6EDF2) else DeepSlate,
        inverseOnSurface=if(dark)DeepSlate else Color(0xFFF9FBFC),
        outline=if(dark)Color(0xFF85959F) else Color(0xFF6D7C86),
        outlineVariant=lerp(surface,foreground,if(dark).26f else .19f),scrim=Color.Black,
        error=if(dark)Color(0xFFFFB4AB) else Color(0xFFBA1A1A),
        onError=if(dark)Color(0xFF690005) else Color.White,
        errorContainer=if(dark)Color(0xFF93000A) else Color(0xFFFFDAD6),
        onErrorContainer=if(dark)Color(0xFFFFDAD6) else Color(0xFF410002),
        surfaceDim=lerp(surface,bg,.55f),surfaceBright=lerp(surface,accent,if(dark).12f else .025f),
        surfaceContainerLowest=bg,surfaceContainerLow=lerp(bg,surface,.7f),
        surfaceContainer=surface,surfaceContainerHigh=lerp(surface,accent,.065f),
        surfaceContainerHighest=lerp(surface,accent,.1f),
        primaryFixed=lerp(Color.White,Color(preset.lightAccent),.18f),primaryFixedDim=lerp(Color.White,Color(preset.lightAccent),.30f),
        onPrimaryFixed=DeepSlate,onPrimaryFixedVariant=DeepSlate,
        secondaryFixed=lerp(Color.White,Color(0xFF197653),.18f),secondaryFixedDim=lerp(Color.White,Color(0xFF197653),.30f),
        onSecondaryFixed=DeepSlate,onSecondaryFixedVariant=DeepSlate,
        tertiaryFixed=lerp(Color.White,Color(0xFFB34840),.18f),tertiaryFixedDim=lerp(Color.White,Color(0xFFB34840),.30f),
        onTertiaryFixed=DeepSlate,onTertiaryFixedVariant=DeepSlate,
    )
}

@Composable
internal fun animatedThemeColors(target: ColorScheme, enabled: Boolean): ColorScheme {
    @Composable fun color(value: Color): Color { val result by animateColorAsState(value,tween(if(enabled)250 else 0),label="主题换色");return result }
    return target.copy(
        primary=color(target.primary),onPrimary=color(target.onPrimary),primaryContainer=color(target.primaryContainer),onPrimaryContainer=color(target.onPrimaryContainer),inversePrimary=color(target.inversePrimary),
        secondary=color(target.secondary),onSecondary=color(target.onSecondary),secondaryContainer=color(target.secondaryContainer),onSecondaryContainer=color(target.onSecondaryContainer),
        tertiary=color(target.tertiary),onTertiary=color(target.onTertiary),tertiaryContainer=color(target.tertiaryContainer),onTertiaryContainer=color(target.onTertiaryContainer),
        background=color(target.background),onBackground=color(target.onBackground),surface=color(target.surface),onSurface=color(target.onSurface),surfaceVariant=color(target.surfaceVariant),onSurfaceVariant=color(target.onSurfaceVariant),surfaceTint=color(target.surfaceTint),
        inverseSurface=color(target.inverseSurface),inverseOnSurface=color(target.inverseOnSurface),outline=color(target.outline),outlineVariant=color(target.outlineVariant),
        surfaceDim=color(target.surfaceDim),surfaceBright=color(target.surfaceBright),surfaceContainerLowest=color(target.surfaceContainerLowest),surfaceContainerLow=color(target.surfaceContainerLow),surfaceContainer=color(target.surfaceContainer),surfaceContainerHigh=color(target.surfaceContainerHigh),surfaceContainerHighest=color(target.surfaceContainerHighest),
        primaryFixed=color(target.primaryFixed),primaryFixedDim=color(target.primaryFixedDim),onPrimaryFixed=color(target.onPrimaryFixed),onPrimaryFixedVariant=color(target.onPrimaryFixedVariant),
        secondaryFixed=color(target.secondaryFixed),secondaryFixedDim=color(target.secondaryFixedDim),onSecondaryFixed=color(target.onSecondaryFixed),onSecondaryFixedVariant=color(target.onSecondaryFixedVariant),
        tertiaryFixed=color(target.tertiaryFixed),tertiaryFixedDim=color(target.tertiaryFixedDim),onTertiaryFixed=color(target.onTertiaryFixed),onTertiaryFixedVariant=color(target.onTertiaryFixedVariant),
    )
}
