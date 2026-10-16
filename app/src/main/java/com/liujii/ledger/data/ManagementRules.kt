package com.liujii.ledger.data

fun nextManagedOrder(orders:List<Int>):Int=Math.addExact(orders.maxOrNull()?:-1,1)

fun RecurringRuleEntity.isEffective():Boolean=active&&(endAt==null||nextAt<=endAt)

fun requireNoRecurringDependency(rule:RecurringRuleEntity?,label:String) {
    require(rule==null){"$label 正被周期账单“${rule?.title}”使用，请先暂停或修改该账单"}
}

fun requireCategoryTypeAllowed(original:CategoryEntity,type:MoneyType,referenced:Boolean) {
    require(!referenced||original.type==type){"已有账目或周期账单引用此分类，不能改变收支类型"}
}
