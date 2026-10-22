package com.liujii.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.liujii.ledger.ui.*
import org.junit.Rule
import org.junit.Test

class Motion117ComposeTest {
    @get:Rule val compose=createComposeRule()
    @Test fun rapidPageChangesKeepSavedInputAndNeverDuplicateStateKeys() {
        var page by mutableIntStateOf(0)
        compose.setContent {
            val holder=rememberSaveableStateHolder()
            CompositionLocalProvider(LocalMotionEnabled provides true){MaterialTheme{
                PageTransition(page,{it},Modifier.fillMaxSize()){destination->holder.SaveableStateProvider(destination){
                    var input by rememberSaveable{mutableStateOf("")}
                    Column{Text("页面$destination");OutlinedTextField(input,{input=it},label={Text("搜索条件")})}
                }}
            }}
        }
        compose.onNode(hasSetTextAction()).performTextInput("保留的筛选")
        compose.mainClock.autoAdvance=false
        for(target in listOf(1,0,2,1,0)){
            compose.runOnIdle{page=target}
            compose.mainClock.advanceTimeBy(48)
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.mainClock.autoAdvance=true
        compose.onNodeWithText("页面0").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("保留的筛选")
    }
    @Test fun disabledMotionImmediatelyShowsOnlyDestination() {
        var page by mutableIntStateOf(0)
        compose.setContent{CompositionLocalProvider(LocalMotionEnabled provides false){MaterialTheme{PageTransition(page,{it}){Text("页面$it")}}}}
        compose.runOnIdle{page=1}
        compose.onNodeWithText("页面1").assertIsDisplayed()
        compose.onNodeWithText("页面0").assertDoesNotExist()
    }
}
