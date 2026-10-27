package com.liujii.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.liujii.ledger.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Ledger118ComposeTest {
    @get:Rule val compose=createComposeRule()

    @Test fun fiveBookmarkTabsRemainVisibleAndSelectableAt320DpAndDoubleFont() {
        val labels=listOf("首页","明细","统计","日历","设置")
        var selected=0
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                MaterialTheme {
                    var current by remember{mutableIntStateOf(0)}
                    Box(Modifier.width(320.dp)) { LedgerNavigation(labels.map{LedgerTab(it,Icons.Default.Home)},current){current=it;selected=it} }
                }
            }
        }
        labels.forEachIndexed{index,label->
            compose.onNode(hasText(label) and isSelectable()).assertIsDisplayed().performClick().assertIsSelected()
            compose.runOnIdle{assertEquals(index,selected)}
        }
    }

    @Test fun topAddActionDoesNotOverlapTitleEvenWithLongSubtitle() {
        var opened=0
        compose.setContent {
            MaterialTheme {Box(Modifier.width(320.dp).padding(20.dp)) {LedgerPageHeading("流迹","每一笔，都有去处"){opened++}}}
        }
        val title=compose.onNodeWithText("流迹").fetchSemanticsNode().boundsInRoot
        val add=compose.onNodeWithContentDescription("记一笔")
        assertTrue(add.fetchSemanticsNode().boundsInRoot.left>=title.right)
        add.performClick()
        compose.runOnIdle{assertEquals(1,opened)}
    }

    @Test fun budgetTicksReadActualCentsAndPercentageToTalkBack() {
        compose.setContent{MaterialTheme{Box(Modifier.width(320.dp)){BudgetTicks(479,10000)}}}
        compose.onNodeWithContentDescription("预算使用4%，已用${money(479)}，额度${money(10000)}").assertIsDisplayed()
    }

    @Test fun overBudgetTicksNeverHideActualOverspend() {
        compose.setContent{MaterialTheme{Box(Modifier.width(320.dp)){BudgetTicks(12001,10000)}}}
        compose.onNodeWithContentDescription("预算使用120%，已用${money(12001)}，额度${money(10000)}").assertIsDisplayed()
    }
}
