package com.liujii.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.liujii.ledger.ui.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class Motion117ComposeTest {
    @get:Rule val compose=createComposeRule(effectContext=object:MotionDurationScale{override val scaleFactor=1f})
    @Test fun realNavigationRemainsTouchableWhilePagesAreLeaving() {
        val application=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as LiuJiApplication
        val store=androidx.lifecycle.ViewModelStore()
        val vm=androidx.lifecycle.ViewModelProvider(store,androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application))[LedgerViewModel::class.java]
        try{
            compose.setContent{LiuJiTheme(vm.appearance.value){CompositionLocalProvider(LocalMotionEnabled provides true){LedgerApp(vm,null){}}}}
            compose.mainClock.autoAdvance=false
            for(name in listOf("明细","统计","首页","明细","日历","设置","首页")){
                compose.onNode(hasText(name) and isSelectable()).performTouchInput{click()}
                compose.mainClock.advanceTimeBy(48)
                compose.onNode(hasText(name) and isSelectable()).assertIsSelected()
            }
            compose.mainClock.advanceTimeBy(1000)
            compose.mainClock.autoAdvance=true
        }finally{store.clear()}
    }
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
    @Test fun outgoingPageCannotInterceptSystemBackDuringTransition() {
        var page by mutableIntStateOf(0)
        var rootBack=0
        var outgoingBack=0
        lateinit var dispatcher:OnBackPressedDispatcher
        compose.setContent {
            dispatcher=LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            BackHandler{rootBack++}
            CompositionLocalProvider(LocalMotionEnabled provides true){MaterialTheme{
                PageTransition(page,{it}){destination->
                    if(destination==0)PageBackHandler(true){outgoingBack++}
                    Text("页面$destination")
                }
            }}
        }
        compose.mainClock.autoAdvance=false
        compose.runOnIdle{page=1}
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle{dispatcher.onBackPressed()}
        org.junit.Assert.assertEquals(1,rootBack)
        org.junit.Assert.assertEquals(0,outgoingBack)
        compose.mainClock.advanceTimeBy(1000)
        compose.mainClock.autoAdvance=true
    }
    @Test fun settingsSubpageScrollReturnsAfterItsExitCompletes() {
        var page by mutableStateOf("appearance")
        var position=0
        lateinit var scroll:androidx.compose.foundation.ScrollState
        compose.setContent{CompositionLocalProvider(LocalMotionEnabled provides true){MaterialTheme{
            StatefulPageTransition(page,{if(it=="root")0 else 1}){destination->
                val state=androidx.compose.foundation.rememberScrollState()
                if(destination=="appearance")scroll=state
                Column(Modifier.fillMaxSize().then(Modifier.verticalScroll(state))){repeat(50){Text("$destination-$it")}}
            }
        }}}
        compose.onNodeWithText("appearance-40").performScrollTo()
        compose.runOnIdle{position=scroll.value;page="root"}
        compose.waitForIdle()
        compose.runOnIdle{page="appearance"}
        compose.waitForIdle()
        compose.runOnIdle{org.junit.Assert.assertTrue(position>0);org.junit.Assert.assertEquals(position,scroll.value)}
    }
}
