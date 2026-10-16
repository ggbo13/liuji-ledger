package com.liujii.ledger

import com.liujii.ledger.data.AccountEntity
import com.liujii.ledger.data.BackupPayload
import com.liujii.ledger.data.CategoryEntity
import com.liujii.ledger.data.MoneyType
import com.liujii.ledger.data.PaymentMethodEntity
import com.liujii.ledger.data.RecurringRuleEntity
import com.liujii.ledger.data.RepeatType
import com.liujii.ledger.data.isEffective
import com.liujii.ledger.data.nextManagedOrder
import com.liujii.ledger.data.requireCategoryTypeAllowed
import com.liujii.ledger.data.requireNoRecurringDependency
import com.liujii.ledger.data.validateBackup
import org.junit.Assert.*
import org.junit.Test

class ManagementRulesTest {
    private fun rule(id: Long = 1, active: Boolean = true, nextAt: Long = 1_000, endAt: Long? = null) =
        RecurringRuleEntity(id = id, title = "餐费$id", type = MoneyType.EXPENSE,
            amountCents = 1_000, categoryId = 1, accountId = 1, paymentMethodId = 1,
            repeatType = RepeatType.MONTHLY, startAt = 500, nextAt = nextAt,
            active = active, endAt = endAt)

    private fun backup(dependency: RecurringRuleEntity = rule()) = BackupPayload(
        version = 3,
        accounts = listOf(AccountEntity(id = 1, name = "日常"), AccountEntity(id = 2, name = "备用账本")),
        categories = listOf(
            CategoryEntity(id = 1, name = "餐饮", type = MoneyType.EXPENSE, icon = "餐", color = 0xFFE76F64),
            CategoryEntity(id = 2, name = "交通", type = MoneyType.EXPENSE, icon = "行", color = 0xFF4D8BB8),
        ),
        transactions = emptyList(), transfers = emptyList(), adjustments = emptyList(), budgets = emptyList(),
        recurringRules = listOf(dependency),
        paymentMethods = listOf(
            PaymentMethodEntity(id = 1, name = "现金", color = 0xFF4D8BB8),
            PaymentMethodEntity(id = 2, name = "银行卡", color = 0xFF9A78C6),
        ),
    )

    private fun assertInactiveDependencyRejected(payload: BackupPayload) {
        // 保留另一个启用账本与方式，确保命中有效规则关联校验，而非缺少可用项校验。
        assertTrue(payload.accounts.any { it.id != 1L && it.active })
        assertTrue(payload.paymentMethods.any { it.id != 1L && it.active })
        validateBackup(payload.copy(recurringRules = emptyList()))
        val error = assertThrows(IllegalArgumentException::class.java) { validateBackup(payload) }
        assertEquals("有效周期账单引用了已停用的分类、账本或收支方式，请先在来源账本修正", error.message)
    }

    private fun inactiveReferenceBackups(dependency: RecurringRuleEntity): List<BackupPayload> {
        val original = backup(dependency)
        val categories = original.categories.map { if (it.id == 1L) it.copy(active = false) else it }
        val accounts = original.accounts.map { if (it.id == 1L) it.copy(active = false) else it }
        val methods = original.paymentMethods.map { if (it.id == 1L) it.copy(active = false) else it }
        return listOf(original.copy(categories = categories), original.copy(accounts = accounts),
            original.copy(paymentMethods = methods),
            original.copy(categories = categories, accounts = accounts, paymentMethods = methods))
    }

    @Test fun nextOrderStartsAtZeroForEmptyInput() {
        assertEquals(0, nextManagedOrder(emptyList()))
    }

    @Test fun nextOrderUsesMaximumDespiteGapsDuplicatesAndInputOrder() {
        val orders = mutableListOf(9, 2, 9, 0)
        val original = orders.toList()
        assertEquals(10, nextManagedOrder(orders))
        assertEquals(10, nextManagedOrder(orders.reversed()))
        assertEquals(original, orders)
    }

    @Test fun nextOrderHandlesNegativeOrdersAndLargestRepresentableResult() {
        assertEquals(-2, nextManagedOrder(listOf(-7, -3)))
        assertEquals(Int.MIN_VALUE + 1, nextManagedOrder(listOf(Int.MIN_VALUE)))
        assertEquals(Int.MAX_VALUE, nextManagedOrder(listOf(0, Int.MAX_VALUE - 1)))
    }

    @Test fun nextOrderRejectsOverflowInsteadOfWrapping() {
        assertThrows(ArithmeticException::class.java) { nextManagedOrder(listOf(Int.MAX_VALUE)) }
    }

    @Test fun activeRuleWithoutEndDateIsEffective() {
        assertTrue(rule().isEffective())
        assertTrue(rule(nextAt = Long.MAX_VALUE).isEffective())
    }

    @Test fun effectiveRuleIncludesEndBoundaryAndExcludesNextPostingAfterEnd() {
        assertTrue(rule(nextAt = 999, endAt = 1_000).isEffective())
        assertTrue(rule(nextAt = 1_000, endAt = 1_000).isEffective())
        assertFalse(rule(nextAt = 1_001, endAt = 1_000).isEffective())
    }

    @Test fun pausedRuleIsNeverEffective() {
        for (endAt in listOf<Long?>(null, 1_000, 999)) {
            assertFalse(rule(active = false, endAt = endAt).isEffective())
        }
    }

    @Test fun effectiveRulesFilterKeepsOnlyActiveUnexpiredRulesWithoutMutation() {
        // 实际接口是 isEffective，管理依赖与提醒恢复均使用此过滤条件。
        val rules = listOf(rule(1), rule(2, endAt = 1_000),
            rule(3, active = false), rule(4, endAt = 999))
        val original = rules.toList()
        assertEquals(listOf(1L, 2L), rules.filter { it.isEffective() }.map { it.id })
        assertEquals(original, rules)
    }

    @Test fun missingRecurringDependencyAllowsEveryManagedItem() {
        for (label in listOf("分类", "账本", "收支方式")) {
            requireNoRecurringDependency(null, label)
        }
    }

    @Test fun recurringDependencyErrorIdentifiesManagedItemAndRuleTitle() {
        val dependency = rule()
        for (label in listOf("分类", "账本", "收支方式")) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                requireNoRecurringDependency(dependency, label)
            }
            assertEquals("$label 正被周期账单“${dependency.title}”使用，请先暂停或修改该账单", error.message)
        }
    }

    @Test fun referencedCategoryAllowsSameTypeButRejectsBothTypeChanges() {
        for (type in MoneyType.entries) {
            for (active in listOf(true, false)) {
                val original = CategoryEntity(id = 1, name = "分类", type = type,
                    icon = "类", color = 0xFFE76F64, active = active)
                requireCategoryTypeAllowed(original, type, referenced = true)
                val otherType = if (type == MoneyType.EXPENSE) MoneyType.INCOME else MoneyType.EXPENSE
                val error = assertThrows(IllegalArgumentException::class.java) {
                    requireCategoryTypeAllowed(original, otherType, referenced = true)
                }
                assertEquals("已有账目或周期账单引用此分类，不能改变收支类型", error.message)
            }
        }
    }

    @Test fun unreferencedCategoryAllowsTypeChangeEvenWhenInactive() {
        for (type in MoneyType.entries) {
            val original = CategoryEntity(name = "未使用分类", type = type,
                icon = "类", color = 0xFFE76F64, active = false)
            for (newType in MoneyType.entries) {
                requireCategoryTypeAllowed(original, newType, referenced = false)
            }
        }
    }

    @Test fun backupRejectsEffectiveRuleReferencingInactiveCategory() {
        val original = backup()
        validateBackup(original)
        assertInactiveDependencyRejected(original.copy(categories = original.categories.map {
            if (it.id == 1L) it.copy(active = false) else it
        }))
    }

    @Test fun backupRejectsEffectiveRuleReferencingInactiveBookDespiteAnotherActiveBook() {
        val original = backup()
        validateBackup(original)
        assertInactiveDependencyRejected(original.copy(accounts = original.accounts.map {
            if (it.id == 1L) it.copy(active = false) else it
        }))
    }

    @Test fun backupRejectsEffectiveRuleReferencingInactiveMethodDespiteAnotherActiveMethod() {
        val original = backup()
        validateBackup(original)
        assertInactiveDependencyRejected(original.copy(paymentMethods = original.paymentMethods.map {
            if (it.id == 1L) it.copy(active = false) else it
        }))
    }

    @Test fun backupAllowsPausedRuleReferencingInactiveManagedItems() {
        for (payload in inactiveReferenceBackups(rule(active = false))) {
            validateBackup(payload)
        }
    }

    @Test fun backupAllowsEndedRuleReferencingInactiveManagedItems() {
        for (payload in inactiveReferenceBackups(rule(endAt = 999))) {
            validateBackup(payload)
        }
    }
}
