package com.liujii.ledger

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.liujii.ledger.data.*
import com.liujii.ledger.ui.SwipeRevealTransaction
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class SwipeRevealTest {
    @get:Rule val compose=createComposeRule()
    @Test fun swipingOnlyRevealsDeleteAndClickExecutesAction() {
        var deleted=false
        val row=TransactionRow(1,MoneyType.EXPENSE,1658,1,1,1000,"午餐",null,"餐饮","餐",0xFFE76F64,"日常",1,"现金",0xFF4D8BB8)
        compose.setContent { MaterialTheme { var expanded by remember{mutableStateOf(false)};SwipeRevealTransaction(row,expanded,{expanded=it},{},{deleted=true}) } }
        compose.onNodeWithText("午餐").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertFalse(deleted)
        compose.onNodeWithText("删除").assertIsDisplayed().performClick()
        assertTrue(deleted)
    }
}
