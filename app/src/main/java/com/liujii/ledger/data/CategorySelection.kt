package com.liujii.ledger.data

// 手动顺序相同的分类再按编号排列，不受最近使用记录或传入列表顺序影响。
fun orderedCategoryChoices(
    categories: List<CategoryEntity>,
    type: MoneyType,
    historicalCategoryId: Long? = null,
): List<CategoryEntity> = categories
    .filter { it.type == type && (it.active || it.id == historicalCategoryId) }
    .sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.id })

// 历史停用项只能保留已选中的原分类；回退目标始终是首个启用项，没有则返回 0。
fun fallbackCategoryId(
    categories: List<CategoryEntity>,
    type: MoneyType,
    selectedId: Long,
    historicalCategoryId: Long? = null,
): Long {
    val choices = orderedCategoryChoices(categories, type, historicalCategoryId)
    return choices.firstOrNull { it.id == selectedId }?.id
        ?: choices.firstOrNull { it.active }?.id
        ?: 0L
}
