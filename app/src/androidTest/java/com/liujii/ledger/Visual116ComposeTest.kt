package com.liujii.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.platform.app.InstrumentationRegistry
import com.liujii.ledger.data.*
import com.liujii.ledger.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class Visual116ComposeTest(private val width: Int, private val fontScale: Float, private val dark: Boolean) {
    @get:Rule val compose=createComposeRule()
    private var expectedBackground=Color.Unspecified
    private val syntheticImage by lazy {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=context.cacheDir.resolve("visual-bg-${java.util.UUID.randomUUID()}.png")
        val bitmap=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.YELLOW)}
        file.outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
        android.net.Uri.fromFile(file).toString()
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}dp_字体{1}_夜间{2}")
        fun configurations():List<Array<Any>> = listOf(320,360,400).flatMap { w -> listOf(1f,2f).flatMap { f -> listOf(false,true).map{d->arrayOf<Any>(w,f,d)} } }
    }
    @Composable private fun Fixture(content:@Composable ()->Unit) {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp,900.dp))){
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)){
                LiuJiTheme(AppearanceSettings(themeMode=if(dark)"dark"else"light",overlayAlpha=.45f)){
                    expectedBackground=MaterialTheme.colorScheme.background.copy(alpha=.45f).compositeOver(Color.Yellow)
                    Background(AppearanceSettings(backgroundType="image",imageUri=syntheticImage,overlayAlpha=.45f)){content()}
                }
            }
        }
    }
    @Test fun transactionHasReadableLargeAmountAndSixteenDpRightSpace() {
        val row=TransactionRow(42,MoneyType.EXPENSE,99999999999,7,1,1700000000000,"很长的备注会优先省略而不是挤掉金额".repeat(4),null,"测试分类","测",0xFFE76F64,"测试账本",2,"测试收支方式",0xFF4D8BB8)
        var inset=0f
        compose.setContent{Fixture{inset=with(LocalDensity.current){16.dp.toPx()};Box(Modifier.fillMaxWidth().testTag("流水视口")){SwipeRevealTransaction(row,false,{},{},{})}}}
        val amount=compose.onNodeWithText(money(-row.amountCents),useUnmergedTree=true)
        amount.assertIsDisplayed()
        val root=compose.onNodeWithTag("流水视口").fetchSemanticsNode().boundsInRoot
        assertTrue(amount.fetchSemanticsNode().boundsInRoot.right<=root.right-inset+1)
        capture("transaction")
        noOverflow(amount)
    }
    @Test fun dialogActionsStayCenteredAndTouchTargetsAreLargeEnough() {
        var clicked=false
        var minimum=0f
        compose.setContent{Fixture{minimum=with(LocalDensity.current){48.dp.toPx()};GlassCard(Modifier.fillMaxWidth()){
            DialogAction("保留草稿并退出",onClick={clicked=true},modifier=Modifier.fillMaxWidth().testTag("操作按钮"))
        }}}
        val button=compose.onNodeWithTag("操作按钮").fetchSemanticsNode().boundsInRoot
        val label=compose.onNodeWithText("保留草稿并退出",useUnmergedTree=true)
        val bounds=label.fetchSemanticsNode().boundsInRoot
        assertTrue(button.height>=minimum-1)
        assertEquals(button.center.x,bounds.center.x,2f)
        assertEquals(button.center.y,bounds.center.y,2f)
        capture("actions")
        noOverflow(label)
        compose.onNodeWithTag("操作按钮").performClick();compose.runOnIdle{assertTrue(clicked)}
    }
    @Test fun restorePreviewShowsCountRangeAndRequiresConfirmation() {
        var confirmed=false
        val preview=BackupPreview("完整备份 v1",1700000000000,42,"2026-01-01 至 2026-10-16",true,null)
        compose.setContent{Fixture{BackupPreviewDialog(preview,false,null,{confirmed=true},{})}}
        compose.onNodeWithText("账目：42 笔").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(preview.dateRange).performScrollTo().assertIsDisplayed()
        compose.runOnIdle{assertFalse(confirmed)}
        capture("preview")
        compose.onNodeWithText("确认恢复").assertIsDisplayed().performClick()
        compose.runOnIdle{assertTrue(confirmed)}
    }
    private fun noOverflow(node:SemanticsNodeInteraction) {
        val layouts=mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
        assertTrue(layouts.isNotEmpty());layouts.forEach{assertFalse("横向：${it.layoutInput.text}，size=${it.size}，段落=${it.multiParagraph.width}×${it.multiParagraph.height}",it.didOverflowWidth);assertFalse("纵向：${it.layoutInput.text}，size=${it.size}，段落=${it.multiParagraph.width}×${it.multiParagraph.height}，超行=${it.multiParagraph.didExceedMaxLines}",it.didOverflowHeight)}
    }
    private fun capture(name:String) {
        // 只保存人工合成的测试界面，不访问真实账本或用户图片。
        if(name!="preview")compose.waitUntil(5_000){
            val image=compose.onAllNodes(isRoot()).onLast().captureToImage()
            val pixel=image.toPixelMap()[image.width-2,image.height-2]
            kotlin.math.abs(pixel.red-expectedBackground.red)<.025f&&
                kotlin.math.abs(pixel.green-expectedBackground.green)<.025f&&
                kotlin.math.abs(pixel.blue-expectedBackground.blue)<.025f
        }
        val directory=InstrumentationRegistry.getInstrumentation().targetContext.filesDir.resolve("visual116").apply{mkdirs()}
        val file=directory.resolve("${name}_${width}_${fontScale}_${dark}.png")
        val image=compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        file.outputStream().use{assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
    }
}
