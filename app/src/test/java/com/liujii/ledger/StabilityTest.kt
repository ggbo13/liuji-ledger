package com.liujii.ledger

import com.liujii.ledger.data.*
import com.liujii.ledger.ui.sanitizeMoneyInput
import com.liujii.ledger.ui.formatCalendarAmount
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StabilityTest {
    private val valid=BackupPayload(3,
        listOf(AccountEntity(id=1,name="日常")),
        listOf(CategoryEntity(id=1,name="餐饮",type=MoneyType.EXPENSE,icon="餐",color=0xFFE76F64)),
        listOf(TransactionEntity(id=1,type=MoneyType.EXPENSE,amountCents=1234,categoryId=1,accountId=1,occurredAt=1000)),
        emptyList(),emptyList(),listOf(BudgetEntity(id=1,yearMonth="2026-10",limitCents=10000)),emptyList(),
        listOf(PaymentMethodEntity(id=1,name="现金",color=0xFF4D8BB8)))

    @Test fun moneyInputKeepsOneDecimalPointAndTwoDecimalPlaces() {
        assertEquals("12.34",sanitizeMoneyInput("12.3.456"))
        assertEquals("0.09",sanitizeMoneyInput(".09"))
        assertEquals("12.50",sanitizeMoneyInput("¥12．50"))
        assertEquals("",sanitizeMoneyInput("文本"))
    }

    @Test fun calendarShowsExactCentsAndCompactUnits() {
        assertEquals("16.58",formatCalendarAmount(1658))
        assertEquals("9.94",formatCalendarAmount(994))
        assertEquals("1.23千",formatCalendarAmount(123456))
        assertEquals("1.23万",formatCalendarAmount(1234567))
    }

    @Test fun budgetPaceUsesCalendarDaysAndDetectsOverspending() {
        assertEquals(BudgetPace.NORMAL,budgetPace(5000,10000,LocalDate.of(2026,10,20)))
        assertEquals(BudgetPace.FAST,budgetPace(5000,10000,LocalDate.of(2026,10,3)))
        assertEquals(BudgetPace.OVER,budgetPace(10001,10000,LocalDate.of(2026,10,31)))
    }

    @Test fun comparisonsHandleZeroAndReduction() {
        assertEquals("与上期持平",periodChange(0,0))
        assertEquals("上期为 0",periodChange(100,0))
        assertEquals("较上期 -50.0%",periodChange(100,200))
    }

    @Test fun backupValidationRejectsDuplicateTransactionsBudgetsAndBadLinks() {
        validateBackup(valid)
        assertThrows(IllegalArgumentException::class.java){validateBackup(valid.copy(transactions=valid.transactions+valid.transactions))}
        assertThrows(IllegalArgumentException::class.java){validateBackup(valid.copy(budgets=valid.budgets+valid.budgets.map{it.copy(id=2)}))}
        assertThrows(IllegalArgumentException::class.java){validateBackup(valid.copy(transactions=valid.transactions.map{it.copy(categoryId=999)}))}
        assertThrows(IllegalArgumentException::class.java){validateBackup(valid.copy(budgets=valid.budgets.map{it.copy(yearMonth="bad")}))}
    }

    @Test fun backupValidationRejectsInvalidReminderTimeAndDates() {
        val rule=RecurringRuleEntity(id=1,title="餐费",type=MoneyType.EXPENSE,amountCents=1000,categoryId=1,accountId=1,repeatType=RepeatType.MONTHLY,startAt=1000,nextAt=2000)
        validateBackup(valid.copy(recurringRules=listOf(rule)))
        assertThrows(IllegalArgumentException::class.java){validateBackup(valid.copy(recurringRules=listOf(rule.copy(remindHour=25))))}
        assertThrows(IllegalArgumentException::class.java){validateBackup(valid.copy(recurringRules=listOf(rule.copy(endAt=500))))}
    }
}
