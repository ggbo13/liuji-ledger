package com.liujii.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.liujii.ledger.data.*
import com.liujii.ledger.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class Ledger118RegressionTest {
    @get:Rule val compose=createComposeRule()

    @Test fun changingZoneOnSameDayRefreshesUpcomingBills() {
        val today=LocalDate.of(2026,10,27)
        var zone by mutableStateOf(ZoneId.of("Asia/Taipei"))
        val at=ZonedDateTime.of(2026,11,4,12,0,0,0,zone).toInstant().toEpochMilli()
        val rule=RecurringRuleEntity(41,"跨时区账单",MoneyType.EXPENSE,100,1,1,RepeatType.MONTHLY,at,null,at)
        val rules=listOf(rule)
        compose.setContent {
            val upcoming=rememberHomeUpcomingRules(rules,today,zone)
            Text("近期账单：${upcoming.size}")
        }
        compose.onNodeWithText("近期账单：0").assertIsDisplayed()
        compose.runOnIdle{zone=ZoneId.of("America/New_York")}
        compose.onNodeWithText("近期账单：1").assertIsDisplayed()
    }

    @Test fun rightToLeftBookmarkStaysBehindTheSelectedTab() {
        var container=Color.Unspecified
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,LocalMotionEnabled provides false) {
                MaterialTheme {
                    container=MaterialTheme.colorScheme.primaryContainer
                    Box(Modifier.width(320.dp).testTag("底栏")) {
                        LedgerNavigation(listOf("首页","明细","统计","日历","设置").map{LedgerTab(it,Icons.Default.Home)},0,{})
                    }
                }
            }
        }
        val root=compose.onNodeWithTag("底栏")
        val home=compose.onNode(hasText("首页") and isSelectable()).assertIsSelected().fetchSemanticsNode().boundsInRoot
        val bounds=root.fetchSemanticsNode().boundsInRoot
        val image=root.captureToImage()
        val pixels=image.toPixelMap()
        val x=(home.center.x-bounds.left).toInt().coerceIn(0,image.width-1)
        // 在书签底部留白处取样，避开顶部标记、文字和图标。
        val y=(home.bottom-bounds.top-4).toInt().coerceIn(0,image.height-1)
        val actual=pixels[x,y]
        assertTrue("首页在RTL应位于右侧",home.center.x>bounds.center.x)
        assertTrue("高亮必须位于选中页签下方",close(actual,container))
    }

    @Test fun budgetActuallyDrawsTwentyTicksAndCorrectFivePercentBoundary() {
        var expense by mutableLongStateOf(0)
        var primary=Color.Unspecified
        compose.setContent {MaterialTheme {
            primary=MaterialTheme.colorScheme.primary
            Surface {Box(Modifier.width(320.dp)) {BudgetTicks(expense,10000,Modifier.testTag("预算刻度"))}}
        }}
        fun coloredRuns(color:Color):Int {
            val image=compose.onNodeWithTag("预算刻度").captureToImage()
            val pixels=image.toPixelMap()
            val y=(image.height-5).coerceAtLeast(0)
            var runs=0;var inside=false
            for(x in 0 until image.width){val matched=close(pixels[x,y],color);if(matched&&!inside)runs++;inside=matched}
            return runs
        }
        assertEquals(0,coloredRuns(primary))
        compose.runOnIdle{expense=500}
        assertEquals("5%恰好填满一格",1,coloredRuns(primary))
        compose.runOnIdle{expense=10000}
        assertEquals("满额应绘制20个独立刻度",20,coloredRuns(primary))
        compose.runOnIdle{expense=12000}
        compose.onNodeWithTag("预算刻度").assertContentDescriptionEquals("预算使用120%，已用${money(12000)}，额度${money(10000)}")
    }

    private fun close(a:Color,b:Color)=kotlin.math.abs(a.red-b.red)<.025f&&kotlin.math.abs(a.green-b.green)<.025f&&kotlin.math.abs(a.blue-b.blue)<.025f
}
