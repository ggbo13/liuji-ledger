package com.liujii.ledger

import com.liujii.ledger.data.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FinanceMathTest {
    @Test fun `汇总收入支出与结余`() {
        val rows = listOf(
            TransactionEntity(type=MoneyType.INCOME,amountCents=10_000,categoryId=1,accountId=1,occurredAt=0),
            TransactionEntity(type=MoneyType.EXPENSE,amountCents=2_350,categoryId=2,accountId=1,occurredAt=0),
        )
        assertEquals(MoneySummary(10_000, 2_350), summarize(rows))
        assertEquals(7_650, summarize(rows).balanceCents)
    }

    @Test fun `每周规则推进七天`() = assertEquals(LocalDate.of(2026,9,15), nextOccurrence(LocalDate.of(2026,9,8), RepeatType.WEEKLY))

    @Test fun `每月规则正确处理月末`() = assertEquals(LocalDate.of(2026,2,28), nextOccurrence(LocalDate.of(2026,1,31), RepeatType.MONTHLY))

    @Test fun `金额格式为人民币两位小数`() = assertEquals("¥12.34", com.liujii.ledger.ui.money(1234).replace("￥","¥"))

    @Test fun `日历金额始终保留两位小数`() {
        assertEquals("17.00", com.liujii.ledger.ui.formatCalendarAmount(1_700))
        assertEquals("9.94", com.liujii.ledger.ui.formatCalendarAmount(994))
    }

    @Test fun `拖动排序只更新当前收支类型并生成连续顺序`() {
        val categories = listOf(
            CategoryEntity(1, "餐饮", MoneyType.EXPENSE, "", 0, 8),
            CategoryEntity(2, "交通", MoneyType.EXPENSE, "", 0, 99),
            CategoryEntity(3, "工资", MoneyType.INCOME, "", 0, 3),
            CategoryEntity(4, "已停用", MoneyType.EXPENSE, "", 0, 1, false),
        )

        val result = normalizeCategoryOrder(categories, MoneyType.EXPENSE, listOf(2, 1, 2, 3, 4))

        assertEquals(listOf(2L, 1L), result.map { it.id })
        assertEquals(listOf(0, 1), result.map { it.sortOrder })
    }

    @Test fun `账户余额包含收支转账和校准`() {
        val account=AccountEntity(id=1,name="银行卡",openingBalanceCents=10_000,createdAt=0)
        val transactions=listOf(
            TransactionRow(1,MoneyType.INCOME,5_000,1,1,100,"",null,"工资","",0,"",1,"现金",0),
            TransactionRow(2,MoneyType.EXPENSE,2_000,1,1,200,"",null,"餐饮","",0,"",1,"现金",0),
        )
        val transfers=listOf(
            TransferRow(1,1,2,1_000,300,"","银行卡","现金"),
            TransferRow(2,2,1,500,400,"","现金","银行卡"),
        )
        val adjustments=listOf(AccountAdjustmentRow(1,1,-300,500,"校准","银行卡"))
        assertEquals(12_200,accountBalance(account,transactions,transfers,adjustments))
    }

    @Test fun `转账不改变总资产`() {
        val accounts=listOf(AccountEntity(id=1,name="A",openingBalanceCents=10_000),AccountEntity(id=2,name="B",openingBalanceCents=2_000))
        val transfers=listOf(TransferRow(1,1,2,3_000,100,"","A","B"))
        assertEquals(12_000,totalAssets(accounts,emptyList(),transfers,emptyList()))
    }

    @Test fun `账户创建前不计入历史资产`() {
        val account=AccountEntity(id=1,name="现金",openingBalanceCents=1_000,createdAt=1_000)
        assertEquals(0,accountBalance(account,emptyList(),emptyList(),emptyList(),999))
        assertEquals(1_000,accountBalance(account,emptyList(),emptyList(),emptyList(),1_000))
    }
}
