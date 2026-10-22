package com.liujii.ledger.data

data class ThemePreset(
    val id: String,
    val name: String,
    val lightStart: Long,
    val lightEnd: Long,
    val darkStart: Long,
    val darkEnd: Long,
    val lightAccent: Long,
    val darkAccent: Long,
)

val officialThemePresets = listOf(
    ThemePreset("mint","清透薄荷",0xFFEAF1F7,0xFFD9F1EB,0xFF10171D,0xFF172C29,0xFF286C5D,0xFF86D4BD),
    ThemePreset("sea_blue","雾海蓝",0xFFEDF3FA,0xFFDDE9F5,0xFF111923,0xFF192C40,0xFF345F87,0xFF9DC5EB),
    ThemePreset("warm_apricot","暖杏",0xFFFAF3EA,0xFFF1E1CF,0xFF201A17,0xFF35271E,0xFF875B35,0xFFE4B98E),
    ThemePreset("dusk_violet","暮山紫",0xFFF2EFF8,0xFFE5DEF0,0xFF191620,0xFF2B2337,0xFF70578E,0xFFC9B3E5),
)

fun themePreset(id: String): ThemePreset = officialThemePresets.firstOrNull { it.id == id } ?: officialThemePresets.first()

fun selectThemePreset(settings: AppearanceSettings, id: String): AppearanceSettings {
    val preset = themePreset(id)
    // 图片 URI 留在本机，切回图片时重新校验；不随未启用的背景导出。
    return settings.copy(themeId=preset.id,useThemeBackground=true,backgroundType="gradient",colorStart=preset.lightStart,colorEnd=preset.lightEnd)
}

fun resolveBackgroundColors(settings: AppearanceSettings, dark: Boolean): Pair<Long,Long> {
    if (!settings.useThemeBackground || settings.backgroundType == "image") return settings.colorStart to settings.colorEnd
    val preset=themePreset(settings.themeId)
    return if(dark) preset.darkStart to preset.darkEnd else preset.lightStart to preset.lightEnd
}

fun shouldAnimate(enabled: Boolean, systemScale: Float): Boolean = enabled && systemScale.isFinite() && systemScale > 0f
