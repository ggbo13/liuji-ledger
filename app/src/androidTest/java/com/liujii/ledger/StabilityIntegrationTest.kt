package com.liujii.ledger

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.liujii.ledger.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class StabilityIntegrationTest {
    @Test fun migrationRetainsHistoricalTransactionsAndAddsPaymentMethod() {
        val context=context()
        val helper=androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name(null).callback(object:androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1){
                override fun onCreate(db:androidx.sqlite.db.SupportSQLiteDatabase){
                    db.execSQL("CREATE TABLE accounts (id INTEGER PRIMARY KEY,name TEXT NOT NULL,openingBalanceCents INTEGER NOT NULL,active INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY, amountCents INTEGER NOT NULL, accountId INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE recurring_rules (id INTEGER PRIMARY KEY)")
                    db.execSQL("INSERT INTO accounts VALUES (7,'现金',0,1)")
                    db.execSQL("INSERT INTO transactions VALUES (42,5678,7)")
                }
                override fun onUpgrade(db:androidx.sqlite.db.SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=Unit
            }).build())
        try {
            val db=helper.writableDatabase
            LedgerDatabase.MIGRATION_1_2.migrate(db);LedgerDatabase.MIGRATION_2_3.migrate(db)
            db.query("SELECT id,amountCents,accountId,paymentMethodId FROM transactions").use{assertTrue(it.moveToFirst());assertEquals(42,it.getInt(0));assertEquals(5678,it.getInt(1));assertEquals(7,it.getInt(2));assertEquals(1,it.getInt(3))}
            db.query("SELECT name,includeInTotal FROM accounts").use{assertTrue(it.moveToFirst());assertEquals("日常",it.getString(0));assertEquals(0,it.getInt(1))}
            db.query("SELECT COUNT(*) FROM payment_methods").use{assertTrue(it.moveToFirst());assertEquals(5,it.getInt(0))}
        } finally {helper.close()}
    }

    private fun context():Context {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(base.cacheDir,"stability-${UUID.randomUUID()}").apply{mkdirs()}
        return object:ContextWrapper(base){override fun getFilesDir()=directory}
    }

    @Test fun duplicatePostingIsAtomicAndUndoPreservesOriginalIdAndAmount()=runBlocking {
        val context=context()
        val db=Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java).build()
        try {
            val dao=db.dao();val repo=LedgerRepository(context,dao)
            dao.insertAccount(AccountEntity(id=1,name="日常"));dao.insertCategory(CategoryEntity(id=1,name="餐饮",type=MoneyType.EXPENSE,icon="餐",color=0xFFE76F64));dao.insertPaymentMethod(PaymentMethodEntity(id=1,name="现金",color=0xFF4D8BB8))
            val rule=RecurringRuleEntity(id=1,title="餐费",type=MoneyType.EXPENSE,amountCents=1000,categoryId=1,accountId=1,repeatType=RepeatType.MONTHLY,startAt=1000,nextAt=1000)
            dao.insertRecurring(rule)
            val transaction=TransactionEntity(type=rule.type,amountCents=rule.amountCents,categoryId=1,accountId=1,occurredAt=1000,recurringRuleId=1)
            val results=(1..4).map{async{dao.postRecurringOnce(rule,transaction,rule.copy(nextAt=2000))}}.awaitAll()
            assertEquals(1,results.count{it});assertEquals(1,dao.allTransactions().size)
            val row=repo.transactions.first().single()
            repo.deleteTransaction(row.id);assertTrue(dao.allTransactions().isEmpty())
            repo.restoreTransaction(row)
            assertEquals(row.id,repo.transactions.first().single().id)
            assertEquals(row.amountCents,repo.transactions.first().single().amountCents)
            assertFalse(dao.postRecurringOnce(rule,transaction,rule.copy(nextAt=2000)))
        } finally {db.close()}
    }

    @Test fun failedImportDoesNotOverwriteAndVersionsOneAndThreeRestore()=runBlocking {
        val context=context();val db=Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java).build()
        try {
            val dao=db.dao();val repo=LedgerRepository(context,dao)
            dao.insertAccount(AccountEntity(id=1,name="日常"));dao.insertCategory(CategoryEntity(id=1,name="餐饮",type=MoneyType.EXPENSE,icon="餐",color=0xFFE76F64));dao.insertPaymentMethod(PaymentMethodEntity(id=1,name="现金",color=0xFF4D8BB8))
            dao.insertTransaction(TransactionEntity(id=1,type=MoneyType.EXPENSE,amountCents=5678,categoryId=1,accountId=1,occurredAt=1000))
            val output=ByteArrayOutputStream();repo.exportJson(output)
            val root=JSONObject(output.toString("UTF-8"))
            val invalid=JSONObject(root.toString());invalid.getJSONArray("transactions").getJSONObject(0).put("categoryId",999)
            val bad=File(context.filesDir,"bad.json").apply{writeText(invalid.toString())}
            assertTrue(runCatching{repo.importJson(Uri.fromFile(bad))}.isFailure)
            assertEquals(5678L,dao.allTransactions().single().amountCents)
            for(version in listOf(1,3)) {
                val file=File(context.filesDir,"version-$version.json").apply{writeText(JSONObject(root.toString()).put("version",version).toString())}
                repo.importJson(Uri.fromFile(file))
                assertEquals(5678L,dao.allTransactions().single().amountCents)
            }
        } finally {db.close()}
    }
}
