package com.liujii.ledger

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.liujii.ledger.data.AppearanceSettings
import com.liujii.ledger.data.CategoryEntity
import com.liujii.ledger.data.MoneyType
import com.liujii.ledger.data.StatementDateRange
import com.liujii.ledger.data.TransactionRow
import com.liujii.ledger.data.statisticsSummary
import com.liujii.ledger.ui.LiuJiTheme
import com.liujii.ledger.ui.StatementReportContent
import com.liujii.ledger.ui.StatementReportLinks
import com.liujii.ledger.ui.money
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StatementReport119ComposeTest {
    @get:Rule val compose=createComposeRule()

    private val day=LocalDate.of(2026,11,2)
    private val zone=ZoneId.systemDefault()
    private val occurredAt=day.atTime(12,0).atZone(zone).toInstant().toEpochMilli()
    private val categories=listOf(
        CategoryEntity(1,"餐饮",MoneyType.EXPENSE,"餐",0xFFE0A04B),
        CategoryEntity(2,"工资",MoneyType.INCOME,"薪",0xFF2FAE83),
    )
    private val rows=listOf(
        TransactionRow(1,MoneyType.EXPENSE,1250,1,1,occurredAt,"午餐",null,"餐饮","餐",0xFFE0A04B,"日常",1,"微信",0xFF2FAE83),
        TransactionRow(2,MoneyType.INCOME,880000,2,1,occurredAt,"工资到账",null,"工资","薪",0xFF2FAE83,"工作",1,"银行卡",0xFF4D8BB8),
    )

    @Test fun monthAndYearEntriesRemainClickableWithShortLabels() {
        var monthClicked=false
        var yearClicked=false
        compose.setContent {
            LiuJiTheme(AppearanceSettings(themeMode="dark")) {
                Box(Modifier.fillMaxSize().padding(20.dp)) {
                    StatementReportLinks("2026 年 11 月","2026 年",{monthClicked=true},{yearClicked=true})
                }
            }
        }
        compose.onNodeWithText("月总账单").assertIsDisplayed().assertHasClickAction().performClick()
        compose.onNodeWithText("年总账单").assertIsDisplayed().assertHasClickAction().performClick()
        compose.runOnIdle {
            assertEquals(true,monthClicked)
            assertEquals(true,yearClicked)
        }
    }

    @Test fun reportShowsTotalsCategoriesAndOpensTransaction() {
        val summary=statisticsSummary(rows,LocalDate.of(2026,11,1),LocalDate.of(2026,11,30),zone)
        val edited=mutableStateOf<Long?>(null)
        var previous=0
        compose.setContent {
            LiuJiTheme(AppearanceSettings(themeMode="light")) {
                StatementReportContent(
                    title="2026 年 11 月",
                    scopeLabel="月总账单",
                    range=StatementDateRange(LocalDate.of(2026,11,1),LocalDate.of(2026,11,30)),
                    summary=summary,
                    categories=categories,
                    previousLabel="上一个月",
                    nextLabel="下一个月",
                    canNext=false,
                    canCurrent=true,
                    onPrevious={previous++},
                    onNext={},
                    onCurrent={},
                    onEdit={edited.value=it.id},
                )
            }
        }
        compose.onNodeWithText("2026 年 11 月").assertIsDisplayed()
        compose.onNodeWithText(money(summary.incomeCents)).assertIsDisplayed()
        compose.onNodeWithText(money(-summary.expenseCents)).assertIsDisplayed()
        compose.onNodeWithContentDescription("上一个月").performClick()
        compose.runOnIdle { assertEquals(1,previous) }
        val list=compose.onNode(hasScrollAction())
        list.performScrollToNode(hasText("支出分类"))
        compose.onNodeWithText("支出分类").assertIsDisplayed()
        list.performScrollToNode(hasText("收入分类"))
        compose.onNodeWithText("收入分类").assertIsDisplayed()
        list.performScrollToNode(hasText("全部流水"))
        compose.onNodeWithText("全部流水").assertIsDisplayed()
        list.performScrollToNode(hasText("午餐"))
        compose.onNodeWithText("午餐").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1L,edited.value) }
    }
}
