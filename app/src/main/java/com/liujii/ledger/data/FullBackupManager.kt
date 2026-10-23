package com.liujii.ledger.data

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.net.Uri
import com.liujii.ledger.BuildConfig
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.core.content.edit
import androidx.core.net.toUri

data class BackupPreview(
    val format: String,
    val exportedAt: Long?,
    val transactionCount: Int,
    val dateRange: String,
    val hasAppearance: Boolean,
    val backgroundUri: String?,
)

class PreparedBackup internal constructor(
    internal val payload: BackupPayload,
    internal val appearance: AppearanceSettings?,
    internal val image: File?,
    internal val imageHash: String?,
    val preview: BackupPreview,
) {
    internal var consumed = false
}

data class BackupStatus(val lastExportAt: Long = 0, val remindEnabled: Boolean = true, val due: Boolean = false, val interrupted: Boolean = false)
class BackgroundUnavailableException : IllegalStateException("当前背景图片无法读取。可重新选择图片，或选择不包含图片继续导出。")

// 新归档与旧业务 JSON 分层，数据库和历史备份的版本保持不变。
class FullBackupManager(private val context: Context, private val repo: LedgerRepository) {
    private val restoreMutex = Mutex()
    private val prefs get() = context.getSharedPreferences("backup_status", Context.MODE_PRIVATE)
    private val recoveryDirectory get() = context.filesDir.resolve("backup-recovery").apply { mkdirs() }

    fun status(hasTransactions: Boolean = false, now: Long = System.currentTimeMillis()): BackupStatus {
        var first = prefs.getLong("firstDataAt", 0)
        if (hasTransactions && first == 0L) {
            first = now
            prefs.edit { putLong("firstDataAt", first) }
        }
        val last = prefs.getLong("lastExportAt", 0)
        val enabled = prefs.getBoolean("remindEnabled", true)
        return BackupStatus(last, enabled, backupReminderDue(hasTransactions,enabled,first,last,now), prefs.contains("pendingRecovery"))
    }

    fun setReminderEnabled(enabled: Boolean) { prefs.edit { putBoolean("remindEnabled", enabled) } }
    fun recoveryFiles(): List<File> = (recoveryDirectory.listFiles()?.filter { it.extension == "zip" } ?: emptyList()).sortedByDescending { it.lastModified() }
    fun latestRecovery(): File? = prefs.getString("latestRecovery", null)?.let(::File)?.takeIf { it.isFile }
        ?: context.filesDir.resolve("恢复前自动备份.json").takeIf { it.isFile }
    fun markExportSuccessful() { prefs.edit { putLong("lastExportAt", System.currentTimeMillis()) } }

    // 保护点必须在替换数据库前同步落盘并检查结果，不能改成异步 apply。
    @SuppressLint("UseKtx")
    private fun markRestorePending(path: String): Boolean = prefs.edit().putString("latestRecovery",path).putString("pendingRecovery",path).commit()
    @SuppressLint("UseKtx")
    private fun clearRestorePending(): Boolean = prefs.edit().remove("pendingRecovery").commit()

    suspend fun export(output: OutputStream, withoutImage: Boolean = false) {
        val stage = newStage()
        val currentAppearance = repo.loadAppearance()
        val image = collectBackground(currentAppearance, stage, withoutImage)
        val appearance = portableAppearance(currentAppearance, image)
        writeArchive(repo.snapshot(), appearance, image, output, stage)
    }

    fun prepare(uri: Uri): PreparedBackup {
        val stage = newStage()
        val input = context.contentResolver.openInputStream(uri) ?: error("无法读取备份，请重新选择")
        BufferedInputStream(input).use { source ->
            source.mark(4)
            val header = ByteArray(4)
            val count = source.read(header)
            source.reset()
            require(count > 0) { "备份文件为空，请重新选择" }
            val zip = count == 4 && header.contentEquals(byteArrayOf(80, 75, 3, 4))
            if (!zip) {
                val ledger = stage.resolve("ledger.json")
                ledger.outputStream().use { copyBackupEntry(source, it, BACKUP_ENTRY_LIMIT) }
                val payload = repo.parseBackupJson(ledger.readText(Charsets.UTF_8))
                return prepared(payload, null, null, null, "JSON v${payload.version}", null)
            }
            val files = linkedMapOf<String, File>()
            var total = 0L
            ZipInputStream(source).use { archive ->
                while (true) {
                    val entry = archive.nextEntry ?: break
                    validateArchiveEntry(entry.name, entry.isDirectory, files.keys)
                    val file = stage.resolve(entry.name)
                    val limit = if (entry.name == "manifest.json") 64L * 1024 else BACKUP_ENTRY_LIMIT
                    file.outputStream().use { total += copyBackupEntry(archive, it, minOf(limit, BACKUP_TOTAL_LIMIT - total)) }
                    files[entry.name] = file
                    archive.closeEntry()
                }
            }
            val manifest = JSONObject((files["manifest.json"] ?: error("备份缺少清单")).readText(Charsets.UTF_8))
            require(manifest.getString("format") == "liuji-backup" && manifest.getInt("version") == 1) { "不支持的完整备份版本" }
            val entries = manifest.getJSONObject("entries")
            val names = entries.keys().asSequence().toSet()
            require(names == files.keys - "manifest.json" && "ledger.json" in names) { "备份清单与内容不一致" }
            names.forEach { name ->
                val file = files.getValue(name)
                val expected = entries.getJSONObject(name)
                require(file.length() == expected.getLong("size") && sha(file) == expected.getString("sha256")) { "备份校验失败，文件可能已损坏" }
            }
            val payload = repo.parseBackupJson(files.getValue("ledger.json").readText(Charsets.UTF_8))
            val image = files["background.bin"]
            image?.let(::validateImage)
            val appearance = decodeAppearance(manifest.getJSONObject("appearance"), image)
            val exportedAt = manifest.getLong("exportedAt")
            require(exportedAt > 0) { "备份时间无效" }
            return prepared(payload, appearance, image, image?.let(::sha), "完整备份 v1 · JSON v${payload.version}", exportedAt)
        }
    }

    suspend fun restore(prepared: PreparedBackup): BackupRestoreOutcome = restoreMutex.withLock { restoreVerified(prepared) }

    private suspend fun restoreVerified(prepared: PreparedBackup): BackupRestoreOutcome {
        check(!prepared.consumed) { "这份备份已经恢复，请重新选择" }
        prepared.image?.let { require(sha(it) == prepared.imageHash) { "预览后的图片发生变化，请重新选择备份" } }
        val oldAppearance = repo.loadAppearance()
        var dataRestored = false
        var appearanceRestored = prepared.appearance == null
        try {
            // 使用独立文件名保存保护点，不覆盖历史保护文件，也不切换当前背景。
            val stage = newStage()
            val image = collectBackground(oldAppearance, stage, false)
            val copiedImage = prepared.image?.let { source ->
                context.filesDir.resolve("restored-backgrounds").apply { mkdirs() }
                    .resolve("${UUID.randomUUID()}.bin").also { target ->
                        FileOutputStream(target).use { output -> source.inputStream().use { copyBackupEntry(it, output, BACKUP_ENTRY_LIMIT) };output.fd.sync() }
                        validateImage(target)
                    }
            }
            repo.restoreSnapshot(prepared.payload) { current ->
                val recovery = recoveryDirectory.resolve("流迹保护备份-${System.currentTimeMillis()}-${UUID.randomUUID()}.zip")
                FileOutputStream(recovery).use { writeArchive(current, portableAppearance(oldAppearance, image), image, it, stage);it.fd.sync() }
                require(markRestorePending(recovery.absolutePath)) { "无法保存恢复保护状态，当前账目未改变" }
            }
            dataRestored = true
            prepared.consumed = true
            prepared.appearance?.let { value ->
                appearanceRestored = repo.saveRestoredAppearance(value.copy(imageUri = copiedImage?.let { Uri.fromFile(it).toString() }, appLockEnabled = oldAppearance.appLockEnabled))
            }
            val markerSaved = !appearanceRestored || clearRestorePending()
            val warning = if(!appearanceRestored) "账目已恢复，但外观保存失败。原保护备份仍可恢复，请检查存储空间。" else if(!markerSaved) "账目和外观已恢复，但完成标记未能保存，保护备份仍保留。" else null
            return BackupRestoreOutcome(true, warning, appearanceRestored)
        } catch (error: Exception) {
            if (!dataRestored) {
                clearRestorePending()
                throw error
            }
            return BackupRestoreOutcome(true, "账目已恢复，但外观恢复失败：${error.message ?: "请检查存储空间"}。保护备份已保留。", appearanceRestored = false)
        }
    }

    private fun newStage(): File = context.cacheDir.resolve("backup-stage-${UUID.randomUUID()}").apply { check(mkdirs()) { "无法准备备份空间" } }
    private fun sha(file: File) = file.inputStream().use(::backupSha256)

    private fun collectBackground(appearance: AppearanceSettings, stage: File, withoutImage: Boolean): File? {
        if (appearance.backgroundType != "image" || withoutImage) return null
        return try {
            val uri = appearance.imageUri?.toUri() ?: throw BackgroundUnavailableException()
            val input = context.contentResolver.openInputStream(uri) ?: throw BackgroundUnavailableException()
            stage.resolve("background.bin").also { file ->
                file.outputStream().use { output -> input.use { copyBackupEntry(it, output, BACKUP_ENTRY_LIMIT) } }
                validateImage(file)
            }
        } catch (error: Exception) {
            if (error is BackgroundUnavailableException) throw error
            throw BackgroundUnavailableException()
        }
    }

    private fun validateImage(file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "备份中的背景不是可读取的图片" }
        var sample=1
        while(bounds.outWidth/sample>1024||bounds.outHeight/sample>1024)sample*=2
        val decoded=BitmapFactory.decodeFile(file.absolutePath,BitmapFactory.Options().apply{inSampleSize=sample})
            ?:error("备份中的背景图片内容已损坏")
        decoded.recycle()
    }

    private fun portableAppearance(value: AppearanceSettings, image: File?) = value.copy(
        backgroundType = if (value.backgroundType == "image" && image == null) "gradient" else value.backgroundType,
        imageUri = null, appLockEnabled = false,
    )

    private fun encodeAppearance(value: AppearanceSettings) = JSONObject()
        .put("themeMode", value.themeMode).put("backgroundType", value.backgroundType)
        .put("colorStart", value.colorStart).put("colorEnd", value.colorEnd).put("overlayAlpha", value.overlayAlpha.toDouble())
        .put("themeId",themePreset(value.themeId).id).put("useThemeBackground",value.useThemeBackground)
        .put("motionEnabled",value.motionEnabled)

    private fun decodeAppearance(value: JSONObject, image: File?): AppearanceSettings {
        val theme = value.getString("themeMode")
        val background = value.getString("backgroundType")
        val alpha = value.getDouble("overlayAlpha").toFloat()
        val start = value.getLong("colorStart")
        val end = value.getLong("colorEnd")
        require(theme in setOf("system", "light", "dark") && background in setOf("gradient", "image")) { "备份外观类型无效" }
        require(alpha.isFinite() && alpha in .45f..1f && start in 0..0xFFFFFFFFL && end in 0..0xFFFFFFFFL) { "备份颜色或遮罩数值无效" }
        require((background == "image") == (image != null)) { "备份背景图片与外观设置不一致" }
        return AppearanceSettings(theme, background, start, end, image?.let { Uri.fromFile(it).toString() }, alpha,
            themeId=themePreset(value.optString("themeId","mint")).id,
            useThemeBackground=value.optBoolean("useThemeBackground",false),motionEnabled=value.optBoolean("motionEnabled",true))
    }

    private fun writeArchive(payload: BackupPayload, appearance: AppearanceSettings, image: File?, output: OutputStream, stage: File) {
        val ledger = stage.resolve("ledger-${UUID.randomUUID()}.json")
        ledger.outputStream().use { repo.writeBackup(payload, BackupLimitedOutput(it, BACKUP_ENTRY_LIMIT)) }
        val contents = linkedMapOf("ledger.json" to ledger)
        image?.let { contents["background.bin"] = it }
        val entries = JSONObject()
        contents.forEach { (name, file) -> entries.put(name, JSONObject().put("size", file.length()).put("sha256", sha(file))) }
        val manifest = JSONObject().put("format", "liuji-backup").put("version", 1).put("exportedAt", System.currentTimeMillis())
            .put("appVersion", BuildConfig.VERSION_NAME).put("appearance", encodeAppearance(appearance)).put("entries", entries).toString(2).toByteArray(Charsets.UTF_8)
        require(contents.values.sumOf { it.length() } + manifest.size <= BACKUP_TOTAL_LIMIT) { "完整备份超过128MiB限制" }
        ZipOutputStream(BackupNonClosingOutput(output)).use { archive ->
            archive.putNextEntry(ZipEntry("manifest.json")); archive.write(manifest); archive.closeEntry()
            contents.forEach { (name, file) ->
                archive.putNextEntry(ZipEntry(name))
                file.inputStream().use { copyBackupEntry(it, archive, BACKUP_ENTRY_LIMIT) }
                archive.closeEntry()
            }
        }
    }

    private fun prepared(payload: BackupPayload, appearance: AppearanceSettings?, image: File?, hash: String?, format: String, at: Long?): PreparedBackup {
        val dates = payload.transactions.map { Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate() }
        val range = if (dates.isEmpty()) "暂无账目" else "${dates.minOrNull()} 至 ${dates.maxOrNull()}"
        return PreparedBackup(payload, appearance, image, hash, BackupPreview(format, at, payload.transactions.size, range, appearance != null, image?.let { Uri.fromFile(it).toString() }))
    }
}
