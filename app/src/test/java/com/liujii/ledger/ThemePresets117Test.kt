package com.liujii.ledger

import com.liujii.ledger.data.*
import org.junit.Assert.*
import org.junit.Test

class ThemePresets117Test {
    @Test fun selectingPresetKeepsImageModeAndOverlayPreferencesExceptActiveBackground() {
        val previous = AppearanceSettings(themeMode="dark",backgroundType="image",imageUri="content://local/42",overlayAlpha=.67f,appLockEnabled=true)
        val result = selectThemePreset(previous,"sea_blue")
        assertEquals("sea_blue", result.themeId)
        assertEquals("gradient", result.backgroundType)
        assertEquals("content://local/42", result.imageUri)
        assertEquals("dark", result.themeMode)
        assertEquals(.67f, result.overlayAlpha)
        assertTrue(result.appLockEnabled)
        assertTrue(result.useThemeBackground)
        assertEquals(0xFFEDF3FAL, result.colorStart)
    }
    @Test fun themeBackgroundResolvesNightWithoutChangingStoredFallback() {
        val settings = selectThemePreset(AppearanceSettings(),"warm_apricot")
        assertEquals(0xFF201A17L to 0xFF35271EL, resolveBackgroundColors(settings,true))
        assertEquals(0xFFFAF3EAL to 0xFFF1E1CFL, resolveBackgroundColors(settings,false))
    }
    @Test fun oldCustomGradientIsNotReplacedByTheme() {
        val settings = AppearanceSettings(colorStart=0xFF123456,colorEnd=0xFF654321,useThemeBackground=false)
        assertEquals(0xFF123456L to 0xFF654321L,resolveBackgroundColors(settings,true))
    }
    @Test fun unknownPresetFallsBackToMint() {
        val result=selectThemePreset(AppearanceSettings(),"future-theme")
        assertEquals("mint",result.themeId)
        assertEquals(0xFF10171DL to 0xFF172C29L,resolveBackgroundColors(result,true))
    }
    @Test fun allOfficialPresetsHaveDistinctStableIdentities() {
        assertEquals(listOf("mint","sea_blue","warm_apricot","dusk_violet"),officialThemePresets.map{it.id})
        assertEquals(4,officialThemePresets.map{it.lightAccent}.distinct().size)
        assertEquals("暮山紫",themePreset("dusk_violet").name)
    }
    @Test fun motionRespectsBothUserSwitchAndSystemScale() {
        assertTrue(shouldAnimate(true,1f))
        assertFalse(shouldAnimate(false,1f))
        assertFalse(shouldAnimate(true,0f))
        assertFalse(shouldAnimate(true,Float.NaN))
    }
}
