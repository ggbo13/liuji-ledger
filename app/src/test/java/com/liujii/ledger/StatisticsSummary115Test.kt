package com.liujii.ledger

import com.liujii.ledger.data.MoneyType
import com.liujii.ledger.data.TransactionRow
import com.liujii.ledger.data.statisticsChartSeries
import com.liujii.ledger.data.statisticsSummary
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsSummary115Test {
    private val zone = ZoneId.of("Asia/Taipei")
    private val start = LocalDate.of(2026, 10, 1)

    private fun row(id: Long, date: LocalDate, type: MoneyType, amount: Long, category: Long = 1): TransactionRow =
        TransactionRow(id, type, amount, category, 1, date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            "测试账目", null, "分类", "", 0xFFE76F64, "日常", 1, "现金", 0xFF4D8BB8)

    @Test fun summaryIncludesBothBoundaryDaysAndSortsExpenseCategories() {
        val rows = listOf(
            row(1, start.minusDays(1), MoneyType.EXPENSE, 900),
            row(2, start, MoneyType.INCOME, 1_000),
            row(3, start, MoneyType.EXPENSE, 200),
            row(4, start.plusDays(1), MoneyType.EXPENSE, 300, 2),
            row(5, start.plusDays(2), MoneyType.EXPENSE, 50),
            row(6, start.plusDays(3), MoneyType.INCOME, 8_000),
        )
        val summary = statisticsSummary(rows, start, start.plusDays(2), zone)
        assertEquals(listOf(2L, 3L, 4L, 5L), summary.rows.map { it.id })
        assertEquals(1_000L, summary.incomeCents)
        assertEquals(550L, summary.expenseCents)
        assertEquals(listOf(2L to 300L, 1L to 250L), summary.categoryExpenses)
    }

    @Test fun timeSeriesAddsSameDayAmountsAndFillsMissingDaysWithZero() {
        val rows = listOf(
            row(1, start, MoneyType.INCOME, 1_000),
            row(2, start, MoneyType.INCOME, 250),
            row(3, start.plusDays(2), MoneyType.EXPENSE, 300),
            row(4, start.minusDays(1), MoneyType.INCOME, 9_999),
        )
        val series = statisticsChartSeries(rows, start, start.plusDays(2), zone)
        assertEquals(listOf(start, start.plusDays(1), start.plusDays(2)), series.dates)
        assertEquals(listOf(1_250L, 0L, 0L), series.income)
        assertEquals(listOf(0L, 0L, 300L), series.expense)
        assertEquals(1_250L, series.maxAmount)
    }

    @Test fun emptySingleDayAndInvalidRangeAreSafe() {
        val oneDay = statisticsChartSeries(emptyList(), start, start, zone)
        assertEquals(listOf(start), oneDay.dates)
        assertEquals(listOf(0L), oneDay.income)
        assertEquals(listOf(0L), oneDay.expense)
        assertEquals(1L, oneDay.maxAmount)
        val invalid = statisticsChartSeries(listOf(row(1, start, MoneyType.EXPENSE, 1)), start, start.minusDays(1), zone)
        assertTrue(invalid.dates.isEmpty())
        assertTrue(invalid.income.isEmpty())
        assertTrue(statisticsSummary(emptyList(), start, start, zone).rows.isEmpty())
    }

    @Test fun monthTotalsRetainFutureEntriesWhileDailyTrailStopsToday() {
        val today = start.plusDays(13)
        val rows = listOf(row(1, today, MoneyType.EXPENSE, 200), row(2, today.plusDays(1), MoneyType.INCOME, 500))
        val month = statisticsSummary(rows, start, start.withDayOfMonth(31), zone)
        val currentPeriod = statisticsSummary(rows, start, today, zone)
        val trail = statisticsChartSeries(month.rows, start, today, zone)
        assertEquals(500L, month.incomeCents)
        assertEquals(0L, currentPeriod.incomeCents)
        assertEquals(14, trail.dates.size)
        assertEquals(0L, trail.income.sum())
        assertEquals(200L, trail.expense.sum())
    }

    @Test fun changingZoneReassignsEntryAtLocalDayBoundary() {
        val entry = row(1, start, MoneyType.INCOME, 100).copy(
            occurredAt = java.time.Instant.parse("2026-09-30T20:00:00Z").toEpochMilli())
        assertEquals(100L, statisticsSummary(listOf(entry), start, start, zone).incomeCents)
        assertEquals(0L, statisticsSummary(listOf(entry), start, start, ZoneId.of("UTC")).incomeCents)
    }
}
