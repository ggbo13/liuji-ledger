package com.liujii.ledger.data

import java.io.OutputStream

data class BackupRestoreOutcome(val restored:Boolean,val message:String?=null,val appearanceRestored:Boolean=true,val remindersRestored:Boolean=true)

// 文件选择成功不代表提供方一定能打开文件；空流必须作为失败处理。
fun writeSelectedDocument(open:()->OutputStream?,write:(OutputStream)->Unit) {
    val output=open()?:error("无法打开所选文件，请换一个保存位置")
    output.use(write)
}
