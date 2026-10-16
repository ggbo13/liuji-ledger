package com.liujii.ledger

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.liujii.ledger.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TransactionDraftComposeTest {
    @get:Rule val compose=createComposeRule()
    private val beer=TransactionDraft(MoneyType.EXPENSE,"38.86",1,1,1,"2026-10-09","啤酒")

    @Test fun disposeInSameFrameAsSaveCannotRestoreFinishedEntry() {
        var stored:TransactionDraft?=beer
        compose.setContent { MaterialTheme {
            var visible by remember{mutableStateOf(true)}
            if(visible) {
                val session=remember{TransactionDraftSession{stored=it}}
                // 刻意让销毁回调持有旧内容，模拟用户报告的同帧退出场景。
                DisposableEffect(session){onDispose{session.persist(beer)}}
                Button(onClick={session.finish(null);visible=false}){Text("模拟保存成功")}
            } else Text("已退出")
        } }
        compose.onNodeWithText("模拟保存成功").performClick()
        compose.onNodeWithText("已退出").assertIsDisplayed()
        compose.runOnIdle{assertNull(stored)}
    }

    @Test fun preserveAndExitIsNotOverwrittenByOldDisposeSnapshot() {
        var stored:TransactionDraft?=null
        val modified=beer.copy(amount="39.00",note="修改后的草稿")
        compose.setContent { MaterialTheme {
            var visible by remember{mutableStateOf(true)}
            if(visible) {
                val session=remember{TransactionDraftSession{stored=it}}
                DisposableEffect(session){onDispose{session.persist(beer)}}
                Button(onClick={session.finish(modified);visible=false}){Text("保留并退出")}
            } else Text("已退出")
        } }
        compose.onNodeWithText("保留并退出").performClick()
        compose.onNodeWithText("已退出").assertIsDisplayed()
        compose.runOnIdle{assertEquals(modified,stored)}
    }
}
