package com.liujii.ledger

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.liujii.ledger.data.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManagementIntegrationTest {
    private suspend fun withDatabase(test: suspend (LedgerDao) -> Unit) {
        // 每个测试独立创建内存数据库，不调用真实数据库单例或种子回调。
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).build()
        try {
            test(database.dao())
        } finally {
            database.close()
        }
    }

    private suspend fun seedReferences(dao: LedgerDao) {
        dao.insertCategory(CategoryEntity(id = 1, name = "餐饮", type = MoneyType.EXPENSE,
            icon = "餐", color = 0xFFE76F64, sortOrder = 2))
        dao.insertCategory(CategoryEntity(id = 2, name = "交通", type = MoneyType.EXPENSE,
            icon = "行", color = 0xFF4D8BB8, sortOrder = 5))
        dao.insertCategory(CategoryEntity(id = 3, name = "工资", type = MoneyType.INCOME,
            icon = "薪", color = 0xFF2FAE83, sortOrder = 200))
        dao.insertCategory(CategoryEntity(id = 4, name = "旧分类", type = MoneyType.EXPENSE,
            icon = "旧", color = 0xFF9A78C6, sortOrder = 900, active = false))
        dao.insertAccount(AccountEntity(id = 1, name = "旧账户", openingBalanceCents = 12_345,
            type = AccountType.BANK_CARD, color = 0xFF9A78C6, sortOrder = 2,
            createdAt = 1_234, includeInTotal = true))
        dao.insertAccount(AccountEntity(id = 2, name = "日常", type = AccountType.OTHER,
            sortOrder = 7, createdAt = 2_345, includeInTotal = false))
        dao.insertAccount(AccountEntity(id = 3, name = "旧账本", sortOrder = 900, active = false))
        dao.insertPaymentMethod(PaymentMethodEntity(id = 1, name = "现金", color = 0xFF4D8BB8, sortOrder = 3))
        dao.insertPaymentMethod(PaymentMethodEntity(id = 2, name = "银行卡", color = 0xFF9A78C6, sortOrder = 8))
        dao.insertPaymentMethod(PaymentMethodEntity(id = 3, name = "旧方式", color = 0xFFE76F64,
            sortOrder = 900, active = false))
    }

    private fun rule(id: Long = 1, active: Boolean = true, endAt: Long? = null) =
        RecurringRuleEntity(id = id, title = "周期餐费$id", type = MoneyType.EXPENSE,
            amountCents = 1_000, categoryId = 1, accountId = 1, paymentMethodId = 1,
            repeatType = RepeatType.MONTHLY, startAt = 500, nextAt = 1_000,
            active = active, endAt = endAt)

    private fun transaction(id: Long = 0) = TransactionEntity(id = id, type = MoneyType.EXPENSE,
        amountCents = 1_280, categoryId = 1, accountId = 1, paymentMethodId = 1,
        occurredAt = 1_000, note = "历史餐费")

    private suspend fun assertRejected(message: String, action: suspend () -> Unit) {
        val error = runCatching { action() }.exceptionOrNull()
        assertTrue("应拒绝该操作，实际异常为 $error", error is IllegalArgumentException)
        assertEquals(message, error?.message)
    }

    private suspend fun raceDeactivations(
        ids: List<Long>, deactivate: suspend (Long) -> Unit,
    ): List<Result<Unit>> = coroutineScope {
        // 两个协程就绪后同时放行，检验事务内的最后启用项保护。
        val ready = ids.map { CompletableDeferred<Unit>() }
        val start = CompletableDeferred<Unit>()
        val attempts = ids.mapIndexed { index, id ->
            async(Dispatchers.Default) {
                ready[index].complete(Unit)
                start.await()
                runCatching { deactivate(id) }
            }
        }
        ready.awaitAll()
        start.complete(Unit)
        attempts.awaitAll()
    }

    @Test(timeout = 30_000) fun concurrentBookDeactivationSucceedsOnlyOnceAndKeepsLastBook() = runBlocking {
        withDatabase { dao ->
            seedReferences(dao)
            val before = dao.allAccounts()
            val results = raceDeactivations(listOf(1L, 2L)) { dao.setBookActive(it, false) }
            assertEquals(1, results.count { it.isSuccess })
            val error = results.single { it.isFailure }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertEquals("至少保留一个启用账本", error?.message)
            assertEquals(1, dao.activeAccountCount())
            val after = dao.allAccounts()
            assertEquals(before.map { it.id }.toSet(), after.map { it.id }.toSet())
            for (account in after) {
                assertEquals(before.single { it.id == account.id }.copy(active = account.active), account)
            }
            assertFalse(dao.accountById(3)!!.active)
        }
    }

    @Test(timeout = 30_000) fun concurrentMethodDeactivationSucceedsOnlyOnceAndKeepsLastMethod() = runBlocking {
        withDatabase { dao ->
            seedReferences(dao)
            val before = dao.allPaymentMethods()
            val results = raceDeactivations(listOf(1L, 2L)) { dao.setPaymentMethodActive(it, false) }
            assertEquals(1, results.count { it.isSuccess })
            val error = results.single { it.isFailure }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertEquals("至少保留一种启用收支方式", error?.message)
            val after = dao.allPaymentMethods()
            assertEquals(1, after.count { it.active })
            assertEquals(before.map { it.id }.toSet(), after.map { it.id }.toSet())
            for (method in after) {
                assertEquals(before.single { it.id == method.id }.copy(active = method.active), method)
            }
            assertFalse(dao.paymentMethodById(3)!!.active)
        }
    }

    @Test fun effectiveRecurringDependencyBlocksAllThreeDeactivationsWithoutMutation() = runBlocking {
        withDatabase { dao ->
            seedReferences(dao)
            dao.insertRecurring(rule(1, active = false))
            dao.insertRecurring(rule(2, endAt = 999))
            val effective = rule(3, endAt = 1_000)
            dao.insertRecurring(effective)
            val before = dao.snapshot()
            assertRejected("分类 正被周期账单“${effective.title}”使用，请先暂停或修改该账单") {
                dao.setCategoryActive(1, false)
            }
            assertEquals(before, dao.snapshot())
            assertRejected("账本 正被周期账单“${effective.title}”使用，请先暂停或修改该账单") {
                dao.setBookActive(1, false)
            }
            assertEquals(before, dao.snapshot())
            assertRejected("收支方式 正被周期账单“${effective.title}”使用，请先暂停或修改该账单") {
                dao.setPaymentMethodActive(1, false)
            }
            assertEquals(before, dao.snapshot())
        }
    }

    private suspend fun assertIneffectiveDependencyAllowsDeactivation(dependency: RecurringRuleEntity) {
        withDatabase { dao ->
            seedReferences(dao)
            dao.insertRecurring(dependency)
            val category = dao.categoryById(1)!!
            val book = dao.accountById(1)!!
            val method = dao.paymentMethodById(1)!!
            val typeError = "已有账目或周期账单引用此分类，不能改变收支类型"
            assertRejected(typeError) { dao.saveCategoryChecked(category.copy(type = MoneyType.INCOME)) }
            assertEquals(category, dao.categoryById(1))
            dao.setCategoryActive(1, false)
            dao.setBookActive(1, false)
            dao.setPaymentMethodActive(1, false)
            assertEquals(category.copy(active = false), dao.categoryById(1))
            assertEquals(book.copy(active = false), dao.accountById(1))
            assertEquals(method.copy(active = false), dao.paymentMethodById(1))
            val before = dao.snapshot()
            assertRejected(typeError) {
                dao.saveCategoryChecked(category.copy(type = MoneyType.INCOME, active = false))
            }
            assertEquals(before, dao.snapshot())
            assertEquals(dependency, dao.recurringById(dependency.id))
        }
    }

    @Test fun pausedRuleAllowsDeactivationButStillPreventsCategoryTypeChange() = runBlocking {
        assertIneffectiveDependencyAllowsDeactivation(rule(active = false))
    }

    @Test fun expiredRuleAllowsDeactivationButStillPreventsCategoryTypeChange() = runBlocking {
        assertIneffectiveDependencyAllowsDeactivation(rule(endAt = 999))
    }

    @Test fun historicalTransactionProtectsCategoryTypeButAllowsSameTypeRename() = runBlocking {
        withDatabase { dao ->
            seedReferences(dao)
            val historical = transaction(42)
            dao.insertTransaction(historical)
            val original = dao.categoryById(1)!!
            assertRejected("已有账目或周期账单引用此分类，不能改变收支类型") {
                dao.saveCategoryChecked(original.copy(type = MoneyType.INCOME))
            }
            assertEquals(original, dao.categoryById(1))
            val renamed = original.copy(name = "餐饮新名")
            assertEquals(1L, dao.saveCategoryChecked(renamed))
            assertEquals(renamed, dao.categoryById(1))
            assertEquals(historical, dao.transactionById(42))
            assertEquals(1, dao.categoryUsage(1))
            assertEquals("餐饮新名", dao.transactions().first().single().categoryName)
        }
    }

    @Test fun unreferencedTypeChangeAppendsToTargetTypeAndPreservesActiveState() = runBlocking {
        withDatabase { dao ->
            seedReferences(dao)
            dao.insertCategory(CategoryEntity(id = 5, name = "旧收入", type = MoneyType.INCOME,
                icon = "旧", color = 0xFF2FAE83, sortOrder = 900, active = false))
            val original = dao.categoryById(2)!!
            assertEquals(2L, dao.saveCategoryChecked(original.copy(type = MoneyType.INCOME,
                active = false, sortOrder = -10)))
            assertEquals(original.copy(type = MoneyType.INCOME, sortOrder = 201), dao.categoryById(2))
        }
    }

    @Test fun restoringAllManagedItemsAppendsAndPreservesHistoricalIdsAndLegacyFields() = runBlocking {
        withDatabase { dao ->
            seedReferences(dao)
            val category = dao.categoryById(1)!!
            val book = dao.accountById(1)!!
            val method = dao.paymentMethodById(1)!!
            val historical = transaction(42)
            val paused = rule(7, active = false)
            val transfer = TransferEntity(id = 51, fromAccountId = 1, toAccountId = 2,
                amountCents = 200, occurredAt = 1_000)
            val adjustment = AccountAdjustmentEntity(id = 61, accountId = 1,
                deltaCents = -123, occurredAt = 1_000)
            val budget = BudgetEntity(id = 71, yearMonth = "2026-10", categoryId = 1, limitCents = 5_000)
            dao.insertTransaction(historical)
            dao.insertRecurring(paused)
            dao.insertTransfer(transfer)
            dao.insertAdjustment(adjustment)
            dao.insertBudget(budget)
            dao.setCategoryActive(1, false)
            dao.setBookActive(1, false)
            dao.setPaymentMethodActive(1, false)
            assertEquals(category.copy(active = false), dao.categoryById(1))
            assertEquals(book.copy(active = false), dao.accountById(1))
            assertEquals(method.copy(active = false), dao.paymentMethodById(1))
            assertEquals(42L, dao.transactions().first().single().id)
            dao.setCategoryActive(1, true)
            dao.setBookActive(1, true)
            dao.setPaymentMethodActive(1, true)
            // 收入分类及停用项的较大序号不得影响恢复后的追加位置。
            assertEquals(category.copy(sortOrder = 6), dao.categoryById(1))
            assertEquals(book.copy(sortOrder = 8), dao.accountById(1))
            assertEquals(method.copy(sortOrder = 9), dao.paymentMethodById(1))
            assertEquals(listOf(2L, 1L), orderedCategoryChoices(dao.categories().first(), MoneyType.EXPENSE).map { it.id })
            assertEquals(listOf(2L, 1L), dao.accounts().first().map { it.id })
            assertEquals(listOf(2L, 1L), dao.paymentMethods().first().map { it.id })
            assertEquals(historical, dao.transactionById(42))
            assertEquals(paused, dao.recurringById(7))
            assertEquals(listOf(transfer), dao.allTransfers())
            assertEquals(listOf(adjustment), dao.allAdjustments())
            assertEquals(budget, dao.budgetById(71))
            val row = dao.transactions().first().single()
            assertEquals(1L, row.categoryId)
            assertEquals(1L, row.accountId)
            assertEquals(1L, row.paymentMethodId)
            assertEquals(1_280L, row.amountCents)
            val restored = dao.snapshot()
            dao.setCategoryActive(1, true)
            dao.setBookActive(1, true)
            dao.setPaymentMethodActive(1, true)
            assertEquals(restored, dao.snapshot())
        }
    }

    @Test fun editingInactiveLegacyBookPreservesAllOtherFieldsAndHistoricalAssociation() = runBlocking {
        withDatabase { dao ->
            seedReferences(dao)
            val historical = transaction(42)
            dao.insertTransaction(historical)
            dao.setBookActive(1, false)
            val original = dao.accountById(1)!!
            assertEquals(1L, dao.saveBookChecked(1, "旧账本新名", 0xFF2FAE83))
            val expected = original.copy(name = "旧账本新名", color = 0xFF2FAE83)
            assertEquals(expected, dao.accountById(1))
            assertEquals(historical, dao.transactionById(42))
            assertEquals("旧账本新名", dao.transactions().first().single().accountName)
            dao.setBookActive(1, true)
            assertEquals(expected.copy(active = true, sortOrder = 8), dao.accountById(1))
        }
    }

    @Test fun editingInactiveCategoryAndMethodPreservesStateOrderAndHistoricalAssociation() = runBlocking {
        withDatabase { dao ->
            seedReferences(dao)
            val historical = transaction(42)
            dao.insertTransaction(historical)
            dao.setCategoryActive(1, false)
            dao.setPaymentMethodActive(1, false)
            val category = dao.categoryById(1)!!
            val method = dao.paymentMethodById(1)!!
            assertEquals(1L, dao.saveCategoryChecked(category.copy(name = "旧分类新名",
                color = 0xFF2FAE83, active = true, sortOrder = -10)))
            assertEquals(1L, dao.savePaymentMethodChecked(method.copy(name = "旧方式新名",
                color = 0xFFE76F64, active = true, sortOrder = -10)))
            assertEquals(category.copy(name = "旧分类新名", color = 0xFF2FAE83), dao.categoryById(1))
            assertEquals(method.copy(name = "旧方式新名", color = 0xFFE76F64), dao.paymentMethodById(1))
            assertEquals(historical, dao.transactionById(42))
            val row = dao.transactions().first().single()
            assertEquals("旧分类新名", row.categoryName)
            assertEquals("旧方式新名", row.paymentMethodName)
        }
    }

    @Test fun managementFlowsIncludeInactiveWhileNewEntryChoicesExcludeThem() = runBlocking {
        withDatabase { dao ->
            seedReferences(dao)
            val categories = dao.categories().first()
            assertEquals(listOf(1L, 2L, 3L, 4L), categories.map { it.id })
            assertFalse(categories.last().active)
            assertEquals(listOf(1L, 2L, 3L), dao.managedAccounts().first().map { it.id })
            assertFalse(dao.managedAccounts().first().last().active)
            assertEquals(listOf(1L, 2L, 3L), dao.managedPaymentMethods().first().map { it.id })
            assertFalse(dao.managedPaymentMethods().first().last().active)
            assertEquals(listOf(1L, 2L), dao.accounts().first().map { it.id })
            assertEquals(listOf(1L, 2L), dao.paymentMethods().first().map { it.id })
            assertEquals(listOf(1L, 2L), orderedCategoryChoices(categories, MoneyType.EXPENSE).map { it.id })
            assertEquals(listOf(3L), orderedCategoryChoices(categories, MoneyType.INCOME).map { it.id })
            dao.setCategoryActive(1, false)
            dao.setBookActive(1, false)
            dao.setPaymentMethodActive(1, false)
            assertEquals(listOf(2L, 3L, 1L, 4L), dao.categories().first().map { it.id })
            assertEquals(listOf(2L, 1L, 3L), dao.managedAccounts().first().map { it.id })
            assertEquals(listOf(2L, 1L, 3L), dao.managedPaymentMethods().first().map { it.id })
            assertEquals(listOf(2L), dao.accounts().first().map { it.id })
            assertEquals(listOf(2L), dao.paymentMethods().first().map { it.id })
            assertEquals(listOf(2L), orderedCategoryChoices(dao.categories().first(), MoneyType.EXPENSE).map { it.id })
        }
    }

    @Test fun historicalEditRetainsOriginalInactiveReferencesAndRecurringOrigin() = runBlocking {
        withDatabase { dao ->
            seedReferences(dao)
            dao.insertRecurring(rule(7, active = false))
            val historical = transaction(42).copy(recurringRuleId = 7)
            dao.insertTransaction(historical)
            dao.setCategoryActive(1, false)
            dao.setBookActive(1, false)
            dao.setPaymentMethodActive(1, false)
            val edited = historical.copy(amountCents = 2_345, note = "编辑历史账目", occurredAt = 2_000)
            assertEquals(42L, dao.saveTransactionChecked(edited.copy(recurringRuleId = null)))
            assertEquals(edited, dao.transactionById(42))
            val row = dao.transactions().first().single()
            assertEquals(42L, row.id)
            assertEquals(1L, row.categoryId)
            assertEquals(1L, row.accountId)
            assertEquals(1L, row.paymentMethodId)
            assertEquals(2_345L, row.amountCents)
            assertEquals(listOf(1L, 2L), orderedCategoryChoices(dao.categories().first(), MoneyType.EXPENSE, 1).map { it.id })
        }
    }

    @Test fun newTransactionsRejectEachInactiveReferenceAndCopyingGetsNoHistoricalException() = runBlocking {
        withDatabase { dao ->
            seedReferences(dao)
            val historical = transaction(42)
            dao.insertTransaction(historical)
            val before = dao.snapshot()
            assertRejected("分类已停用，请重新选择") {
                dao.saveTransactionChecked(transaction().copy(categoryId = 4))
            }
            assertRejected("账本已停用，请重新选择") {
                dao.saveTransactionChecked(transaction().copy(accountId = 3))
            }
            assertRejected("收支方式已停用，请重新选择") {
                dao.saveTransactionChecked(transaction().copy(paymentMethodId = 3))
            }
            assertEquals(before, dao.snapshot())
            dao.setCategoryActive(1, false)
            dao.setBookActive(1, false)
            dao.setPaymentMethodActive(1, false)
            val inactive = dao.snapshot()
            assertRejected("分类已停用，请重新选择") {
                dao.saveTransactionChecked(historical.copy(id = 0))
            }
            assertEquals(inactive, dao.snapshot())
            val newId = dao.saveTransactionChecked(transaction().copy(categoryId = 2, accountId = 2, paymentMethodId = 2))
            assertTrue(newId != 42L)
            assertEquals(2, dao.allTransactions().size)
        }
    }

    @Test fun historicalEditRejectsSwitchingToAnotherInactiveReferenceButAllowsActiveReferences() = runBlocking {
        withDatabase { dao ->
            seedReferences(dao)
            val historical = transaction(42)
            dao.insertTransaction(historical)
            dao.setCategoryActive(1, false)
            dao.setBookActive(1, false)
            dao.setPaymentMethodActive(1, false)
            val before = dao.snapshot()
            assertRejected("分类已停用，请重新选择") {
                dao.saveTransactionChecked(historical.copy(categoryId = 4))
            }
            assertRejected("账本已停用，请重新选择") {
                dao.saveTransactionChecked(historical.copy(accountId = 3))
            }
            assertRejected("收支方式已停用，请重新选择") {
                dao.saveTransactionChecked(historical.copy(paymentMethodId = 3))
            }
            assertEquals(before, dao.snapshot())
            val switched = historical.copy(categoryId = 2, accountId = 2, paymentMethodId = 2)
            assertEquals(42L, dao.saveTransactionChecked(switched))
            assertEquals(switched, dao.transactionById(42))
            assertEquals(1, dao.allTransactions().size)
            assertRejected("分类已停用，请重新选择") { dao.saveTransactionChecked(historical) }
            assertEquals(switched, dao.transactionById(42))
        }
    }

    @Test fun expiredRuleCannotPostOrAdvanceEvenWhenMarkedActive() = runBlocking {
        withDatabase { dao ->
            seedReferences(dao)
            val expired = rule(endAt = 999)
            dao.insertRecurring(expired)
            val before = dao.snapshot()
            val posting = transaction().copy(amountCents = expired.amountCents,
                occurredAt = expired.nextAt, recurringRuleId = expired.id)
            assertFalse(dao.postRecurringOnce(expired, posting, expired.copy(nextAt = 2_000)))
            assertEquals(before, dao.snapshot())
            assertTrue(dao.allTransactions().isEmpty())
            assertEquals(expired, dao.recurringById(expired.id))
            // 结束日等于下次入账日仍可入账，避免将有效边界一并拦截。
            val boundary = expired.copy(endAt = expired.nextAt)
            dao.updateRecurring(boundary)
            val advanced = boundary.copy(nextAt = 2_000)
            assertTrue(dao.postRecurringOnce(boundary, posting, advanced))
            val saved = dao.allTransactions().single()
            assertEquals(posting.copy(id = saved.id), saved)
            assertEquals(advanced, dao.recurringById(boundary.id))
        }
    }

    @Test fun preservingFailureLeavesCurrentDatabaseUnchanged() = runBlocking {
        withDatabase { dao ->
            seedReferences(dao)
            dao.insertTransaction(transaction(42))
            dao.insertRecurring(rule(7, active = false))
            dao.insertTransfer(TransferEntity(id = 51, fromAccountId = 1, toAccountId = 2,
                amountCents = 200, occurredAt = 1_000))
            dao.insertAdjustment(AccountAdjustmentEntity(id = 61, accountId = 1,
                deltaCents = -123, occurredAt = 1_000))
            dao.insertBudget(BudgetEntity(id = 71, yearMonth = "2026-10", categoryId = 1, limitCents = 5_000))
            val before = dao.snapshot()
            val backup = before.copy(
                accounts = before.accounts.map { it.copy(name = "备份${it.name}") },
                transactions = listOf(transaction(84).copy(note = "导入的账目")),
            )
            val failure = IllegalStateException("保护备份保存失败")
            var preserved: BackupPayload? = null
            var preserveCount = 0
            val error = runCatching {
                dao.restorePreservingCurrent(backup) {
                    preserveCount += 1
                    preserved = it
                    throw failure
                }
            }.exceptionOrNull()
            assertSame(failure, error)
            assertEquals(1, preserveCount)
            assertEquals(before, preserved)
            assertEquals(before, dao.snapshot())
        }
    }

    @Test(timeout = 30_000) fun concurrentNewTransactionWaitsForRestoreAndSurvivesReplacement() = runBlocking {
        withDatabase { dao ->
            seedReferences(dao)
            dao.insertTransaction(transaction(42))
            val before = dao.snapshot()
            val imported = transaction(84).copy(amountCents = 5_000, note = "导入的账目")
            val backup = before.copy(transactions = listOf(imported))
            val newTransaction = transaction().copy(amountCents = 2_345, note = "恢复期间新增")
            assertEquals(0L, newTransaction.id)
            coroutineScope {
                val preserved = CompletableDeferred<BackupPayload>()
                val releasePreserve = CompletableDeferred<Unit>()
                val writerStarted = CompletableDeferred<Unit>()
                val restoring = async(Dispatchers.Default) {
                    dao.restorePreservingCurrent(backup) {
                        preserved.complete(it)
                        releasePreserve.await()
                    }
                }
                try {
                    assertEquals(before, preserved.await())
                    // 从事务外启动写入，避免继承恢复事务的协程上下文。
                    val writing = async(Dispatchers.Default) {
                        writerStarted.complete(Unit)
                        dao.saveTransactionChecked(newTransaction)
                    }
                    writerStarted.await()
                    assertNull("保护回调悬停时，新增账目必须等待恢复事务结束",
                        withTimeoutOrNull(300) { writing.await() })
                    assertFalse(restoring.isCompleted)
                    releasePreserve.complete(Unit)
                    restoring.await()
                    val newId = writing.await()
                    assertTrue(newId > 0 && newId != imported.id)
                    assertEquals(newTransaction.copy(id = newId), dao.transactionById(newId))
                    assertEquals(imported, dao.transactionById(imported.id))
                    assertNull(dao.transactionById(42))
                    val expected = backup.copy(transactions = listOf(imported, newTransaction.copy(id = newId)))
                    val actual = dao.snapshot()
                    assertEquals(expected.copy(transactions = expected.transactions.sortedBy { it.id }),
                        actual.copy(transactions = actual.transactions.sortedBy { it.id }))
                } finally {
                    // 断言失败时同样解除悬停，由协程作用域等待子任务退出后再关闭数据库。
                    releasePreserve.complete(Unit)
                }
            }
        }
    }
}
