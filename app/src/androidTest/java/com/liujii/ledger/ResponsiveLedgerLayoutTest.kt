package com.liujii.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.liujii.ledger.data.*
import com.liujii.ledger.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

class ResponsiveLedgerLayoutTest {
    @get:Rule val compose=createComposeRule()
    private val month=YearMonth.of(2026,10)
    private val row=TransactionRow(1,MoneyType.EXPENSE,479,1,1,1000,"棉签",null,"日常","日",0xFFE76F64,"日常",1,"微信",0xFF4D8BB8)

    @Test fun smallScreenShowsSevenColumnsAndBothAmounts()=checkCalendar(1f,false)
    @Test fun doubleFontDarkCalendarStillShowsAllDatesAndCents()=checkCalendar(2f,true)

    private fun checkCalendar(fontScale:Float,dark:Boolean) {
        var clicked=month.atDay(11)
        compose.setContent {
            val deviceDensity=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity.density,fontScale)) {
                MaterialTheme(colorScheme=if(dark)darkColorScheme()else lightColorScheme()) {
                    var selected by remember{mutableStateOf(clicked)}
                    Box(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        GlassCard(Modifier.fillMaxWidth().padding(horizontal=20.dp),contentPadding=PaddingValues(horizontal=6.dp,vertical=12.dp)) {
                            CalendarMonthGrid(month,selected,mapOf(month.atDay(4) to CalendarDayTotals(1392,12549)),Modifier.testTag("月历网格")){selected=it;clicked=it}
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val grid=compose.onNodeWithTag("月历网格").fetchSemanticsNode().boundsInRoot
        listOf("一","二","三","四","五","六","日").forEach{label->
            val node=compose.onNodeWithText(label,useUnmergedTree=true)
            node.assertIsDisplayed()
            val bounds=node.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left>=grid.left-1f&&bounds.right<=grid.right+1f)
        }
        listOf("+13.92","-125.49").forEach{value->
            val node=compose.onNodeWithText(value,useUnmergedTree=true)
            node.assertIsDisplayed()
            assertNoTextOverflow(node)
        }
        // 逐一滚动到日期只检查纵向可见性；星期日无需任何横向滚动。
        for(day in 1..31) {
            val node=compose.onNodeWithText(day.toString(),useUnmergedTree=true)
            node.performScrollTo().assertIsDisplayed()
            val bounds=node.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left>=grid.left-1f&&bounds.right<=grid.right+1f)
            assertNoTextOverflow(node)
        }
        compose.onNode(hasText("4") and hasContentDescription("4日，收入",substring=true)).performScrollTo().performClick()
        compose.runOnIdle{assertEquals(month.atDay(4),clicked)}
    }

    @Test fun detailBarSpansViewportAndAmountHasSixteenDpInset() {
        var insetPx=0f
        compose.setContent {MaterialTheme {
            insetPx=with(LocalDensity.current){16.dp.toPx()}
            Box(Modifier.width(320.dp).testTag("明细可用宽度")) {
                SwipeRevealTransaction(row,false,{},{},{})
            }
        }}
        val viewport=compose.onNodeWithTag("明细可用宽度").fetchSemanticsNode().boundsInRoot
        val bar=compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).fetchSemanticsNode().boundsInRoot
        assertEquals(viewport.left,bar.left,1f)
        assertEquals(viewport.right,bar.right,1f)
        val amount=compose.onNodeWithText(money(-479),useUnmergedTree=true)
        assertTrue(amount.fetchSemanticsNode().boundsInRoot.right<=bar.right-insetPx+1f)
        assertNoTextOverflow(amount)
    }

    @Test fun largeAmountWithDoubleFontUsesSecondLineWithoutClipping() {
        val largeRow=row.copy(amountCents=99999999999)
        compose.setContent {
            val deviceDensity=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity.density,2f)) {
                MaterialTheme {Box(Modifier.width(320.dp)){SwipeRevealTransaction(largeRow,false,{},{},{})}}
            }
        }
        val amount=compose.onNodeWithText(money(-largeRow.amountCents),useUnmergedTree=true)
        val description=compose.onNodeWithText("日常 · 微信 · ${dateText(largeRow.occurredAt)}",useUnmergedTree=true)
        amount.assertIsDisplayed()
        assertTrue(amount.fetchSemanticsNode().boundsInRoot.top>=description.fetchSemanticsNode().boundsInRoot.bottom)
        assertNoTextOverflow(amount)
    }

    private fun assertNoTextOverflow(node:SemanticsNodeInteraction) {
        val layouts=mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
        assertTrue(layouts.isNotEmpty())
        layouts.forEach{assertFalse("横向：${it.layoutInput.text}，size=${it.size}，段落=${it.multiParagraph.width}×${it.multiParagraph.height}",it.didOverflowWidth);assertFalse("纵向：${it.layoutInput.text}，size=${it.size}，段落=${it.multiParagraph.width}×${it.multiParagraph.height}，超行=${it.multiParagraph.didExceedMaxLines}",it.didOverflowHeight)}
    }
}
