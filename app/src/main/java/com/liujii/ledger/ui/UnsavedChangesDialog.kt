package com.liujii.ledger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

// 编辑弹窗共用退出确认，系统返回、点遮罩和取消按钮采用相同规则。
@Composable
fun UnsavedChangesDialog(show: Boolean, canSave: Boolean, onSave: () -> Unit, onDiscard: () -> Unit, onContinue: () -> Unit) {
    if (!show) return
    AlertDialog(
        onDismissRequest=onContinue,
        title={Text("修改尚未保存")},
        text={Text("保存修改后退出，或放弃这次修改。")},
        confirmButton={TextButton(onClick=onSave,enabled=canSave){Text("保存")}},
        dismissButton={Column{
            TextButton(onClick=onDiscard){Text("放弃修改",color=MaterialTheme.colorScheme.error)}
            TextButton(onClick=onContinue){Text("继续编辑")}
        }},
    )
}
