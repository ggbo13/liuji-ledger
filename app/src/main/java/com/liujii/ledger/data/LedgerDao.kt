package com.liujii.ledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM categories ORDER BY active DESC, sortOrder, id")
    fun categories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM accounts WHERE active = 1 ORDER BY sortOrder, id")
    fun accounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM payment_methods WHERE active = 1 ORDER BY sortOrder, id")
    fun paymentMethods(): Flow<List<PaymentMethodEntity>>

    @Query("SELECT * FROM accounts ORDER BY active DESC, sortOrder, id")
    fun managedAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM payment_methods ORDER BY active DESC, sortOrder, id")
    fun managedPaymentMethods(): Flow<List<PaymentMethodEntity>>

    @Query("SELECT * FROM categories WHERE id=:id") suspend fun categoryById(id:Long):CategoryEntity?
    @Query("SELECT * FROM accounts WHERE id=:id") suspend fun accountById(id:Long):AccountEntity?
    @Query("SELECT * FROM payment_methods WHERE id=:id") suspend fun paymentMethodById(id:Long):PaymentMethodEntity?
    @Query("SELECT * FROM transactions WHERE id=:id") suspend fun transactionById(id:Long):TransactionEntity?
    @Query("SELECT * FROM budgets WHERE id=:id") suspend fun budgetById(id:Long):BudgetEntity?

    @Query("SELECT COUNT(*) FROM accounts WHERE active = 1")
    suspend fun activeAccountCount(): Int

    @Query("SELECT t.*, f.name fromAccountName, a.name toAccountName FROM transfers t JOIN accounts f ON f.id=t.fromAccountId JOIN accounts a ON a.id=t.toAccountId ORDER BY t.occurredAt DESC, t.id DESC")
    fun transfers(): Flow<List<TransferRow>>

    @Query("SELECT x.*, a.name accountName FROM account_adjustments x JOIN accounts a ON a.id=x.accountId ORDER BY x.occurredAt DESC, x.id DESC")
    fun adjustments(): Flow<List<AccountAdjustmentRow>>

    @Query("SELECT t.*, c.name categoryName, c.icon categoryIcon, c.color categoryColor, a.name accountName, p.name paymentMethodName, p.color paymentMethodColor FROM transactions t JOIN categories c ON c.id=t.categoryId JOIN accounts a ON a.id=t.accountId JOIN payment_methods p ON p.id=t.paymentMethodId ORDER BY t.occurredAt DESC, t.id DESC")
    fun transactions(): Flow<List<TransactionRow>>

    @Query("SELECT * FROM budgets ORDER BY yearMonth DESC, categoryId")
    fun budgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM recurring_rules ORDER BY active DESC, nextAt")
    fun recurringRules(): Flow<List<RecurringRuleEntity>>

    @Insert suspend fun insertCategory(value: CategoryEntity): Long
    @Insert suspend fun insertAccount(value: AccountEntity): Long
    @Insert suspend fun insertPaymentMethod(value: PaymentMethodEntity): Long
    @Insert suspend fun insertTransfer(value: TransferEntity): Long
    @Insert suspend fun insertAdjustment(value: AccountAdjustmentEntity): Long
    @Insert suspend fun insertTransaction(value: TransactionEntity): Long
    @Insert suspend fun insertBudget(value: BudgetEntity): Long
    @Insert suspend fun insertRecurring(value: RecurringRuleEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategories(values: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAccounts(values: List<AccountEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPaymentMethods(values: List<PaymentMethodEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTransfers(values: List<TransferEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAdjustments(values: List<AccountAdjustmentEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTransactions(values: List<TransactionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBudgets(values: List<BudgetEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRecurringRules(values: List<RecurringRuleEntity>)

    @Update suspend fun updateCategory(value: CategoryEntity)
    @Update suspend fun updateCategories(values: List<CategoryEntity>)
    @Update suspend fun updateTransaction(value: TransactionEntity)
    @Update suspend fun updateAccount(value: AccountEntity)
    @Update suspend fun updateAccounts(values: List<AccountEntity>)
    @Update suspend fun updatePaymentMethod(value: PaymentMethodEntity)
    @Update suspend fun updateTransfer(value: TransferEntity)
    @Update suspend fun updateRecurring(value: RecurringRuleEntity)
    @Update suspend fun updateBudget(value: BudgetEntity)

    // 状态校验与写入必须位于同一事务，不能依赖界面中可能过时的列表。
    @Transaction
    suspend fun setCategoryActive(id:Long,active:Boolean) {
        val current=categoryById(id)?:error("分类已不存在，请刷新后重试")
        if(current.active==active)return
        if(!active)requireNoRecurringDependency(allRecurring().firstOrNull{it.categoryId==id&&it.isEffective()},"分类")
        val order=if(active)nextManagedOrder(allCategories().filter{it.active&&it.type==current.type}.map{it.sortOrder})else current.sortOrder
        updateCategory(current.copy(active=active,sortOrder=order))
    }

    @Transaction
    suspend fun setBookActive(id:Long,active:Boolean) {
        val current=accountById(id)?:error("账本已不存在，请刷新后重试")
        if(current.active==active)return
        if(!active){
            require(allAccounts().count{it.active}>1){"至少保留一个启用账本"}
            requireNoRecurringDependency(allRecurring().firstOrNull{it.accountId==id&&it.isEffective()},"账本")
        }
        val order=if(active)nextManagedOrder(allAccounts().filter{it.active}.map{it.sortOrder})else current.sortOrder
        updateAccount(current.copy(active=active,sortOrder=order))
    }

    @Transaction
    suspend fun setPaymentMethodActive(id:Long,active:Boolean) {
        val current=paymentMethodById(id)?:error("收支方式已不存在，请刷新后重试")
        if(current.active==active)return
        if(!active){
            require(allPaymentMethods().count{it.active}>1){"至少保留一种启用收支方式"}
            requireNoRecurringDependency(allRecurring().firstOrNull{it.paymentMethodId==id&&it.isEffective()},"收支方式")
        }
        val order=if(active)nextManagedOrder(allPaymentMethods().filter{it.active}.map{it.sortOrder})else current.sortOrder
        updatePaymentMethod(current.copy(active=active,sortOrder=order))
    }

    @Transaction
    suspend fun saveCategoryChecked(value:CategoryEntity):Long {
        require(value.name.isNotBlank()){"分类名称不能为空"}
        if(value.id==0L)return insertCategory(value.copy(sortOrder=nextManagedOrder(allCategories().filter{it.active&&it.type==value.type}.map{it.sortOrder})))
        val current=categoryById(value.id)?:error("分类已不存在，请刷新后重试")
        val referenced=categoryUsage(value.id)>0||allRecurring().any{it.categoryId==value.id}
        requireCategoryTypeAllowed(current,value.type,referenced)
        val order=if(current.type!=value.type)nextManagedOrder(allCategories().filter{it.active&&it.type==value.type}.map{it.sortOrder})else current.sortOrder
        updateCategory(value.copy(active=current.active,sortOrder=order))
        return value.id
    }

    @Transaction
    suspend fun saveBookChecked(id:Long,name:String,color:Long):Long {
        require(name.isNotBlank()){"账本名称不能为空"}
        if(id==0L)return insertAccount(AccountEntity(name=name,color=color,type=AccountType.OTHER,includeInTotal=false,createdAt=System.currentTimeMillis(),sortOrder=nextManagedOrder(allAccounts().filter{it.active}.map{it.sortOrder})))
        val current=accountById(id)?:error("账本已不存在，请刷新后重试")
        // 编辑账本名称和颜色，不覆盖旧版本保存的其他账户字段。
        updateAccount(current.copy(name=name,color=color))
        return id
    }

    @Transaction
    suspend fun savePaymentMethodChecked(value:PaymentMethodEntity):Long {
        require(value.name.isNotBlank()){"收支方式名称不能为空"}
        if(value.id==0L)return insertPaymentMethod(value.copy(sortOrder=nextManagedOrder(allPaymentMethods().filter{it.active}.map{it.sortOrder})))
        val current=paymentMethodById(value.id)?:error("收支方式已不存在，请刷新后重试")
        updatePaymentMethod(current.copy(name=value.name,color=value.color))
        return value.id
    }

    @Transaction
    suspend fun saveTransactionChecked(value:TransactionEntity):Long {
        val current=if(value.id==0L)null else transactionById(value.id)?:error("账目已不存在，请刷新后重试")
        val category=categoryById(value.categoryId)?:error("分类已不存在，请重新选择")
        val book=accountById(value.accountId)?:error("账本已不存在，请重新选择")
        val method=paymentMethodById(value.paymentMethodId)?:error("收支方式已不存在，请重新选择")
        require(category.type==value.type){"分类与收支类型不一致，请重新选择"}
        require(category.active||current?.categoryId==category.id){"分类已停用，请重新选择"}
        require(book.active||current?.accountId==book.id){"账本已停用，请重新选择"}
        require(method.active||current?.paymentMethodId==method.id){"收支方式已停用，请重新选择"}
        require(value.amountCents>0){"请输入大于 0 的金额"}
        return if(current==null)insertTransaction(value)else{updateTransaction(value.copy(recurringRuleId=current.recurringRuleId));value.id}
    }

    @Transaction
    suspend fun saveRecurringChecked(value:RecurringRuleEntity):Long {
        if(value.id!=0L)require(recurringById(value.id)!=null){"周期账单已不存在，请刷新后重试"}
        validateRecurringReferences(value)
        return if(value.id==0L)insertRecurring(value)else{updateRecurring(value);value.id}
    }

    @Transaction
    suspend fun setRecurringActiveChecked(id:Long,active:Boolean):RecurringRuleEntity {
        val current=recurringById(id)?:error("周期账单已不存在，请刷新后重试")
        require(!active||current.endAt==null||current.nextAt<=current.endAt){"这条规则已结束，请先编辑下次入账和结束日期"}
        val updated=current.copy(active=active)
        validateRecurringReferences(updated)
        updateRecurring(updated)
        return updated
    }

    @Transaction
    suspend fun deleteBudgetChecked(id:Long) {
        require(budgetById(id)!=null){"预算已不存在，请刷新后重试"}
        deleteBudget(id)
    }

    suspend fun validateRecurringReferences(value:RecurringRuleEntity) {
        val category=categoryById(value.categoryId)?:error("请选择有效分类")
        val book=accountById(value.accountId)?:error("请选择有效账本")
        val method=paymentMethodById(value.paymentMethodId)?:error("请选择有效收支方式")
        require(category.type==value.type){"分类与周期账单收支类型不一致"}
        require(!value.active||category.active&&book.active&&method.active){"启用周期账单前，请先选择启用的分类、账本和收支方式"}
    }

    @Transaction
    suspend fun reorderCategoriesChecked(type:MoneyType,ids:List<Long>) {
        val active=allCategories().filter{it.active&&it.type==type}.sortedWith(compareBy<CategoryEntity>{it.sortOrder}.thenBy{it.id})
        updateCategories(normalizeCategoryOrder(active,type,ids+active.map{it.id}))
    }

    @Transaction
    suspend fun reorderBooksChecked(ids:List<Long>) {
        val active=allAccounts().filter{it.active}.sortedWith(compareBy<AccountEntity>{it.sortOrder}.thenBy{it.id})
        val byId=active.associateBy{it.id}
        updateAccounts((ids+active.map{it.id}).distinct().mapNotNull(byId::get).mapIndexed{index,item->item.copy(sortOrder=index)})
    }

    @Transaction
    suspend fun saveBudget(value: BudgetEntity): Long {
        val existing = allBudgets().firstOrNull { it.yearMonth == value.yearMonth && it.categoryId == value.categoryId && it.id != value.id }
        val original = allBudgets().firstOrNull { it.id == value.id }
        require(value.id == 0L || existing == null || original?.categoryId == value.categoryId) { "该范围已有预算，请编辑原预算" }
        val id = if(value.id != 0L) value.id else existing?.id ?: 0L
        return if(id == 0L) insertBudget(value) else { updateBudget(value.copy(id=id)); id }
    }

    @Query("DELETE FROM transactions WHERE id=:id") suspend fun deleteTransaction(id: Long)
    @Query("DELETE FROM transfers WHERE id=:id") suspend fun deleteTransfer(id: Long)
    @Query("DELETE FROM account_adjustments WHERE id=:id") suspend fun deleteAdjustment(id: Long)
    @Query("DELETE FROM budgets WHERE id=:id") suspend fun deleteBudget(id: Long)
    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId=:id") suspend fun categoryUsage(id: Long): Int
    @Query("SELECT COUNT(*) FROM transactions WHERE recurringRuleId=:ruleId AND occurredAt=:occurredAt") suspend fun recurringPostingCount(ruleId: Long, occurredAt: Long): Int
    @Query("SELECT * FROM recurring_rules WHERE id=:id") suspend fun recurringById(id: Long): RecurringRuleEntity?
    @Query("DELETE FROM recurring_rules WHERE id=:id") suspend fun deleteRecurring(id: Long)

    @Query("SELECT * FROM categories") suspend fun allCategories(): List<CategoryEntity>
    @Query("SELECT * FROM accounts") suspend fun allAccounts(): List<AccountEntity>
    @Query("SELECT * FROM payment_methods") suspend fun allPaymentMethods(): List<PaymentMethodEntity>
    @Query("SELECT * FROM transactions") suspend fun allTransactions(): List<TransactionEntity>
    @Query("SELECT * FROM transfers") suspend fun allTransfers(): List<TransferEntity>
    @Query("SELECT * FROM account_adjustments") suspend fun allAdjustments(): List<AccountAdjustmentEntity>
    @Query("SELECT * FROM budgets") suspend fun allBudgets(): List<BudgetEntity>
    @Query("SELECT * FROM recurring_rules") suspend fun allRecurring(): List<RecurringRuleEntity>

    @Transaction
    suspend fun snapshot()=BackupPayload(3,allAccounts(),allCategories(),allTransactions(),allTransfers(),allAdjustments(),allBudgets(),allRecurring(),allPaymentMethods())

    @Transaction
    suspend fun restorePreservingCurrent(backup:BackupPayload,preserve:suspend (BackupPayload)->Unit) {
        // 快照、落盘和替换共享事务，其他写入不能插在快照与替换之间。
        preserve(snapshot())
        replaceAll(backup)
    }

    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM transfers") suspend fun clearTransfers()
    @Query("DELETE FROM account_adjustments") suspend fun clearAdjustments()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM recurring_rules") suspend fun clearRecurring()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM payment_methods") suspend fun clearPaymentMethods()

    @Transaction
    suspend fun replaceAll(backup: BackupPayload) {
        clearTransactions(); clearTransfers(); clearAdjustments(); clearBudgets(); clearRecurring(); clearCategories(); clearAccounts(); clearPaymentMethods()
        insertAccounts(backup.accounts)
        insertPaymentMethods(backup.paymentMethods)
        insertCategories(backup.categories)
        insertTransactions(backup.transactions)
        insertTransfers(backup.transfers)
        insertAdjustments(backup.adjustments)
        insertBudgets(backup.budgets)
        insertRecurringRules(backup.recurringRules)
    }

    @Transaction
    suspend fun postRecurringOnce(rule: RecurringRuleEntity, transaction: TransactionEntity, updated: RecurringRuleEntity): Boolean {
        val current=recurringById(rule.id) ?: return false
        if(!current.isEffective() || current != rule) return false
        if (recurringPostingCount(rule.id, rule.nextAt) > 0) return false
        validateRecurringReferences(current)
        insertTransaction(transaction)
        updateRecurring(updated)
        return true
    }
}

data class BackupPayload(
    val version: Int,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val transfers: List<TransferEntity>,
    val adjustments: List<AccountAdjustmentEntity>,
    val budgets: List<BudgetEntity>,
    val recurringRules: List<RecurringRuleEntity>,
    val paymentMethods: List<PaymentMethodEntity>,
)
