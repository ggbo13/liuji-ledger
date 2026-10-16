package com.liujii.ledger

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.liujii.ledger.ui.UnsavedChangesDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UnsavedChangesTest {
    @get:Rule val compose=createComposeRule()

    @Test fun explicitSaveDiscardAndContinueAreSeparateActions() {
        var action=""
        compose.setContent { MaterialTheme { UnsavedChangesDialog(true,true,{action="save"},{action="discard"},{action="continue"}) } }
        compose.onNodeWithText("继续编辑").performClick();assertEquals("continue",action)
        compose.onNodeWithText("放弃修改").performClick();assertEquals("discard",action)
        compose.onNodeWithText("保存",useUnmergedTree=true).performClick();assertEquals("save",action)
    }

    @Test fun invalidFormCannotSaveButCanContinueEditing() {
        compose.setContent { MaterialTheme { UnsavedChangesDialog(true,false,{},{},{}) } }
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNodeWithText("继续编辑").assertIsEnabled()
    }
}
