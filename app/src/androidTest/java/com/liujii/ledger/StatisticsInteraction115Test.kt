package com.liujii.ledger

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.liujii.ledger.data.AppearanceSettings
import com.liujii.ledger.data.MoneyType
import com.liujii.ledger.data.StatisticsChartSeries
import com.liujii.ledger.data.TransactionRow
import com.liujii.ledger.ui.LiuJiTheme
import com.liujii.ledger.ui.StatisticsCategoryDetails
import com.liujii.ledger.ui.StatisticsTrendChart
import com.liujii.ledger.ui.money
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class StatisticsInteraction115Test(private val width: Int, private val fontScale: Float) {
    @get:Rule val compose = createComposeRule()

    private val series = StatisticsChartSeries(
        dates = (0L..2L).map { LocalDate.of(2026, 10, 13).plusDays(it) },
        income = listOf(123456L, 0L, 98765L),
        expense = listOf(479L, 3866L, 0L),
        maxAmount = 123456L,
    )

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}dp_字体{1}x")
        fun configurations(): List<Array<Any>> = listOf(320, 360, 400).flatMap { width ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(width, scale) }
        }
    }

    @Composable
    private fun TestTheme(content: @Composable () -> Unit) {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, 640.dp))) {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                // 六组配置同时覆盖明暗主题，避免再增加一倍相同的手势用例。
                LiuJiTheme(AppearanceSettings(themeMode = if (fontScale == 2f) "dark" else "light")) {
                    Box(Modifier.fillMaxSize().padding(12.dp)) { content() }
                }
            }
        }
    }

    private fun chart() = compose.onNode(hasContentDescription("收支折线图", substring = true))

    @Test fun pressAndHorizontalDragShowMatchingDateAndAmountsUntilRelease() {
        lateinit var parentScroll: ScrollState
        compose.setContent {
            TestTheme {
                parentScroll = rememberScrollState()
                Column(Modifier.fillMaxSize().verticalScroll(parentScroll)) {
                    StatisticsTrendChart(series)
                    Spacer(Modifier.height(900.dp))
                }
            }
        }
        assertNoTooltip(series)
        chart().performTouchInput { down(Offset(1f, height * .8f)) }
        try {
            assertTooltip(series, 0)
            chart().performTouchInput { moveTo(Offset(centerX, height * .8f)) }
            assertTooltip(series, 1)
            compose.onNodeWithText(series.dates[0].toString(), useUnmergedTree = true).assertDoesNotExist()
            chart().performTouchInput { moveTo(Offset(right - 1f, height * .8f)) }
            assertTooltip(series, 2)
            compose.onNodeWithText(series.dates[1].toString(), useUnmergedTree = true).assertDoesNotExist()
            // 同一次按压向左拖回起点，检查选择不会锁在最后一天。
            chart().performTouchInput { moveTo(Offset(1f, height * .8f)) }
            assertTooltip(series, 0)
            compose.runOnIdle { assertEquals("横向拖动不应滚动父页面", 0, parentScroll.value) }
        } finally {
            chart().performTouchInput { up() }
        }
        assertNoTooltip(series)
    }

    @Test fun verticalDragHandsScrollingToParentAndClearsTooltipBeforeRelease() {
        lateinit var parentScroll: ScrollState
        compose.setContent {
            TestTheme {
                parentScroll = rememberScrollState()
                Column(Modifier.fillMaxSize().verticalScroll(parentScroll).testTag("统计父滚动")) {
                    Spacer(Modifier.height(24.dp))
                    StatisticsTrendChart(series)
                    Spacer(Modifier.height(900.dp))
                }
            }
        }
        chart().performTouchInput { down(Offset(centerX, height * .85f)) }
        try {
            assertTooltip(series, 1)
            // 连续发送竖直移动事件，保留按压以检查父滚动接管时的状态。
            chart().performTouchInput {
                val x = centerX
                val startY = height * .85f
                val distance = height * .6f
                for (step in 1..8) moveTo(Offset(x, startY - distance * step / 8f), delayMillis = 24L)
            }
            compose.runOnIdle { assertTrue("纵向手势必须交还父 verticalScroll", parentScroll.value > 0) }
            assertNoTooltip(series)
        } finally {
            compose.onNodeWithTag("统计父滚动").performTouchInput { up() }
        }
        assertNoTooltip(series)
    }

    @Test fun emptyAndSingleDaySeriesAcceptTouchWithoutCrashing() {
        val empty = StatisticsChartSeries(emptyList(), emptyList(), emptyList(), 1L)
        val single = StatisticsChartSeries(listOf(LocalDate.of(2026, 10, 15)), listOf(0L), listOf(0L), 1L)
        val current = mutableStateOf(empty)
        compose.setContent { TestTheme { Column { StatisticsTrendChart(current.value) } } }
        chart().assertContentDescriptionEquals("收支折线图，暂无数据")
        chart().performTouchInput { down(center) }
        try {
            chart().performTouchInput { moveTo(Offset(right - 1f, centerY)) }
            chart().assertContentDescriptionEquals("收支折线图，暂无数据")
            compose.onNodeWithText("收入 ${money(0L)}", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithText("支出 ${money(0L)}", useUnmergedTree = true).assertDoesNotExist()
        } finally {
            chart().performTouchInput { up() }
        }
        compose.onNodeWithText("● 收入").assertIsDisplayed()
        compose.onNodeWithText("● 支出").assertIsDisplayed()

        compose.runOnIdle { current.value = single }
        assertNoTooltip(single)
        chart().performTouchInput { down(Offset(1f, height * .8f)) }
        try {
            assertTooltip(single, 0)
            chart().performTouchInput { moveTo(Offset(right - 1f, height * .8f)) }
            assertTooltip(single, 0)
        } finally {
            chart().performTouchInput { up() }
        }
        assertNoTooltip(single)
    }

    @Test fun manyCategoryDetailsRemainLazyAndLastRowCanBeScrolledToAndEdited() {
        val rows = (1L..120L).map { id ->
            TransactionRow(
                id = id, type = MoneyType.EXPENSE, amountCents = 479L + id,
                categoryId = 1L, accountId = 1L,
                occurredAt = LocalDate.of(2026, 10, 15).atTime(12, 0)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                note = "第${id}笔分类明细", recurringRuleId = null,
                categoryName = "日常", categoryIcon = "日", categoryColor = 0xFFE76F64,
                accountName = "日常账本", paymentMethodId = 1L,
                paymentMethodName = "微信", paymentMethodColor = 0xFF4D8BB8,
            )
        }
        val edited = mutableListOf<TransactionRow>()
        compose.setContent { TestTheme { StatisticsCategoryDetails(rows) { edited.add(it) } } }
        val list = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        list.assertIsDisplayed()
        compose.onNodeWithText(rows.first().note).assertIsDisplayed()
        // 末行初始未进入组合，防止把 LazyColumn 回退成一次性加载全部条目的列表。
        compose.onNodeWithText(rows.last().note).assertDoesNotExist()
        list.performScrollToIndex(rows.lastIndex)
        val last = compose.onNodeWithText(rows.last().note).assertIsDisplayed().assertHasClickAction()
        last.performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(rows.last()), edited) }
        compose.onNodeWithText(rows.first().note).assertDoesNotExist()
    }

    private fun assertTooltip(expected: StatisticsChartSeries, index: Int) {
        chart().assertContentDescriptionEquals(
            "收支折线图。${expected.dates[index]}，收入${money(expected.income[index])}，支出${money(expected.expense[index])}",
        )
        val area = chart().getUnclippedBoundsInRoot()
        listOf(expected.dates[index].toString(), "收入 ${money(expected.income[index])}", "支出 ${money(expected.expense[index])}")
            .forEach { text ->
                val node = compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
                val bounds = node.getUnclippedBoundsInRoot()
                assertTrue(
                    "提示文字应完整位于图表内：$text",
                    bounds.left >= area.left - 1.dp && bounds.right <= area.right + 1.dp &&
                        bounds.top >= area.top - 1.dp && bounds.bottom <= area.bottom + 1.dp,
                )
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue(layouts.isNotEmpty())
                layouts.forEach { assertFalse(it.didOverflowWidth); assertFalse(it.didOverflowHeight) }
            }
    }

    private fun assertNoTooltip(expected: StatisticsChartSeries) {
        expected.dates.forEach { compose.onNodeWithText(it.toString(), useUnmergedTree = true).assertDoesNotExist() }
        expected.income.distinct().forEach { compose.onNodeWithText("收入 ${money(it)}", useUnmergedTree = true).assertDoesNotExist() }
        expected.expense.distinct().forEach { compose.onNodeWithText("支出 ${money(it)}", useUnmergedTree = true).assertDoesNotExist() }
    }
}
