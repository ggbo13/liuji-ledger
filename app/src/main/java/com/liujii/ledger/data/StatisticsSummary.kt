package com.liujii.ledger.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class StatisticsSummary(
    val rows: List<TransactionRow>,
    val incomeCents: Long,
    val expenseCents: Long,
    val categoryExpenses: List<Pair<Long, Long>>,
)

internal fun statisticsSummary(
    transactions: List<TransactionRow>,
    start: LocalDate,
    end: LocalDate,
    zone: ZoneId,
): StatisticsSummary {
    val rows = mutableListOf<TransactionRow>()
    var income = 0L
    var expense = 0L
    val categories = linkedMapOf<Long, Long>()
    transactions.forEach { row ->
        val date = Instant.ofEpochMilli(row.occurredAt).atZone(zone).toLocalDate()
        if (date in start..end) {
            rows.add(row)
            if (row.type == MoneyType.INCOME) income += row.amountCents
            else {
                expense += row.amountCents
                categories[row.categoryId] = (categories[row.categoryId] ?: 0L) + row.amountCents
            }
        }
    }
    return StatisticsSummary(rows, income, expense, categories.toList().sortedByDescending { it.second })
}

internal data class StatisticsChartSeries(
    val dates: List<LocalDate>,
    val income: List<Long>,
    val expense: List<Long>,
    val maxAmount: Long,
)

internal fun statisticsChartSeries(
    rows: List<TransactionRow>,
    start: LocalDate,
    end: LocalDate,
    zone: ZoneId,
): StatisticsChartSeries {
    val dates = if (end < start) emptyList() else generateSequence(start) { if (it < end) it.plusDays(1) else null }.toList()
    val income = LongArray(dates.size)
    val expense = LongArray(dates.size)
    rows.forEach { row ->
        val date = Instant.ofEpochMilli(row.occurredAt).atZone(zone).toLocalDate()
        val index = date.toEpochDay() - start.toEpochDay()
        if (index >= 0 && index < dates.size) {
            if (row.type == MoneyType.INCOME) income[index.toInt()] += row.amountCents
            else expense[index.toInt()] += row.amountCents
        }
    }
    val maxAmount = maxOf(income.maxOrNull() ?: 0L, expense.maxOrNull() ?: 0L).coerceAtLeast(1L)
    return StatisticsChartSeries(dates, income.toList(), expense.toList(), maxAmount)
}
