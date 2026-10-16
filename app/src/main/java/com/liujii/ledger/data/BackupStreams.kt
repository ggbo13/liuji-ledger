package com.liujii.ledger.data

import java.io.InputStream
import java.io.OutputStream
import java.io.FilterOutputStream
import java.security.MessageDigest

internal const val BACKUP_ENTRY_LIMIT = 64L * 1024 * 1024
internal const val BACKUP_TOTAL_LIMIT = 128L * 1024 * 1024
internal val BACKUP_ENTRY_NAMES = setOf("manifest.json", "ledger.json", "background.bin")

fun backupReminderDue(hasTransactions: Boolean, enabled: Boolean, firstDataAt: Long, lastExportAt: Long, now: Long): Boolean {
    val baseline = if(lastExportAt>0)lastExportAt else firstDataAt
    return hasTransactions && enabled && baseline>0 && now-baseline>=7L*24*60*60*1000
}

// 引号只解决 CSV 分隔符，文本公式前缀需要额外转为文本。
fun csvCell(value: String, text: Boolean = true): String {
    val candidate = value.trimStart { it.isWhitespace() || it == '\uFEFF' }
    val dangerous = candidate.firstOrNull() in listOf('=', '+', '-', '@', '＝', '＋', '－', '＠') ||
        value.firstOrNull() in listOf('\t', '\r', '\n')
    val safe = if (text && dangerous) "'$value" else value
    return "\"${safe.replace("\"", "\"\"")}\""
}

internal fun copyBackupEntry(input: InputStream, output: OutputStream, limit: Long): Long {
    val buffer = ByteArray(8192)
    var copied = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        copied += count
        require(copied <= limit) { "备份文件超过允许大小，请选择较小的备份或背景图片" }
        output.write(buffer, 0, count)
    }
    return copied
}

internal fun backupSha256(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
}

internal fun validateArchiveEntry(name: String, directory: Boolean, seen: Set<String>) {
    require(!directory && name in BACKUP_ENTRY_NAMES && name !in seen) { "备份包含未知、重复或不安全的文件" }
}

internal class BackupLimitedOutput(private val output: OutputStream, private val limit: Long) : OutputStream() {
    private var written = 0L
    override fun write(value: Int) { checkSize(1); output.write(value) }
    override fun write(bytes: ByteArray, offset: Int, length: Int) { checkSize(length); output.write(bytes, offset, length) }
    private fun checkSize(count: Int) { written += count; require(written <= limit) { "备份内容超过允许大小" } }
    override fun flush() = output.flush()
}

// ZIP 关闭自身压缩器，但系统提供的流只由最外层关闭一次。
internal class BackupNonClosingOutput(output: OutputStream) : FilterOutputStream(output) {
    override fun close() = flush()
    override fun write(bytes: ByteArray, offset: Int, length: Int) = out.write(bytes, offset, length)
}
