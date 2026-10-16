package com.liujii.ledger

import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.liujii.ledger.data.AppearanceSettings
import com.liujii.ledger.ui.LiuJiTheme
import com.liujii.ledger.ui.TransactionNoteField
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class TransactionNote115Test(private val width: Int, private val fontScale: Float) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var formScroll: ScrollState
    private var fieldWindowBounds = Rect.Zero
    private var viewportWindowBounds = Rect.Zero
    private var originalSoftInputMode = 0

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}dp_字体{1}x")
        fun configurations(): List<Array<Any>> = listOf(320, 360, 400).flatMap { width ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(width, scale) }
        }
    }

    @Before fun configureWindowForRealIme() {
        compose.runOnUiThread {
            val window = compose.activity.window
            originalSoftInputMode = window.attributes.softInputMode
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    @After fun hideKeyboardAndRestoreWindowMode() {
        compose.runOnUiThread {
            val window = compose.activity.window
            WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
            window.setSoftInputMode(originalSoftInputMode)
        }
    }

    private fun setNoteFixture() {
        compose.setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // 高度跟随真实窗口，不用固定屏高或伪造 IME Insets 代替键盘。
                val formHeight = maxHeight
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, formHeight))) {
                    DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                        LiuJiTheme(AppearanceSettings(themeMode = if (fontScale == 2f) "dark" else "light")) {
                            var note by remember { mutableStateOf("") }
                            formScroll = rememberScrollState()
                            Column(Modifier.fillMaxSize().imePadding()) {
                                Column(
                                    Modifier.fillMaxSize().testTag("备注可视区域")
                                        .onGloballyPositioned {
                                            viewportWindowBounds = Rect(it.positionInWindow(), it.size.toSize())
                                        }
                                        .verticalScroll(formScroll).padding(horizontal = 22.dp, vertical = 12.dp),
                                ) {
                                    // 让备注初始位于视区之外，覆盖长表单中的聚焦滚动。
                                    Spacer(Modifier.height(formHeight + 80.dp))
                                    TransactionNoteField(
                                        value = note,
                                        onValueChange = { note = it },
                                        modifier = Modifier.testTag("备注输入")
                                            .onGloballyPositioned {
                                                fieldWindowBounds = Rect(it.positionInWindow(), it.size.toSize())
                                            },
                                    )
                                    Spacer(Modifier.height(240.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun focusingOffscreenNoteAndEnteringTextKeepsEntireFieldInsideScrollViewport() {
        setNoteFixture()
        val field = compose.onNodeWithTag("备注输入")
        field.assertIsNotDisplayed()
        // 直接请求焦点，不先替组件滚动；输入走 Compose 编辑语义，不宣称是真实键盘敲击。
        field.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        field.assertIsFocused()
        assertFieldInsideViewport()
        field.performTextInput("周末杂货与车费")
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("周末杂货与车费")))
        field.assertIsFocused()
        assertFieldInsideViewport()
        compose.runOnIdle { assertTrue("聚焦屏外备注应带动父表单滚动", formScroll.value > 0) }
    }

    @Test fun realSoftwareKeyboardLeavesFocusedAndEditedNoteFullyVisibleAboveIme() {
        setNoteFixture()
        val field = compose.onNodeWithTag("备注输入")
        field.performScrollTo().assertIsDisplayed()
        val beforeKeyboard = compose.runOnIdle { formScroll.value }
        field.performTouchInput { click() }
        field.assertIsFocused()
        compose.runOnUiThread {
            val window = compose.activity.window
            // 请求系统展示当前输入法，不替换 WindowInsets，也不修改设备的全局键盘设置。
            WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.ime())
        }
        val requestedAt = SystemClock.elapsedRealtime()
        compose.waitUntil(timeoutMillis = 6000L) {
            realImeBottom() > 0 || SystemClock.elapsedRealtime() - requestedAt >= 5000L
        }
        assumeTrue("当前设备没有展示真实软件键盘，跳过 IME 遮挡验证；需要启用可用输入法并允许显示软件键盘", realImeBottom() > 0)
        waitForFieldAboveRealIme()
        assertFieldInsideViewport()
        compose.runOnIdle { assertTrue("真实键盘出现后应继续滚动备注到可视区域", formScroll.value > beforeKeyboard) }

        // 真实 IME 必须保持可见；文本由测试编辑语义注入，验证聚焦输入后的布局。
        field.performTextInput("周末杂货")
        field.performTextInput("，咖啡")
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("周末杂货，咖啡")))
        field.assertIsFocused()
        assertTrue("输入后真实软件键盘仍应显示", realImeBottom() > 0)
        waitForFieldAboveRealIme()
        assertFieldInsideViewport()
    }

    private fun assertFieldInsideViewport() {
        val field = compose.onNodeWithTag("备注输入").assertIsDisplayed().getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("备注可视区域").getUnclippedBoundsInRoot()
        // 未裁剪边界可识别半个字段被遮挡的情况，不能只检查 assertIsDisplayed。
        assertTrue("备注左边界超出视区", field.left >= viewport.left - 1.dp)
        assertTrue("备注右边界超出视区", field.right <= viewport.right + 1.dp)
        assertTrue("备注上边界超出视区", field.top >= viewport.top - 1.dp)
        assertTrue("备注下边界超出视区", field.bottom <= viewport.bottom + 1.dp)
    }

    private fun realImeBottom(): Int = compose.runOnUiThread {
        val insets = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
        if (insets != null && insets.isVisible(WindowInsetsCompat.Type.ime())) {
            insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        } else 0
    }

    private fun waitForFieldAboveRealIme() {
        compose.waitUntil(timeoutMillis = 5000L) {
            compose.runOnUiThread {
                val decor = compose.activity.window.decorView
                val insets = ViewCompat.getRootWindowInsets(decor)
                val imeBottom = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                val keyboardTop = decor.height - imeBottom
                insets?.isVisible(WindowInsetsCompat.Type.ime()) == true && imeBottom > 0 &&
                    fieldWindowBounds.height > 0f && viewportWindowBounds.height > 0f &&
                    fieldWindowBounds.left >= viewportWindowBounds.left - 1f &&
                    fieldWindowBounds.right <= viewportWindowBounds.right + 1f &&
                    fieldWindowBounds.top >= viewportWindowBounds.top - 1f &&
                    fieldWindowBounds.bottom <= viewportWindowBounds.bottom + 1f &&
                    fieldWindowBounds.bottom <= keyboardTop + 1f &&
                    viewportWindowBounds.bottom <= keyboardTop + 1f
            }
        }
    }
}
