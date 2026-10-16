package com.liujii.ledger

import com.liujii.ledger.data.CategoryEntity
import com.liujii.ledger.data.MoneyType
import com.liujii.ledger.data.fallbackCategoryId
import com.liujii.ledger.data.orderedCategoryChoices
import org.junit.Assert.assertEquals
import org.junit.Test

class CategorySelection115Test {
    private fun category(
        id: Long,
        order: Int,
        active: Boolean = true,
        type: MoneyType = MoneyType.EXPENSE,
    ) = CategoryEntity(id, "分类$id", type, "类", 0xFFE76F64, order, active)

    private val categories = listOf(
        category(30, 2), category(9, 0), category(4, 0),
        category(2, -1, active = false),
        category(1, -2, type = MoneyType.INCOME),
    )

    @Test fun manualOrderUsesIdToBreakTiesAndDoesNotMutateInput() {
        val original = categories.toList()
        assertEquals(listOf(4L, 9L, 30L), orderedCategoryChoices(categories, MoneyType.EXPENSE).map { it.id })
        assertEquals(original, categories)
    }

    @Test fun shuffledOrRecentlyUsedInputCannotChangeManualOrder() {
        for (input in listOf(categories, categories.reversed(), categories.drop(1) + categories.first())) {
            assertEquals(listOf(4L, 9L, 30L), orderedCategoryChoices(input, MoneyType.EXPENSE).map { it.id })
        }
    }

    @Test fun validSelectionSurvivesManualReordering() {
        assertEquals(30L, fallbackCategoryId(categories, MoneyType.EXPENSE, 30))
        val reordered = categories.map { if (it.id == 9L) it.copy(sortOrder = -10) else it }
        assertEquals(30L, fallbackCategoryId(reordered, MoneyType.EXPENSE, 30))
    }

    @Test fun newOrMissingSelectionFallsBackToFirstEnabledInManualOrder() {
        for (selected in listOf(0L, 999L, 2L, 1L)) {
            assertEquals(4L, fallbackCategoryId(categories, MoneyType.EXPENSE, selected))
        }
    }

    @Test fun editingKeepsOnlyTheOriginalInactiveCategoryInItsManualPosition() {
        val withOtherInactive = categories + category(3, -5, active = false)
        assertEquals(listOf(2L, 4L, 9L, 30L), orderedCategoryChoices(withOtherInactive, MoneyType.EXPENSE, 2).map { it.id })
        assertEquals(2L, fallbackCategoryId(withOtherInactive, MoneyType.EXPENSE, 2, 2))
        assertEquals(4L, fallbackCategoryId(withOtherInactive, MoneyType.EXPENSE, 3, 2))
    }

    @Test fun inactiveHistoricalCategoryIsNeverAFallbackTarget() {
        assertEquals(4L, fallbackCategoryId(categories, MoneyType.EXPENSE, 999, 2))
        assertEquals(0L, fallbackCategoryId(listOf(category(2, 0, active = false)), MoneyType.EXPENSE, 999, 2))
    }

    @Test fun copyingDropsHistoricalExceptionButRetainsAnEnabledChoice() {
        assertEquals(listOf(4L, 9L, 30L), orderedCategoryChoices(categories, MoneyType.EXPENSE).map { it.id })
        assertEquals(4L, fallbackCategoryId(categories, MoneyType.EXPENSE, 2))
        assertEquals(30L, fallbackCategoryId(categories, MoneyType.EXPENSE, 30))
    }

    @Test fun typeSwitchNeverKeepsASelectionFromTheOtherType() {
        assertEquals(1L, fallbackCategoryId(categories, MoneyType.INCOME, 4, 2))
        assertEquals(listOf(1L), orderedCategoryChoices(categories, MoneyType.INCOME, 2).map { it.id })
    }

    @Test fun disablingOrRemovingTheSelectedCategoryReevaluatesFallback() {
        assertEquals(9L, fallbackCategoryId(categories.map { if (it.id == 4L) it.copy(active = false) else it }, MoneyType.EXPENSE, 4))
        assertEquals(9L, fallbackCategoryId(categories.filterNot { it.id == 4L }, MoneyType.EXPENSE, 4))
    }

    @Test fun emptyTypeAndAllInactiveNewChoicesReturnZero() {
        assertEquals(emptyList<CategoryEntity>(), orderedCategoryChoices(emptyList(), MoneyType.EXPENSE))
        assertEquals(0L, fallbackCategoryId(emptyList(), MoneyType.EXPENSE, 20))
        assertEquals(0L, fallbackCategoryId(listOf(category(1, 0, type = MoneyType.INCOME)), MoneyType.EXPENSE, 1))
        assertEquals(0L, fallbackCategoryId(listOf(category(2, 0, active = false)), MoneyType.EXPENSE, 2))
    }

    @Test fun historyWithoutEnabledChoicesCanBeKeptButCannotBeCopied() {
        val history = listOf(category(2, 0, active = false))
        assertEquals(2L, fallbackCategoryId(history, MoneyType.EXPENSE, 2, 2))
        assertEquals(0L, fallbackCategoryId(history, MoneyType.EXPENSE, 2))
    }
}
