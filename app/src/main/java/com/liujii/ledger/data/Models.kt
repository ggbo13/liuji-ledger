package com.liujii.ledger.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

enum class MoneyType { EXPENSE, INCOME }
enum class RepeatType { WEEKLY, MONTHLY }
enum class AccountType { CASH, BANK_CARD, EWALLET, OTHER }

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: MoneyType,
    val icon: String,
    val color: Long,
    val sortOrder: Int = 0,
    val active: Boolean = true,
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val openingBalanceCents: Long = 0,
    val active: Boolean = true,
    @ColumnInfo(defaultValue = "'CASH'") val type: AccountType = AccountType.CASH,
    @ColumnInfo(defaultValue = "4283272120") val color: Long = 0xFF4D8BB8,
    @ColumnInfo(defaultValue = "99") val sortOrder: Int = 99,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0,
    @ColumnInfo(defaultValue = "1") val includeInTotal: Boolean = true,
)

@Entity(tableName = "payment_methods")
data class PaymentMethodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long,
    val sortOrder: Int = 99,
    val active: Boolean = true,
)

@Entity(
    tableName = "transfers",
    foreignKeys = [
        ForeignKey(AccountEntity::class, ["id"], ["fromAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(AccountEntity::class, ["id"], ["toAccountId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("fromAccountId"), Index("toAccountId"), Index("occurredAt")],
)
data class TransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amountCents: Long,
    val occurredAt: Long,
    val note: String = "",
)

@Entity(
    tableName = "account_adjustments",
    foreignKeys = [ForeignKey(AccountEntity::class, ["id"], ["accountId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("accountId"), Index("occurredAt")],
)
data class AccountAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val deltaCents: Long,
    val occurredAt: Long,
    val note: String = "余额校准",
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(AccountEntity::class, ["id"], ["accountId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("categoryId"), Index("accountId"), Index("occurredAt")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: MoneyType,
    val amountCents: Long,
    val categoryId: Long,
    val accountId: Long,
    val occurredAt: Long,
    val note: String = "",
    val recurringRuleId: Long? = null,
    @ColumnInfo(defaultValue = "1") val paymentMethodId: Long = 1,
)

@Entity(tableName = "budgets", indices = [Index("categoryId")])
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearMonth: String,
    val categoryId: Long? = null,
    val limitCents: Long,
)

@Entity(tableName = "recurring_rules", indices = [Index("categoryId"), Index("accountId")])
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: MoneyType,
    val amountCents: Long,
    val categoryId: Long,
    val accountId: Long,
    val repeatType: RepeatType,
    val startAt: Long,
    val endAt: Long? = null,
    val nextAt: Long,
    val remindDaysBefore: Int = 0,
    val remindHour: Int = 9,
    val remindMinute: Int = 0,
    val active: Boolean = true,
    @ColumnInfo(defaultValue = "1") val paymentMethodId: Long = 1,
)

data class TransactionRow(
    val id: Long,
    val type: MoneyType,
    val amountCents: Long,
    val categoryId: Long,
    val accountId: Long,
    val occurredAt: Long,
    val note: String,
    val recurringRuleId: Long?,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: Long,
    val accountName: String,
    val paymentMethodId: Long,
    val paymentMethodName: String,
    val paymentMethodColor: Long,
)

data class TransferRow(
    val id: Long,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amountCents: Long,
    val occurredAt: Long,
    val note: String,
    val fromAccountName: String,
    val toAccountName: String,
)

data class AccountAdjustmentRow(
    val id: Long,
    val accountId: Long,
    val deltaCents: Long,
    val occurredAt: Long,
    val note: String,
    val accountName: String,
)

data class DayTotal(val day: String, val incomeCents: Long, val expenseCents: Long)

data class AppearanceSettings(
    val themeMode: String = "system",
    val backgroundType: String = "gradient",
    val colorStart: Long = 0xFFEAF1F7,
    val colorEnd: Long = 0xFFD9F1EB,
    val imageUri: String? = null,
    val overlayAlpha: Float = 0.84f,
    val appLockEnabled: Boolean = false,
    val themeId: String = "mint",
    val useThemeBackground: Boolean = false,
    val motionEnabled: Boolean = true,
)
