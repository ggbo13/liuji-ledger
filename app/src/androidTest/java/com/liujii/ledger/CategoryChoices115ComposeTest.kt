package com.liujii.ledger

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.liujii.ledger.data.CategoryEntity
import com.liujii.ledger.data.MoneyType
import com.liujii.ledger.ui.CategoryChoices
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CategoryChoices115ComposeTest(private val width: Int, private val fontScale: Float) {
    @get:Rule val compose = createComposeRule()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}dp_字体{1}x")
        fun configurations(): List<Array<Any>> = listOf(320, 360, 400).flatMap { width ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(width, scale) }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun choicesWrapInManualOrderWithAccessibleSingleLineLabelsAndLargeTouchTargets() {
        val longName = "家庭生活与跨城出行的超长分类名称".repeat(4)
        fun category(id: Long, name: String, order: Int) =
            CategoryEntity(id, name, MoneyType.EXPENSE, "类", 0xFFE76F64, order)
        val expected = listOf(
            category(1, "餐饮", 10), category(3, "日", 10), category(4, "购物与日用", 20),
            category(5, "城际交通与通勤", 30), category(7, longName, 40), category(8, "家居维修", 50),
        )
        val input = listOf(expected[3], expected[1], expected[4], expected[0], expected[5], expected[2]) +
            category(9, "不提供的停用项", 0).copy(active = false) +
            category(10, "工资", -1).copy(type = MoneyType.INCOME)
        var minTouchPx = 0f
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, 640.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                    MaterialTheme {
                        minTouchPx = with(LocalDensity.current) { 48.dp.toPx() }
                        var selected by remember { mutableLongStateOf(1) }
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp).testTag("分类可用区域")) {
                                CategoryChoices(input, MoneyType.EXPENSE, selected) { selected = it }
                            }
                        }
                    }
                }
            }
        }
        val area = compose.onNodeWithTag("分类可用区域").fetchSemanticsNode().boundsInRoot
        val bounds = expected.map { category ->
            // 默认合并树命中可点击标签；未合并树中的 Text 保留完整名称和文本布局信息。
            val chip = compose.onNodeWithText(category.name).assertIsDisplayed().assertHasClickAction()
            val rect = chip.fetchSemanticsNode().boundsInRoot
            assertTrue(rect.left >= area.left - 1f && rect.right <= area.right + 1f)
            assertTrue(rect.width >= minTouchPx - 1f && rect.height >= minTouchPx - 1f)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(category.name, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            layouts.forEach { layout ->
                assertEquals(category.name, layout.layoutInput.text.text)
                assertEquals(1, layout.lineCount)
                assertEquals(TextAlign.Center, layout.layoutInput.style.textAlign)
                assertFalse("分类=${category.name}，size=${layout.size}，段落=${layout.multiParagraph.width}×${layout.multiParagraph.height}，超行=${layout.multiParagraph.didExceedMaxLines}",layout.didOverflowHeight)
                if (category.name == longName) assertTrue(layout.isLineEllipsized(0))
            }
            rect
        }
        assertTrue(bounds.any { it.top > bounds.first().top + 1f })
        bounds.zipWithNext().forEach { (before, after) ->
            assertTrue(after.top > before.top + 1f || (kotlin.math.abs(after.top - before.top) <= 1f && after.left >= before.right))
        }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).assertCountEquals(0)
        compose.onNodeWithText("不提供的停用项").assertDoesNotExist()
        compose.onNodeWithText("工资").assertDoesNotExist()
        compose.onNodeWithText("家居维修").performClick().assertIsSelected()
        compose.onNodeWithText("餐饮").assertIsNotSelected()
    }
}
