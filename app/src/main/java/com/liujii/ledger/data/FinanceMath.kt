package com.liujii.ledger.data

import java.time.LocalDate

// 金额必须精确到分，非法精度与溢出均返回空值。
fun parseMoneyCents(value: String): Long? = runCatching {
    value.trim().replace(',', '.').toBigDecimal().movePointRight(2).longValueExact()
}.getOrNull()

data class MoneySummary(val incomeCents: Long, val expenseCents: Long) {
    val balanceCents: Long get() = incomeCents - expenseCents
}

fun summarize(rows: Iterable<TransactionEntity>): MoneySummary = MoneySummary(
    incomeCents = rows.filter { it.type == MoneyType.INCOME }.sumOf { it.amountCents },
    expenseCents = rows.filter { it.type == MoneyType.EXPENSE }.sumOf { it.amountCents },
)

fun nextOccurrence(date: LocalDate, repeatType: RepeatType): LocalDate = when (repeatType) {
    RepeatType.WEEKLY -> date.plusWeeks(1)
    RepeatType.MONTHLY -> date.plusMonths(1)
}

fun normalizeCategoryOrder(
    categories: List<CategoryEntity>,
    type: MoneyType,
    orderedIds: List<Long>,
): List<CategoryEntity> {
    val categoryById = categories
        .filter { it.active && it.type == type }
        .associateBy { it.id }

    // 先去重并排除已停用或其他类型的分类，再生成连续顺序，避免排序值空洞。
    return orderedIds.distinct()
        .mapNotNull(categoryById::get)
        .mapIndexed { index, category -> category.copy(sortOrder = index) }
}

fun accountBalance(
    account: AccountEntity,
    transactions: Iterable<TransactionRow>,
    transfers: Iterable<TransferRow>,
    adjustments: Iterable<AccountAdjustmentRow>,
    untilInclusive: Long = Long.MAX_VALUE,
): Long {
    if (untilInclusive < account.createdAt) return 0
    val transactionDelta = transactions
        .filter { it.accountId == account.id && it.occurredAt <= untilInclusive }
        .sumOf { if (it.type == MoneyType.INCOME) it.amountCents else -it.amountCents }
    val transferDelta = transfers
        .filter { it.occurredAt <= untilInclusive }
        .sumOf {
            when (account.id) {
                it.toAccountId -> it.amountCents
                it.fromAccountId -> -it.amountCents
                else -> 0L
            }
        }
    val adjustmentDelta = adjustments
        .filter { it.accountId == account.id && it.occurredAt <= untilInclusive }
        .sumOf { it.deltaCents }
    return account.openingBalanceCents + transactionDelta + transferDelta + adjustmentDelta
}

fun totalAssets(
    accounts: Iterable<AccountEntity>,
    transactions: Iterable<TransactionRow>,
    transfers: Iterable<TransferRow>,
    adjustments: Iterable<AccountAdjustmentRow>,
    untilInclusive: Long = Long.MAX_VALUE,
): Long = accounts.filter { it.includeInTotal }
    .sumOf { accountBalance(it, transactions, transfers, adjustments, untilInclusive) }
