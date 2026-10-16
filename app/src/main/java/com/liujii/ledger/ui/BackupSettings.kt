package com.liujii.ledger.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.liujii.ledger.LedgerViewModel
import com.liujii.ledger.data.BackupPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri

private fun backupTime(at: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(at))

@Composable
fun FullBackupSettings(vm: LedgerViewModel) {
    val rows by vm.transactions.collectAsState()
    val today = rememberCurrentDate()
    val status by vm.backupStatus
    val busy by vm.fileOperationInProgress
    var notice by remember { mutableStateOf<String?>(null) }
    var imageUnavailable by remember { mutableStateOf(false) }
    var withoutImage by rememberSaveable { mutableStateOf(false) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(rows.isNotEmpty(), today) { vm.refreshBackupStatus(rows.isNotEmpty()) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        try {
            uri?.let { vm.exportFullBackupToUri(it, withoutImage) { error, missingImage ->
                imageUnavailable = missingImage
                if (!missingImage) notice = error ?: if(withoutImage)"备份已导出，未包含背景图片" else "完整备份已导出"
            } }
        } finally { vm.endExternalActivity() }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        try { uri?.let { restoreError=null; vm.prepareBackup(it) { error -> if(error!=null)notice=error } } }
        finally { vm.endExternalActivity() }
    }
    fun launch(action: () -> Unit) {
        if(busy)return
        vm.beginExternalActivity()
        try { action() } catch(error:Exception) { vm.endExternalActivity();notice="无法打开系统文件选择器：${error.message?:"请重试"}" }
    }
    fun export(noImage: Boolean) {
        withoutImage=noImage
        launch { create.launch("流迹完整备份-$today.zip") }
    }
    Column(Modifier.fillMaxWidth()) {
        Text("完整备份",style=MaterialTheme.typography.titleSmall,modifier=Modifier.padding(top=12.dp))
        Text("包含账目、外观和背景图片。文件未经加密，请勿分享给他人。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=6.dp))
        Text(if(status.lastExportAt>0)"上次成功备份：${backupTime(status.lastExportAt)}" else "尚未导出过完整备份",style=MaterialTheme.typography.bodySmall)
        if(status.due)Text("已超过7天未备份，建议现在保存一份。",color=MaterialTheme.colorScheme.tertiary,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=6.dp))
        if(status.interrupted)Text("上次恢复可能被中断，请检查账目和外观。需要时可恢复保护备份。",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=6.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end=12.dp)){Text("每7天提醒备份");Text("仅在应用内提示，不自动导出",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Switch(status.remindEnabled,{vm.setBackupReminder(it)},enabled=!busy)
        }
        DialogAction("导出完整备份",onClick={export(false)},enabled=!busy,modifier=Modifier.fillMaxWidth())
        DialogAction("预览并恢复备份",onClick={launch{open.launch(arrayOf("application/zip","application/x-zip-compressed","application/json","text/plain","application/octet-stream"))}},enabled=!busy,modifier=Modifier.fillMaxWidth())
        vm.latestRecoveryBackup()?.let { uri ->
            DialogAction("预览最近的保护备份",onClick={restoreError=null;vm.prepareBackup(uri){error->if(error!=null)notice=error}},enabled=!busy,modifier=Modifier.fillMaxWidth())
        }
    }
    vm.preparedBackup.value?.let { prepared ->
        BackupPreviewDialog(prepared.preview,busy,restoreError,
            onConfirm={restoreError=null;vm.restorePreparedBackup{result->if(result.restored)notice=result.message?:"备份恢复成功" else restoreError=result.message}},
            onDismiss={vm.dismissBackupPreview()})
    }
    if(imageUnavailable)AlertDialog(onDismissRequest={imageUnavailable=false},title={Text("背景图片无法读取")},text={Text("导出未完成。请返回外观重新选择图片，或继续导出账目和外观参数；后者将使用渐变背景。")},
        confirmButton={DialogAction("不含图片继续",onClick={imageUnavailable=false;export(true)})},
        dismissButton={DialogAction("返回检查图片",onClick={imageUnavailable=false})})
    notice?.let { message -> AlertDialog(onDismissRequest={notice=null},title={Text("备份提示")},text={Text(message)},confirmButton={DialogAction("知道了",onClick={notice=null})}) }
}

@Composable
internal fun BackupPreviewDialog(preview: BackupPreview, busy: Boolean, error: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("确认恢复这份备份？")},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(preview.format)
            Text("备份时间：${preview.exportedAt?.let(::backupTime)?:"旧备份未记录"}")
            Text("账目：${preview.transactionCount} 笔")
            Text(preview.dateRange)
            Text(if(preview.hasAppearance)"包含外观设置" else "不含外观设置，保留当前外观")
            preview.backgroundUri?.let { BackupImagePreview(it) }
            Text(if(preview.backgroundUri!=null)"包含私人背景图片" else "不包含背景图片",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("确认后将替换当前账目。会先保存完整保护备份，失败时不会替换账目。",style=MaterialTheme.typography.bodySmall)
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }
    },confirmButton={DialogAction(if(busy)"恢复中…"else"确认恢复",onClick=onConfirm,enabled=!busy)},dismissButton={DialogAction("取消",onClick=onDismiss,enabled=!busy)})
}

@Composable
private fun BackupImagePreview(uri: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(null,uri) {
        value=withContext(Dispatchers.IO){
            val path=uri.toUri().path
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            BitmapFactory.decodeFile(path,bounds)
            var sample=1
            while(bounds.outWidth/sample>768||bounds.outHeight/sample>768)sample*=2
            BitmapFactory.decodeFile(path,BitmapFactory.Options().apply{inSampleSize=sample})
        }
    }
    bitmap?.let{Image(it.asImageBitmap(),"备份中的背景预览",Modifier.fillMaxWidth().height(140.dp),contentScale=ContentScale.Crop)}
}
