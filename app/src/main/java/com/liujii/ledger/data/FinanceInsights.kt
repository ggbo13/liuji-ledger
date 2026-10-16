package com.liujii.ledger.data

import java.time.LocalDate
import java.time.YearMonth

enum class BudgetPace(val label: String) { NORMAL("支出节奏正常"), FAST("支出节奏偏快"), OVER("已超支") }

fun budgetPace(expense: Long, limit: Long, today: LocalDate): BudgetPace {
    require(limit > 0)
    if (expense > limit) return BudgetPace.OVER
    val elapsed = today.dayOfMonth.toDouble() / YearMonth.from(today).lengthOfMonth()
    return if (expense.toDouble() / limit > elapsed * 1.2) BudgetPace.FAST else BudgetPace.NORMAL
}

fun periodChange(current: Long, previous: Long): String = when {
    previous == 0L && current == 0L -> "与上期持平"
    previous == 0L -> "上期为 0"
    current == previous -> "与上期持平"
    else -> "较上期 " + String.format(java.util.Locale.CHINA, "%+.1f%%", (current - previous).toDouble() * 100 / previous)
}

// 所有校验均在替换数据库之前完成，避免不完整备份破坏当前数据。
fun validateBackup(payload: BackupPayload) {
    fun ids(values: List<Long>) = values.all { it > 0 } && values.distinct().size == values.size
    require(payload.version in 1..3) { "不支持的备份版本" }
    require(ids(payload.categories.map { it.id }) && ids(payload.accounts.map { it.id }) && ids(payload.paymentMethods.map { it.id })) { "分类、账本或收支方式标识无效" }
    require(ids(payload.transactions.map { it.id }) && ids(payload.transfers.map { it.id }) && ids(payload.adjustments.map { it.id }) && ids(payload.budgets.map { it.id }) && ids(payload.recurringRules.map { it.id })) { "备份含重复或无效标识" }
    require(payload.categories.all { it.name.isNotBlank() } && payload.accounts.all { it.name.isNotBlank() } && payload.paymentMethods.all { it.name.isNotBlank() }) { "名称不能为空" }
    val categories = payload.categories.associateBy { it.id }
    val accounts = payload.accounts.map { it.id }.toSet()
    val methods = payload.paymentMethods.map { it.id }.toSet()
    require(payload.accounts.any { it.active } && payload.paymentMethods.any { it.active }) { "备份缺少可用账本或收支方式" }
    require(payload.transactions.all { it.amountCents > 0 && categories[it.categoryId]?.type == it.type && it.accountId in accounts && it.paymentMethodId in methods }) { "账目关联数据无效" }
    require(payload.budgets.all { it.limitCents > 0 && runCatching { YearMonth.parse(it.yearMonth) }.isSuccess && (it.categoryId == null || categories[it.categoryId]?.type == MoneyType.EXPENSE) }) { "预算数据无效" }
    require(payload.budgets.distinctBy { it.yearMonth to it.categoryId }.size == payload.budgets.size) { "同一范围存在重复预算" }
    require(payload.recurringRules.all {
        it.title.isNotBlank() && it.amountCents > 0 && categories[it.categoryId]?.type == it.type && it.accountId in accounts && it.paymentMethodId in methods &&
            it.nextAt >= it.startAt && (it.endAt == null || it.endAt >= it.startAt) && it.remindDaysBefore in 0..30 && it.remindHour in 0..23 && it.remindMinute in 0..59
    }) { "周期账单时间或关联数据无效" }
    require(payload.recurringRules.filter{it.isEffective()}.all { rule ->
        categories[rule.categoryId]?.active==true&&payload.accounts.any{it.id==rule.accountId&&it.active}&&
            payload.paymentMethods.any{it.id==rule.paymentMethodId&&it.active}
    }) { "有效周期账单引用了已停用的分类、账本或收支方式，请先在来源账本修正" }
    require(payload.transfers.all { it.amountCents > 0 && it.fromAccountId in accounts && it.toAccountId in accounts && it.fromAccountId != it.toAccountId }) { "转账数据无效" }
    require(payload.adjustments.all { it.accountId in accounts }) { "校准数据无效" }
}

data class TransactionDraft(
    val type: MoneyType, val amount: String, val categoryId: Long, val accountId: Long,
    val paymentMethodId: Long, val date: String, val note: String, val isCopy: Boolean = false,
)
