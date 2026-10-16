package com.liujii.ledger

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.liujii.ledger.data.AccountEntity
import com.liujii.ledger.data.CategoryEntity
import com.liujii.ledger.data.LedgerDatabase
import com.liujii.ledger.data.LedgerRepository
import com.liujii.ledger.data.MoneyType
import com.liujii.ledger.data.PaymentMethodEntity
import com.liujii.ledger.data.TransactionEntity
import com.liujii.ledger.data.summarize
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryColorIntegrationTest {
    @Test
    fun editingCategoryColorRefreshesTransactionsAndBackupWithoutChangingAmounts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).build()
        try {
            val dao = database.dao()
            val repository = LedgerRepository(context, dao)
            val categoryId = repository.saveCategory(
                CategoryEntity(name = "购物", type = MoneyType.EXPENSE, icon = "购", color = 0xFFE75F09)
            )
            val accountId = repository.saveAccount(AccountEntity(name = "日常"))
            val methodId = repository.savePaymentMethod(PaymentMethodEntity(name = "现金", color = 0xFF4D8BB8))
            repository.saveTransaction(
                TransactionEntity(type = MoneyType.EXPENSE, amountCents = 1280,
                    categoryId = categoryId, accountId = accountId, occurredAt = 1000,
                    paymentMethodId = methodId)
            )

            val original = repository.categories.first().single()
            repository.saveCategory(original.copy(color = 0xFF4D8BB8))

            assertEquals(0xFF4D8BB8, repository.categories.first().single().color)
            assertEquals(0xFF4D8BB8, repository.transactions.first().single().categoryColor)
            assertEquals(1, repository.categoryUsage(categoryId))
            assertEquals(1280, summarize(dao.allTransactions()).expenseCents)

            val backup = ByteArrayOutputStream().also { repository.exportJson(it) }
            val savedCategory = JSONObject(backup.toString("UTF-8"))
                .getJSONArray("categories").getJSONObject(0)
            assertEquals(0xFF4D8BB8, savedCategory.getLong("color"))
        } finally {
            database.close()
        }
    }
}
