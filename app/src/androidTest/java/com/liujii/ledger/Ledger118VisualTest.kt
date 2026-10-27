package com.liujii.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
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
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class Ledger118VisualTest(private val width:Int,private val scale:Float,private val dark:Boolean) {
    @get:Rule val compose=createComposeRule()
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}dp_{1}_{2}")
        fun cases()=listOf(320,360,400).flatMap{w->listOf(1f,2f).flatMap{f->listOf(false,true).map{d->arrayOf<Any>(w,f,d)}}}
    }
    @Test fun homeKeepsReadableAmountsEditableRowsAndBudgetManagement() {
        val day=LocalDate.of(2026,10,27)
        val time=day.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val rows=listOf(
            TransactionRow(101,MoneyType.EXPENSE,2490,1,1,time,"午餐",null,"餐饮","餐",0xFFE0A04B,"日常",1,"微信",0xFF2FAE83),
            TransactionRow(102,MoneyType.INCOME,280000,2,1,time,"项目收入",null,"工资","薪",0xFF2FAE83,"工作",1,"银行卡",0xFF4D8BB8))
        val summary=statisticsSummary(rows,day.withDayOfMonth(1),day,ZoneId.systemDefault())
        var edited:Long?=null
        var managed=false
        var added=false
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp,860.dp))){
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale)){
                    val settings=AppearanceSettings(themeMode=if(dark)"dark"else"light",motionEnabled=false)
                    LiuJiTheme(settings){Background(settings){
                        Scaffold(containerColor=androidx.compose.ui.graphics.Color.Transparent,bottomBar={LedgerNavigation(listOf("首页","明细","统计","日历","设置").map{LedgerTab(it,Icons.Default.Home)},0,{})}){padding->Box(Modifier.padding(padding)){
                        LedgerHomeContent(day,summary,listOf(-1000,5000,-2490),BudgetEntity(1,"2026-10",null,600000),
                            rows.map{it.occurredAt to it},2,emptyList(),emptySet(),{},{managed=true},{},{added=true},{edited=it.id},{})
                        }}
                    }}
                }
            }
        }
        compose.onNodeWithText("本月结余").assertIsDisplayed()
        compose.onNodeWithContentDescription("记一笔").assertIsDisplayed().performClick()
        compose.runOnIdle{assertTrue(added)}
        val balance=compose.onNodeWithText(money(summary.incomeCents-summary.expenseCents),useUnmergedTree=true)
        balance.assertIsDisplayed()
        val balanceLayouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        balance.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult){it(balanceLayouts)}
        assertTrue(balanceLayouts.isNotEmpty());balanceLayouts.forEach{assertVisibleTextFits(it)}
        if(scale==1f) {
            val first=compose.onNode(hasText("午餐") and hasClickAction()).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val nav=compose.onNode(hasText("首页") and isSelectable()).fetchSemanticsNode().boundsInRoot
            assertTrue("首屏完整露出至少一笔流水",first.bottom<=nav.top)
        }
        capture()
        compose.onNodeWithText("管理").performScrollTo().performClick()
        compose.runOnIdle{assertTrue(managed)}
        compose.onNodeWithText("午餐").performScrollTo().performClick()
        compose.runOnIdle{assertEquals(101L,edited)}
        val moneyNode=compose.onNodeWithText(money(-2490),useUnmergedTree=true)
        moneyNode.performScrollTo().assertIsDisplayed()
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        moneyNode.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult){it(layouts)}
        assertTrue(layouts.isNotEmpty());layouts.forEach{assertVisibleTextFits(it)}
    }
    private fun capture() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val output=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val dir=(output?.let{java.io.File(it)}?:instrumentation.targetContext.filesDir).resolve("visual118").apply{mkdirs()}
        dir.resolve("home_${width}_${scale}_${dark}.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
}
