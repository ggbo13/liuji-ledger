package com.liujii.ledger

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.liujii.ledger.data.*
import com.liujii.ledger.reminder.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as LiuJiApplication).repository
    private val fullBackup = FullBackupManager(application, repo)
    val preparedBackup = androidx.compose.runtime.mutableStateOf<PreparedBackup?>(null)
    val backupStatus = androidx.compose.runtime.mutableStateOf(fullBackup.status())
    fun refreshBackupStatus(hasTransactions: Boolean = transactions.value.isNotEmpty()) { backupStatus.value = fullBackup.status(hasTransactions) }
    fun setBackupReminder(enabled: Boolean) { fullBackup.setReminderEnabled(enabled); refreshBackupStatus() }
    fun latestRecoveryBackup(): Uri? = fullBackup.latestRecovery()?.let { Uri.fromFile(it) }
    fun dismissBackupPreview() { if (!fileOperationInProgress.value) preparedBackup.value = null }
    val categories = repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts = repo.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val paymentMethods = repo.paymentMethods.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val managedAccounts = repo.managedAccounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val managedPaymentMethods = repo.managedPaymentMethods.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transactions = repo.transactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transfers = repo.transfers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val adjustments = repo.adjustments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val budgets = repo.budgets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recurring = repo.recurringRules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var appearance = androidx.compose.runtime.mutableStateOf(repo.loadAppearance())
        private set

    // 调起系统文件选择器时，应用虽然进入后台，但不应立即触发应用锁。
    var externalActivityInProgress: Boolean = false
        private set

    fun beginExternalActivity() { externalActivityInProgress = true }
    fun endExternalActivity() { externalActivityInProgress = false }

    val fileOperationInProgress = androidx.compose.runtime.mutableStateOf(false)
    val operationNotice = androidx.compose.runtime.mutableStateOf<String?>(null)
    private var restoringBackup=false
    private val submissions=androidx.compose.runtime.mutableStateMapOf<String,TransactionSubmission>()
    fun transactionSubmission(sessionId:String)=submissions[sessionId]?:TransactionSubmission()
    private val pendingOperations = androidx.compose.runtime.mutableStateOf<Set<String>>(emptySet())
    fun isProcessing(key:String):Boolean=key in pendingOperations.value

    private fun managedOperation(key:String,done:(String?)->Unit,work:suspend ()->Unit) {
        if(isProcessing(key)){done("该操作正在处理，请稍候");return}
        if(restoringBackup){done("正在恢复备份，请稍后再修改数据");return}
        pendingOperations.value+=key
        viewModelScope.launch {
            try {check(!restoringBackup){"正在恢复备份，请稍后再修改数据"};work();done(null)}
            catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){done(error.message?:"操作失败，请重试")}
            finally {pendingOperations.value-=key}
        }
    }

    fun setCategoryActive(id:Long,active:Boolean,done:(String?)->Unit)=managedOperation("category-active-$id",done){repo.setCategoryActive(id,active)}
    fun setBookActive(id:Long,active:Boolean,done:(String?)->Unit)=managedOperation("book-active-$id",done){repo.setBookActive(id,active)}
    fun setPaymentMethodActive(id:Long,active:Boolean,done:(String?)->Unit)=managedOperation("method-active-$id",done){repo.setPaymentMethodActive(id,active)}

    fun saveTransaction(id: Long = 0, type: MoneyType, amount: String, categoryId: Long, accountId: Long, paymentMethodId: Long, date: LocalDate, note: String, after: (() -> Unit)? = null, onError: (String) -> Unit = {}) {
        if(restoringBackup){onError("正在恢复备份，请稍后再保存");return}
        val cents = parseMoneyCents(amount)
        if (cents == null || cents <= 0) { onError("请输入有效金额"); return }
        if(categoryId<=0 || accountId<=0 || paymentMethodId<=0) { onError("请选择分类、账本和收支方式"); return }
        val time = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val original=transactions.value.find { it.id==id }
        viewModelScope.launch {
            runCatching { repo.saveTransaction(TransactionEntity(id, type, cents, categoryId, accountId, time, note.trim(), recurringRuleId=original?.recurringRuleId,paymentMethodId=paymentMethodId)) }
                .fold({repo.rememberAccount(accountId);repo.rememberPaymentMethod(paymentMethodId);after?.invoke()}, {onError(it.message ?: "保存失败，请重试")})
        }
    }

    fun submitTransaction(sessionId:String,id:Long,type:MoneyType,amount:String,categoryId:Long,accountId:Long,paymentMethodId:Long,date:LocalDate,note:String) {
        val previous=transactionSubmission(sessionId)
        if(previous.processing||previous.saved)return
        submissions[sessionId]=TransactionSubmission(processing=true)
        saveTransaction(id,type,amount,categoryId,accountId,paymentMethodId,date,note,
            after={draftSessions.finishSubmission(sessionId);submissions[sessionId]=TransactionSubmission(saved=true)},
            onError={submissions[sessionId]=TransactionSubmission(error=it)})
    }

    fun deleteTransaction(row: TransactionRow, after: (() -> Unit)? = null, onError: (String) -> Unit = {}) = viewModelScope.launch {
        if(restoringBackup){onError("正在恢复备份，请稍后再删除");return@launch}
        runCatching{repo.deleteTransaction(row.id)}.fold({after?.invoke()},{onError(it.message?:"删除失败")})
    }

    fun restoreTransaction(row: TransactionRow, after: (() -> Unit)? = null, onError: (String) -> Unit = {}) = viewModelScope.launch {
        if(restoringBackup){onError("正在恢复备份，请稍后再撤销");return@launch}
        runCatching{repo.restoreTransaction(row)}.fold({after?.invoke()},{onError(it.message?:"撤销失败")})
    }

    fun preferredAccountId(): Long = repo.lastAccountId()
    fun preferredPaymentMethodId(): Long = repo.lastPaymentMethodId()
    fun loadDraft(key: String) = repo.loadDraft(key)
    fun saveDraft(key: String, value: TransactionDraft?) = repo.saveDraft(key,value)
    private val draftSessions=TransactionDraftSessions{key,draft->repo.saveDraft(key,draft)}
    fun beginDraftSession(key: String,identity:String?=null)=draftSessions.open(key,identity)
    suspend fun activeAccountCount(): Int = repo.activeAccountCount()

    fun saveAccount(id: Long = 0, name: String, type: AccountType, opening: String, color: Long, includeInTotal: Boolean, after: (() -> Unit)? = null) {
        if (name.isBlank()) return
        val cents = parseMoneyCents(opening) ?: return
        val existing = accounts.value.find { it.id == id }
        val order = existing?.sortOrder ?: ((accounts.value.maxOfOrNull { it.sortOrder } ?: -1) + 1)
        val created = existing?.createdAt ?: System.currentTimeMillis()
        viewModelScope.launch {
            repo.saveAccount(AccountEntity(id,name.trim(),cents,existing?.active?:true,type,color,order,created,includeInTotal))
            after?.invoke()
        }
    }

    fun saveBook(id: Long = 0, name: String, color: Long, after: (() -> Unit)? = null, onError: (String) -> Unit = {}) {
        managedOperation("book-save-$id",{error->if(error==null)after?.invoke()else onError(error)}){repo.saveBook(id,name.trim(),color)}
    }

    fun savePaymentMethod(value: PaymentMethodEntity, done: (String?) -> Unit) {
        managedOperation("method-save-${value.id}",done){repo.savePaymentMethod(value.copy(name=value.name.trim()))}
    }

    fun deactivateBook(book: AccountEntity, done: (String?) -> Unit) {
        setBookActive(book.id,false,done)
    }

    fun reorderAccounts(orderedIds: List<Long>,done:(String?)->Unit={error->operationNotice.value=error}) {
        managedOperation("book-order",done){repo.reorderBooks(orderedIds)}
    }

    fun deactivateAccount(account: AccountEntity, done: (String?) -> Unit) {
        val balance = accountBalance(account, transactions.value, transfers.value, adjustments.value)
        when {
            balance != 0L -> done("请先转出或校准余额至 0 后再停用")
            recurring.value.any { it.active && it.accountId == account.id } -> done("请先处理使用该账户的周期账单")
            else -> viewModelScope.launch { repo.saveAccount(account.copy(active=false)); done(null) }
        }
    }

    fun saveTransfer(id: Long = 0, fromAccountId: Long, toAccountId: Long, amount: String, date: LocalDate, note: String, after: (() -> Unit)? = null) {
        val cents = parseMoneyCents(amount) ?: return
        if (cents <= 0 || fromAccountId <= 0 || toAccountId <= 0 || fromAccountId == toAccountId) return
        val time = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        viewModelScope.launch { repo.saveTransfer(TransferEntity(id,fromAccountId,toAccountId,cents,time,note.trim())); repo.rememberAccount(fromAccountId); after?.invoke() }
    }

    fun deleteTransfer(id: Long,done:(String?)->Unit={error->if(error!=null)operationNotice.value=error})=managedOperation("transfer-delete-$id",done){repo.deleteTransfer(id)}

    fun calibrateAccount(account: AccountEntity, target: String, note: String, after: (() -> Unit)? = null) {
        val targetCents = parseMoneyCents(target) ?: return
        val current = accountBalance(account, transactions.value, transfers.value, adjustments.value)
        val delta = targetCents - current
        if (delta == 0L) { after?.invoke(); return }
        viewModelScope.launch { repo.saveAdjustment(AccountAdjustmentEntity(accountId=account.id,deltaCents=delta,occurredAt=System.currentTimeMillis(),note=note.trim().ifBlank{"余额校准"})); after?.invoke() }
    }

    fun saveCategory(id: Long = 0, name: String, type: MoneyType, icon: String, color: Long, sortOrder: Int = 99, active: Boolean = true, done: (String?) -> Unit = {}) {
        managedOperation("category-save-$id",done){repo.saveCategory(CategoryEntity(id,name.trim(),type,icon.ifBlank{name.take(1)},color,sortOrder,active))}
    }

    fun deactivateCategory(category: CategoryEntity,done:(String?)->Unit={error->operationNotice.value=error}) = setCategoryActive(category.id,false,done)

    fun reorderCategories(type: MoneyType, orderedIds: List<Long>,done:(String?)->Unit={error->operationNotice.value=error}) {
        managedOperation("category-order-$type",done){repo.reorderCategories(type,orderedIds)}
    }

    fun saveBudget(amount: String, categoryId: Long?, existing: BudgetEntity? = null, done: (String?) -> Unit = {}) {
        val cents = parseMoneyCents(amount)
        if(cents==null||cents<=0){done("请输入大于 0 的预算金额");return}
        managedOperation("budget-save-${existing?.id?:0}",done){repo.saveBudget(BudgetEntity(id=existing?.id?:0,yearMonth=existing?.yearMonth?:java.time.YearMonth.now().toString(),categoryId=categoryId,limitCents=cents))}
    }

    fun deleteBudget(id: Long,done:(String?)->Unit={error->operationNotice.value=error})=managedOperation("budget-delete-$id",done){repo.deleteBudget(id)}

    fun saveRecurring(id: Long = 0, title: String, type: MoneyType, amount: String, categoryId: Long, accountId: Long, paymentMethodId: Long, repeat: RepeatType, nextDate: LocalDate, days: Int, hour: Int, minute: Int = 0, endDate: LocalDate? = null, active: Boolean = true, after: (() -> Unit)? = null, onError: (String) -> Unit = {}) {
        val cents = parseMoneyCents(amount)
        if(cents==null||cents<=0||title.isBlank()){onError("请输入账单名称和大于 0 的金额");return}
        val at = nextDate.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        managedOperation("recurring-$id",{error->if(error==null)after?.invoke()else onError(error)}){
            val old = recurring.value.find { it.id == id }
            require(endDate==null||endDate>=nextDate){"结束日期不能早于入账日期"}
            val entity = RecurringRuleEntity(id=id,title=title.trim(), type=type, amountCents=cents, categoryId=categoryId, accountId=accountId, repeatType=repeat, startAt=if(old?.nextAt!=at)at else old.startAt, endAt=endDate?.plusDays(1)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()?.minus(1), nextAt=at, remindDaysBefore=days, remindHour=hour, remindMinute=minute, active=active, paymentMethodId=paymentMethodId)
            val savedId = repo.saveRecurring(entity)
            if(!ReminderScheduler.schedule(getApplication(),entity.copy(id=savedId)))operationNotice.value="周期账单已保存，但系统未接受提醒。请检查通知和后台运行设置，首页仍会显示到期账单。"
        }
    }

    fun setRecurringActive(rule: RecurringRuleEntity, active: Boolean, onError: (String) -> Unit = {}) {
        managedOperation("recurring-${rule.id}",{error->error?.let(onError)}){
            val updated=repo.setRecurringActive(rule.id,active)
            if(!ReminderScheduler.schedule(getApplication(),updated))operationNotice.value="账单状态已更新，但系统未接受提醒调整。请检查后台运行设置。"
        }
    }

    fun deleteRecurring(rule: RecurringRuleEntity, done: (String?) -> Unit = {}) {
        managedOperation("recurring-${rule.id}",done){
            repo.deleteRecurring(rule.id)
            if(!ReminderScheduler.cancel(getApplication(),rule.id))operationNotice.value="周期账单已删除，但系统未确认取消提醒。请检查通知设置。"
        }
    }

    val postingRuleIds = androidx.compose.runtime.mutableStateOf<Set<Long>>(emptySet())
    fun postRecurring(rule: RecurringRuleEntity, onError:(String)->Unit={},done: (Boolean) -> Unit = {}) {
        if(restoringBackup){onError("正在恢复备份，请稍后再入账");return}
        if(rule.id in postingRuleIds.value) return
        postingRuleIds.value += rule.id
        viewModelScope.launch {
            try {
            val date = java.time.Instant.ofEpochMilli(rule.nextAt).atZone(ZoneId.systemDefault()).toLocalDate()
            val anchor=java.time.Instant.ofEpochMilli(rule.startAt).atZone(ZoneId.systemDefault()).toLocalDate()
            val next = if(rule.repeatType==RepeatType.MONTHLY) date.plusMonths(1).withDayOfMonth(anchor.dayOfMonth.coerceAtMost(date.plusMonths(1).lengthOfMonth())) else nextOccurrence(date, rule.repeatType)
            val nextMillis = next.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val updated = rule.copy(nextAt=nextMillis, active=rule.endAt?.let { nextMillis <= it } ?: true)
            val inserted = repo.postRecurringOnce(rule, TransactionEntity(type=rule.type, amountCents=rule.amountCents, categoryId=rule.categoryId, accountId=rule.accountId, occurredAt=rule.nextAt, note=rule.title, recurringRuleId=rule.id, paymentMethodId=rule.paymentMethodId), updated)
            repo.allRecurringRules().find{it.id==rule.id}?.let{if(!ReminderScheduler.schedule(getApplication(),it))operationNotice.value="账目已入账，但下次提醒未能调度。首页仍会显示待入账账单。"}
            done(inserted)
            } catch(cancelled:CancellationException){throw cancelled} catch(error:Exception) { onError(error.message?:"入账失败，请重试") } finally { postingRuleIds.value -= rule.id }
        }
    }

    fun updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings) {
        val updated=transform(appearance.value)
        repo.saveAppearance(updated)
        appearance.value=updated
    }

    fun exportJson(output: OutputStream, done: (String?) -> Unit) = viewModelScope.launch { runCatching { withContext(Dispatchers.IO){ repo.exportJson(output) } }.fold({done(null)}, {done(it.message)}) }
    fun importJson(uri:Uri,done:(String?)->Unit)=restoreBackup(uri){done(it.message)}
    fun prepareBackup(uri: Uri, done: (String?) -> Unit) {
        if(fileOperationInProgress.value){done("另一个文件操作正在进行，请稍候");return}
        fileOperationInProgress.value=true
        preparedBackup.value=null
        viewModelScope.launch {
            var failure:String?=null
            try { preparedBackup.value=withContext(Dispatchers.IO){fullBackup.prepare(uri)} }
            catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){failure="无法预览备份，当前数据未改变：${error.message?:"请检查文件"}"}
            finally {fileOperationInProgress.value=false;endExternalActivity()}
            done(failure)
        }
    }

    fun restoreBackup(uri: Uri, done: (BackupRestoreOutcome) -> Unit) {
        prepareBackup(uri){error->if(error!=null)done(BackupRestoreOutcome(false,error))else restorePreparedBackup(done)}
    }

    fun restorePreparedBackup(done: (BackupRestoreOutcome) -> Unit) = viewModelScope.launch {
        if(fileOperationInProgress.value){done(BackupRestoreOutcome(false,"另一个文件操作正在进行，请稍候"));return@launch}
        val prepared=preparedBackup.value?:run{done(BackupRestoreOutcome(false,"请重新选择备份并查看预览"));return@launch}
        if(pendingOperations.value.isNotEmpty()||submissions.values.any{it.processing}||postingRuleIds.value.isNotEmpty()){
            done(BackupRestoreOutcome(false,"请等待当前保存操作完成后再恢复"));return@launch
        }
        fileOperationInProgress.value=true
        restoringBackup=true
        var restored=false
        try {
            val oldRules=repo.allRecurringRules()
            val result=withContext(Dispatchers.IO){fullBackup.restore(prepared)}
            restored=result.restored
            appearance.value=repo.loadAppearance()
            preparedBackup.value=null
            var remindersRestored=true
            oldRules.forEach{if(!ReminderScheduler.cancel(getApplication(),it.id))remindersRestored=false}
            repo.allRecurringRules().filter{it.isEffective()}.forEach{if(!ReminderScheduler.schedule(getApplication(),it))remindersRestored=false}
            val messages=listOfNotNull(result.message,if(remindersRestored)null else "账目已恢复，但部分提醒未能恢复。请检查通知和后台运行设置。")
            done(result.copy(message=messages.joinToString("\n").takeIf{it.isNotBlank()},remindersRestored=remindersRestored))
        } catch(cancelled:CancellationException){throw cancelled}
        catch(error:Exception){done(BackupRestoreOutcome(restored,if(restored)"备份恢复成功，但提醒恢复失败：${error.message?:"请检查系统设置"}" else "备份恢复失败，当前数据未改变：${error.message?:"请检查文件"}"))}
        finally{restoringBackup=false;fileOperationInProgress.value=false;refreshBackupStatus();endExternalActivity()}
    }
    fun exportCsv(output: OutputStream, rows: List<TransactionRow>, done: (String?) -> Unit) = viewModelScope.launch { runCatching { withContext(Dispatchers.IO){repo.exportCsv(rows, transfers.value, adjustments.value, output)} }.fold({done(null)}, {done(it.message)}) }

    private fun writeDocument(uri:Uri,done:(String?)->Unit,write:(OutputStream)->Unit) {
        if(fileOperationInProgress.value){done("另一个文件操作正在进行，请稍候");endExternalActivity();return}
        fileOperationInProgress.value=true
        viewModelScope.launch {
            try {withContext(Dispatchers.IO){writeSelectedDocument({getApplication<Application>().contentResolver.openOutputStream(uri,"wt")},write)};done(null)}
            catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){done(error.message?:"无法写入所选文件，请换一个保存位置")}
            finally{fileOperationInProgress.value=false;endExternalActivity()}
        }
    }

    fun exportCsvToUri(uri:Uri,rows:List<TransactionRow>,done:(String?)->Unit) {
        val selected=rows.toList()
        writeDocument(uri,done){output->repo.exportCsv(selected,emptyList(),emptyList(),output)}
    }

    fun exportFilteredCsvToUri(uri:Uri,filter:CsvTransactionFilter,done:(String?,Int)->Unit) {
        if(fileOperationInProgress.value){done("另一个文件操作正在进行，请稍候",0);endExternalActivity();return}
        fileOperationInProgress.value=true
        viewModelScope.launch {
            try {
                val count=withContext(Dispatchers.IO){
                    val rows=filter.select(repo.transactions.first())
                    writeSelectedDocument({getApplication<Application>().contentResolver.openOutputStream(uri,"wt")}){repo.exportCsv(rows,emptyList(),emptyList(),it)}
                    rows.size
                }
                done(null,count)
            }catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){done("CSV 导出失败：${error.message?:"无法写入所选文件"}",0)}
            finally{fileOperationInProgress.value=false;endExternalActivity()}
        }
    }

    fun exportJsonToUri(uri:Uri,done:(String?)->Unit) {
        if(fileOperationInProgress.value){done("另一个文件操作正在进行，请稍候");endExternalActivity();return}
        fileOperationInProgress.value=true
        viewModelScope.launch {
            try {withContext(Dispatchers.IO){val output=getApplication<Application>().contentResolver.openOutputStream(uri,"wt")?:error("无法打开所选文件，请换一个保存位置");output.use{repo.exportJson(it)}};done(null)}
            catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){done(error.message?:"备份导出失败，请换一个保存位置")}
            finally{fileOperationInProgress.value=false;endExternalActivity()}
        }
    }

    fun exportFullBackupToUri(uri:Uri,withoutImage:Boolean=false,done:(String?,Boolean)->Unit) {
        if(fileOperationInProgress.value){done("另一个文件操作正在进行，请稍候",false);endExternalActivity();return}
        fileOperationInProgress.value=true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO){
                    val output=getApplication<Application>().contentResolver.openOutputStream(uri,"wt")?:error("无法打开所选文件")
                    output.use{fullBackup.export(it,withoutImage)}
                    fullBackup.markExportSuccessful()
                }
                done(null,false)
            }catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){done(error.message?:"备份导出失败，请换一个保存位置",error is BackgroundUnavailableException)}
            finally{fileOperationInProgress.value=false;refreshBackupStatus();endExternalActivity()}
        }
    }

    fun selectBackgroundImage(uri:Uri,persistPermission:Boolean=true,done:(String?)->Unit) {
        if(fileOperationInProgress.value){done("另一个文件操作正在进行，请稍候");endExternalActivity();return}
        fileOperationInProgress.value=true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO){
                    val resolver=getApplication<Application>().contentResolver
                    val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
                    val input=resolver.openInputStream(uri)?:error("无法读取所选图片，请重新选择")
                    input.use{android.graphics.BitmapFactory.decodeStream(it,null,bounds)}
                    require(bounds.outWidth>0&&bounds.outHeight>0){"所选文件不是可读取的图片"}
                    BackgroundImages.validate(getApplication(),uri)
                    if(persistPermission)resolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                updateAppearance{it.copy(backgroundType="image",imageUri=uri.toString(),useThemeBackground=false)}
                done(null)
            }catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){done(error.message?:"无法使用所选图片，原背景已保留")}
            finally{fileOperationInProgress.value=false;endExternalActivity()}
        }
    }
}

fun monthRange(date: LocalDate = LocalDate.now()): LongRange {
    val zone = ZoneId.systemDefault()
    val start = date.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    return start..end
}
