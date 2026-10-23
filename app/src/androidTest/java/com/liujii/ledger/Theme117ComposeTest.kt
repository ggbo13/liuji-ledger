package com.liujii.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.liujii.ledger.data.*
import com.liujii.ledger.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class Theme117ComposeTest(private val themeId:String,private val width:Int,private val scale:Float,private val dark:Boolean) {
    @get:Rule val compose=createComposeRule()
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}_{1}dp_{2}_{3}") fun cases()=officialThemePresets.flatMap{p->listOf(320,360,400).flatMap{w->listOf(1f,2f).flatMap{f->listOf(false,true).map{d->arrayOf<Any>(p.id,w,f,d)}}}}
    }
    @Test fun themeNamesAndSelectionRemainVisibleAtEverySize() {
        var settings by mutableStateOf(selectThemePreset(AppearanceSettings(themeMode=if(dark)"dark"else"light",motionEnabled=false),themeId))
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp,900.dp))){DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale)){
                LiuJiTheme(settings){Background(settings){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)){
                    OfficialThemeGallery(settings){id->settings=selectThemePreset(settings,id)}
                }}}
            }}
        }
        val chosen=themePreset(themeId)
        compose.onNodeWithText(chosen.name).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(chosen.name).performClick()
        compose.onAllNodes(hasStateDescription("使用中")).assertCountEquals(1)
        compose.onNode(hasStateDescription("使用中")).assertIsSelected()
        assertEquals(themeId,settings.themeId)
        val bundle=InstrumentationRegistry.getArguments()
        val directory=java.io.File(bundle.getString("additionalTestOutputDir")?:InstrumentationRegistry.getInstrumentation().targetContext.filesDir.absolutePath,"visual117").apply{mkdirs()}
        val file=directory.resolve("theme_${themeId}_${width}_${scale}_${dark}.png")
        file.outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
}
