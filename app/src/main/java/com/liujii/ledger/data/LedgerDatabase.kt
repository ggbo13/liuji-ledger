package com.liujii.ledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CategoryEntity::class, AccountEntity::class, PaymentMethodEntity::class, TransactionEntity::class, TransferEntity::class, AccountAdjustmentEntity::class, BudgetEntity::class, RecurringRuleEntity::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao

    companion object {
        @Volatile private var instance: LedgerDatabase? = null

        fun get(context: Context): LedgerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, LedgerDatabase::class.java, "liuji.db")
                .addCallback(SeedCallback(context))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE accounts ADD COLUMN type TEXT NOT NULL DEFAULT 'CASH'")
            db.execSQL("ALTER TABLE accounts ADD COLUMN color INTEGER NOT NULL DEFAULT 4283272120")
            db.execSQL("ALTER TABLE accounts ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 99")
            db.execSQL("ALTER TABLE accounts ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE accounts ADD COLUMN includeInTotal INTEGER NOT NULL DEFAULT 1")
            db.execSQL("CREATE TABLE IF NOT EXISTS transfers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fromAccountId INTEGER NOT NULL, toAccountId INTEGER NOT NULL, amountCents INTEGER NOT NULL, occurredAt INTEGER NOT NULL, note TEXT NOT NULL, FOREIGN KEY(fromAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(toAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transfers_fromAccountId ON transfers(fromAccountId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transfers_toAccountId ON transfers(toAccountId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transfers_occurredAt ON transfers(occurredAt)")
            db.execSQL("CREATE TABLE IF NOT EXISTS account_adjustments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, accountId INTEGER NOT NULL, deltaCents INTEGER NOT NULL, occurredAt INTEGER NOT NULL, note TEXT NOT NULL, FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_account_adjustments_accountId ON account_adjustments(accountId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_account_adjustments_occurredAt ON account_adjustments(occurredAt)")
        }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS payment_methods (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, color INTEGER NOT NULL, sortOrder INTEGER NOT NULL, active INTEGER NOT NULL)")
                defaultPaymentMethods.forEach { (id, name, color, order) ->
                    db.execSQL("INSERT OR IGNORE INTO payment_methods (id,name,color,sortOrder,active) VALUES (?,?,?,?,1)", arrayOf(id,name,color,order))
                }
                db.execSQL("ALTER TABLE transactions ADD COLUMN paymentMethodId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE recurring_rules ADD COLUMN paymentMethodId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE accounts SET name='日常', openingBalanceCents=0, type='OTHER', includeInTotal=0 WHERE name='现金'")
            }
        }
    }
}

private val defaultPaymentMethods = listOf(
    arrayOf<Any>(1, "现金", 0xFF4D8BB8, 0), arrayOf<Any>(2, "支付宝", 0xFF1677FF, 1),
    arrayOf<Any>(3, "微信", 0xFF2FAE83, 2), arrayOf<Any>(4, "银行卡", 0xFF9A78C6, 3),
    arrayOf<Any>(5, "数字人民币", 0xFFE76F64, 4),
)

private class SeedCallback(private val context: Context) : RoomDatabase.Callback() {
    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        super.onCreate(db)
        db.execSQL("INSERT INTO accounts (name,openingBalanceCents,active,type,color,sortOrder,createdAt,includeInTotal) VALUES ('日常',0,1,'OTHER',4283272120,0,0,0)")
        defaultPaymentMethods.forEach { (id, name, color, order) -> db.execSQL("INSERT INTO payment_methods (id,name,color,sortOrder,active) VALUES (?,?,?,?,1)", arrayOf(id,name,color,order)) }
        val rows = listOf(
            "餐饮|EXPENSE|餐|4293444196|0", "交通|EXPENSE|行|4283215696|1",
            "购物|EXPENSE|购|4293353225|2", "居住|EXPENSE|住|4284907380|3",
            "娱乐|EXPENSE|乐|4288373759|4", "医疗|EXPENSE|医|4292245602|5",
            "工资|INCOME|薪|4281319043|0", "奖金|INCOME|奖|4286023833|1",
            "理财|INCOME|盈|4281186228|2", "其他|INCOME|入|4286611584|3",
            "其他|EXPENSE|支|4286874750|6",
        )
        rows.forEach {
            val p = it.split('|')
            db.execSQL("INSERT INTO categories (name,type,icon,color,sortOrder,active) VALUES (?,?,?,?,?,1)", arrayOf<Any>(p[0], p[1], p[2], p[3].toLong(), p[4].toInt()))
        }
    }
}
