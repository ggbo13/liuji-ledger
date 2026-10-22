package com.liujii.ledger

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.liujii.ledger.data.officialThemePresets
import com.liujii.ledger.ui.themeColorScheme
import org.junit.Assert.*
import org.junit.Test

class ThemeColors117Test {
    private fun contrast(a:Color,b:Color):Float {val x=a.luminance();val y=b.luminance();return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)}
    @Test fun allThemesKeepReadableBodyAndActionText() {
        for(preset in officialThemePresets)for(dark in listOf(false,true)) {
            val c=themeColorScheme(preset,dark)
            for((foreground,background) in listOf(c.onSurface to c.surface,c.onSurfaceVariant to c.surface,c.onBackground to c.background,c.onPrimary to c.primary,c.onPrimaryContainer to c.primaryContainer,c.secondary to c.surface,c.tertiary to c.surface)) {
                assertTrue("${preset.id}/$dark 对比度不足：${contrast(foreground,background)}",contrast(foreground,background)>=4.5f)
            }
        }
    }
    @Test fun themeAccentNeverChangesIncomeOrExpenseMeaning() {
        for(dark in listOf(false,true)) {
            val schemes=officialThemePresets.map{themeColorScheme(it,dark)}
            assertEquals(1,schemes.map{it.secondary}.distinct().size)
            assertEquals(1,schemes.map{it.tertiary}.distinct().size)
            assertTrue(schemes.first().secondary.green>schemes.first().secondary.red)
            assertTrue(schemes.first().tertiary.red>schemes.first().tertiary.green)
        }
    }
    @Test fun fixedContainersUseThemeColorsInsteadOfMaterialDefaults() {
        val mint=themeColorScheme(officialThemePresets[0],false)
        val blue=themeColorScheme(officialThemePresets[1],false)
        assertNotEquals("固定容器也应随主题改变",mint.primaryFixed,blue.primaryFixed)
        for(preset in officialThemePresets)for(dark in listOf(false,true)){
            val c=themeColorScheme(preset,dark)
            assertTrue(contrast(c.onPrimaryFixed,c.primaryFixed)>=4.5f)
            assertTrue(contrast(c.onPrimaryFixedVariant,c.primaryFixedDim)>=4.5f)
        }
    }
}
