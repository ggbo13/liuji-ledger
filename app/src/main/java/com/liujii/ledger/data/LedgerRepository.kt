package com.liujii.ledger.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.io.FileOutputStream

class LedgerRepository(private val context: Context, private val dao: LedgerDao) {
    val categories = dao.categories()
    val accounts = dao.accounts()
    val paymentMethods = dao.paymentMethods()
    val managedAccounts = dao.managedAccounts()
    val managedPaymentMethods = dao.managedPaymentMethods()
    val transactions = dao.transactions()
    val transfers = dao.transfers()
    val adjustments = dao.adjustments()
    val budgets = dao.budgets()
    val recurringRules = dao.recurringRules()

    suspend fun saveTransaction(value: TransactionEntity) = dao.saveTransactionChecked(value)
    suspend fun deleteTransaction(id: Long) = dao.deleteTransaction(id)
    suspend fun restoreTransaction(row: TransactionRow) = dao.insertTransaction(row.toEntity())
    suspend fun saveAccount(value: AccountEntity) = if (value.id == 0L) dao.insertAccount(value) else { dao.updateAccount(value); value.id }
    suspend fun activeAccountCount(): Int = dao.activeAccountCount()
    suspend fun updateAccounts(values: List<AccountEntity>) = dao.updateAccounts(values)
    suspend fun savePaymentMethod(value: PaymentMethodEntity) = dao.savePaymentMethodChecked(value)
    suspend fun saveBook(id:Long,name:String,color:Long)=dao.saveBookChecked(id,name,color)
    suspend fun setBookActive(id:Long,active:Boolean)=dao.setBookActive(id,active)
    suspend fun setCategoryActive(id:Long,active:Boolean)=dao.setCategoryActive(id,active)
    suspend fun setPaymentMethodActive(id:Long,active:Boolean)=dao.setPaymentMethodActive(id,active)
    suspend fun reorderCategories(type:MoneyType,ids:List<Long>)=dao.reorderCategoriesChecked(type,ids)
    suspend fun reorderBooks(ids:List<Long>)=dao.reorderBooksChecked(ids)
    suspend fun saveTransfer(value: TransferEntity) = if (value.id == 0L) dao.insertTransfer(value) else { dao.updateTransfer(value); value.id }
    suspend fun deleteTransfer(id: Long) = dao.deleteTransfer(id)
    suspend fun saveAdjustment(value: AccountAdjustmentEntity) = dao.insertAdjustment(value)
    suspend fun deleteAdjustment(id: Long) = dao.deleteAdjustment(id)
    suspend fun saveCategory(value: CategoryEntity) = dao.saveCategoryChecked(value)
    suspend fun updateCategories(values: List<CategoryEntity>) = dao.updateCategories(values)
    suspend fun categoryUsage(id: Long) = dao.categoryUsage(id)
    suspend fun saveBudget(value: BudgetEntity) = dao.saveBudget(value)
    suspend fun deleteBudget(id: Long) = dao.deleteBudgetChecked(id)
    suspend fun saveRecurring(value: RecurringRuleEntity) = dao.saveRecurringChecked(value)
    suspend fun setRecurringActive(id:Long,active:Boolean)=dao.setRecurringActiveChecked(id,active)
    suspend fun deleteRecurring(id: Long) = dao.deleteRecurring(id)
    suspend fun postRecurringOnce(rule: RecurringRuleEntity, transaction: TransactionEntity, updated: RecurringRuleEntity) = dao.postRecurringOnce(rule, transaction, updated)
    suspend fun allRecurringRules(): List<RecurringRuleEntity> = dao.allRecurring()

    fun lastAccountId(): Long = context.getSharedPreferences("ledger", Context.MODE_PRIVATE).getLong("lastAccountId", 0)
    fun rememberAccount(id: Long) { context.getSharedPreferences("ledger", Context.MODE_PRIVATE).edit().putLong("lastAccountId", id).apply() }
    fun lastPaymentMethodId(): Long = context.getSharedPreferences("ledger", Context.MODE_PRIVATE).getLong("lastPaymentMethodId", 1)
    fun rememberPaymentMethod(id: Long) { context.getSharedPreferences("ledger", Context.MODE_PRIVATE).edit().putLong("lastPaymentMethodId", id).apply() }

    fun loadDraft(key: String): TransactionDraft? = runCatching {
        val raw=context.getSharedPreferences("drafts",Context.MODE_PRIVATE).getString(key,null) ?: return null
        val json=JSONObject(raw)
        TransactionDraft(MoneyType.valueOf(json.getString("type")),json.getString("amount"),json.getLong("category"),json.getLong("account"),json.getLong("method"),json.getString("date"),json.getString("note"),json.optBoolean("isCopy",false))
    }.getOrNull()

    fun saveDraft(key: String, draft: TransactionDraft?) {
        val edit=context.getSharedPreferences("drafts",Context.MODE_PRIVATE).edit()
        if(draft==null) edit.remove(key) else edit.putString(key,JSONObject().put("type",draft.type.name).put("amount",draft.amount).put("category",draft.categoryId).put("account",draft.accountId).put("method",draft.paymentMethodId).put("date",draft.date).put("note",draft.note).put("isCopy",draft.isCopy).toString())
        edit.apply()
    }

    fun loadAppearance(): AppearanceSettings {
        val p = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        return AppearanceSettings(
            themeMode = p.getString("themeMode", "system") ?: "system",
            backgroundType = p.getString("backgroundType", "gradient") ?: "gradient",
            colorStart = p.getLong("colorStart", 0xFFEAF1F7),
            colorEnd = p.getLong("colorEnd", 0xFFD9F1EB),
            imageUri = p.getString("imageUri", null),
            overlayAlpha = p.getFloat("overlayAlpha", .84f),
            appLockEnabled = p.getBoolean("appLockEnabled", false),
        )
    }

    fun saveAppearance(value: AppearanceSettings) {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
            .putString("themeMode", value.themeMode)
            .putString("backgroundType", value.backgroundType)
            .putLong("colorStart", value.colorStart)
            .putLong("colorEnd", value.colorEnd)
            .putString("imageUri", value.imageUri)
            .putFloat("overlayAlpha", value.overlayAlpha)
            .putBoolean("appLockEnabled", value.appLockEnabled)
            .apply()
    }

    suspend fun exportJson(output: OutputStream) {
        writeBackup(dao.snapshot(),output)
    }

    internal fun writeBackup(snapshot:BackupPayload,output:OutputStream) {
        val root = JSONObject().put("version", 3)
        root.put("accounts", JSONArray().apply { snapshot.accounts.forEach { put(JSONObject().put("id",it.id).put("name",it.name).put("opening",it.openingBalanceCents).put("active",it.active).put("type",it.type.name).put("color",it.color).put("sort",it.sortOrder).put("createdAt",it.createdAt).put("includeInTotal",it.includeInTotal)) } })
        root.put("paymentMethods", JSONArray().apply { snapshot.paymentMethods.forEach { put(JSONObject().put("id",it.id).put("name",it.name).put("color",it.color).put("sort",it.sortOrder).put("active",it.active)) } })
        root.put("categories", JSONArray().apply { snapshot.categories.forEach { put(JSONObject().put("id",it.id).put("name",it.name).put("type",it.type.name).put("icon",it.icon).put("color",it.color).put("sort",it.sortOrder).put("active",it.active)) } })
        root.put("transactions", JSONArray().apply { snapshot.transactions.forEach { put(JSONObject().put("id",it.id).put("type",it.type.name).put("amount",it.amountCents).put("categoryId",it.categoryId).put("accountId",it.accountId).put("occurredAt",it.occurredAt).put("note",it.note).put("recurringRuleId",it.recurringRuleId).put("paymentMethodId",it.paymentMethodId)) } })
        root.put("transfers", JSONArray().apply { snapshot.transfers.forEach { put(JSONObject().put("id",it.id).put("fromAccountId",it.fromAccountId).put("toAccountId",it.toAccountId).put("amount",it.amountCents).put("occurredAt",it.occurredAt).put("note",it.note)) } })
        root.put("adjustments", JSONArray().apply { snapshot.adjustments.forEach { put(JSONObject().put("id",it.id).put("accountId",it.accountId).put("delta",it.deltaCents).put("occurredAt",it.occurredAt).put("note",it.note)) } })
        root.put("budgets", JSONArray().apply { snapshot.budgets.forEach { put(JSONObject().put("id",it.id).put("month",it.yearMonth).put("categoryId",it.categoryId).put("limit",it.limitCents)) } })
        root.put("recurring", JSONArray().apply { snapshot.recurringRules.forEach { put(JSONObject().put("id",it.id).put("title",it.title).put("type",it.type.name).put("amount",it.amountCents).put("categoryId",it.categoryId).put("accountId",it.accountId).put("repeat",it.repeatType.name).put("startAt",it.startAt).put("endAt",it.endAt).put("nextAt",it.nextAt).put("days",it.remindDaysBefore).put("hour",it.remindHour).put("minute",it.remindMinute).put("active",it.active).put("paymentMethodId",it.paymentMethodId)) } })
        output.writer(Charsets.UTF_8).apply { write(root.toString(2)); flush() }
    }

    suspend fun importJson(uri: Uri) {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("无法读取备份")
        val payload = parseBackupJson(raw)
        dao.restorePreservingCurrent(payload){current->
            FileOutputStream(context.filesDir.resolve("恢复前自动备份.json")).use{writeBackup(current,it)}
        }
    }

    internal suspend fun snapshot(): BackupPayload = dao.snapshot()
    internal suspend fun restoreSnapshot(payload: BackupPayload, preserve: suspend (BackupPayload) -> Unit) = dao.restorePreservingCurrent(payload, preserve)

    // 恢复结果依赖实际落盘状态，此处需要 commit 的布尔结果。
    @android.annotation.SuppressLint("UseKtx")
    internal fun saveRestoredAppearance(value: AppearanceSettings): Boolean = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
        .putString("themeMode", value.themeMode).putString("backgroundType", value.backgroundType)
        .putLong("colorStart", value.colorStart).putLong("colorEnd", value.colorEnd)
        .putString("imageUri", value.imageUri).putFloat("overlayAlpha", value.overlayAlpha).commit()

    internal fun parseBackupJson(raw: String): BackupPayload {
        require(raw.isNotBlank()){ "备份文件为空，请重新选择" }
        val root = JSONObject(raw)
        val version = root.getInt("version")
        require(version in 1..3) { "不支持的备份版本" }
        fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
        val payload = BackupPayload(
            version = version,
            accounts = root.getJSONArray("accounts").objects().map { AccountEntity(it.getLong("id"), it.getString("name"), it.getLong("opening"), it.getBoolean("active"), AccountType.valueOf(it.optString("type", "CASH")), it.optLong("color", 0xFF4D8BB8), it.optInt("sort", 99), it.optLong("createdAt", 0), it.optBoolean("includeInTotal", true)) },
            categories = root.getJSONArray("categories").objects().map { CategoryEntity(it.getLong("id"), it.getString("name"), MoneyType.valueOf(it.getString("type")), it.getString("icon"), it.getLong("color"), it.getInt("sort"), it.getBoolean("active")) },
            transactions = root.getJSONArray("transactions").objects().map { TransactionEntity(it.getLong("id"), MoneyType.valueOf(it.getString("type")), it.getLong("amount"), it.getLong("categoryId"), it.getLong("accountId"), it.getLong("occurredAt"), it.optString("note"), if(it.isNull("recurringRuleId")) null else it.getLong("recurringRuleId"), it.optLong("paymentMethodId",1)) },
            transfers = root.optJSONArray("transfers")?.objects()?.map { TransferEntity(it.getLong("id"), it.getLong("fromAccountId"), it.getLong("toAccountId"), it.getLong("amount"), it.getLong("occurredAt"), it.optString("note")) } ?: emptyList(),
            adjustments = root.optJSONArray("adjustments")?.objects()?.map { AccountAdjustmentEntity(it.getLong("id"), it.getLong("accountId"), it.getLong("delta"), it.getLong("occurredAt"), it.optString("note", "余额校准")) } ?: emptyList(),
            budgets = root.getJSONArray("budgets").objects().map { BudgetEntity(it.getLong("id"), it.getString("month"), if(it.isNull("categoryId")) null else it.getLong("categoryId"), it.getLong("limit")) },
            recurringRules = root.getJSONArray("recurring").objects().map { RecurringRuleEntity(it.getLong("id"), it.getString("title"), MoneyType.valueOf(it.getString("type")), it.getLong("amount"), it.getLong("categoryId"), it.getLong("accountId"), RepeatType.valueOf(it.getString("repeat")), it.getLong("startAt"), if(it.isNull("endAt")) null else it.getLong("endAt"), it.getLong("nextAt"), it.getInt("days"), it.getInt("hour"), it.getInt("minute"), it.getBoolean("active"), it.optLong("paymentMethodId",1)) },
            paymentMethods = root.optJSONArray("paymentMethods")?.objects()?.map { PaymentMethodEntity(it.getLong("id"),it.getString("name"),it.getLong("color"),it.getInt("sort"),it.getBoolean("active")) } ?: defaultPaymentMethodEntities(),
        )
        validateBackup(payload)
        require(payload.accounts.isNotEmpty() && payload.categories.isNotEmpty()) { "备份缺少账户或分类" }
        val categoryIds = payload.categories.map { it.id }.toSet()
        val accountIds = payload.accounts.map { it.id }.toSet()
        val methodIds = payload.paymentMethods.map { it.id }.toSet()
        require(payload.paymentMethods.any { it.active } && payload.accounts.any { it.active }) { "备份缺少可用账本或收支方式" }
        require(methodIds.size == payload.paymentMethods.size && categoryIds.size == payload.categories.size && accountIds.size == payload.accounts.size) { "备份中有重复标识" }
        require(payload.transactions.map { it.id }.toSet().size == payload.transactions.size) { "备份中有重复账目" }
        require(payload.transfers.map { it.id }.toSet().size == payload.transfers.size) { "备份中有重复转账" }
        require(payload.recurringRules.map { it.id }.toSet().size == payload.recurringRules.size) { "备份中有重复周期规则" }
        require(payload.budgets.groupBy { it.yearMonth to it.categoryId }.values.none { it.size > 1 }) { "同一范围存在重复预算" }
        require(payload.transactions.all { it.paymentMethodId in methodIds }) { "账目引用了不存在的收支方式" }
        require(payload.recurringRules.all { it.amountCents > 0 && it.categoryId in categoryIds && it.accountId in accountIds && it.paymentMethodId in methodIds && it.nextAt >= it.startAt && (it.endAt == null || it.endAt >= it.startAt) }) { "周期账单关联数据无效" }
        require(payload.budgets.all { it.limitCents > 0 && (it.categoryId == null || it.categoryId in categoryIds) }) { "预算数据无效" }
        require(payload.transactions.all { it.amountCents > 0 && it.categoryId in categoryIds && it.accountId in accountIds }) { "备份账目数据无效" }
        require(payload.transfers.all { it.amountCents > 0 && it.fromAccountId in accountIds && it.toAccountId in accountIds && it.fromAccountId != it.toAccountId }) { "备份转账数据无效" }
        require(payload.adjustments.all { it.accountId in accountIds }) { "备份余额校准数据无效" }
        return payload
    }

    fun exportCsv(rows: List<TransactionRow>, transfers: List<TransferRow>, adjustments: List<AccountAdjustmentRow>, output: OutputStream) {
        output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        output.bufferedWriter(Charsets.UTF_8).use { w ->
            w.appendLine("日期,类型,金额,分类,账本,收支方式,备注")
            val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
            data class CsvLine(val at:Long,val type:String,val amount:Long,val category:String,val account:String,val target:String,val note:String)
            val lines = rows.map { CsvLine(it.occurredAt,if(it.type==MoneyType.INCOME)"收入" else "支出",it.amountCents,it.categoryName,it.accountName,it.paymentMethodName,it.note) }
            lines.sortedByDescending { it.at }.forEach { t ->
                val cells = listOf(f.format(java.util.Date(t.at)), t.type, java.math.BigDecimal.valueOf(t.amount,2).toPlainString(), t.category, t.account, t.target, t.note)
                w.appendLine(cells.mapIndexed { index, value -> csvCell(value, text = index != 2) }.joinToString(","))
            }
        }
    }
}

private fun TransactionRow.toEntity() = TransactionEntity(
    id=id, type=type, amountCents=amountCents, categoryId=categoryId, accountId=accountId,
    occurredAt=occurredAt, note=note, recurringRuleId=recurringRuleId, paymentMethodId=paymentMethodId,
)

private fun defaultPaymentMethodEntities() = listOf(
    PaymentMethodEntity(1,"现金",0xFF4D8BB8,0), PaymentMethodEntity(2,"支付宝",0xFF1677FF,1),
    PaymentMethodEntity(3,"微信",0xFF2FAE83,2), PaymentMethodEntity(4,"银行卡",0xFF9A78C6,3),
    PaymentMethodEntity(5,"数字人民币",0xFFE76F64,4),
)
