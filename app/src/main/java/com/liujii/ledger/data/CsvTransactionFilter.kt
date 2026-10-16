package com.liujii.ledger.data

import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// 系统文件选择器可能导致进程重建，因此保存筛选条件，不保存尚未加载的列表。
data class CsvTransactionFilter(
    val search: String = "",
    val type: MoneyType? = null,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val paymentMethodId: Long? = null,
    val startDate: String? = null,
    val endDate: String? = null,
) : Serializable {
    fun select(rows: List<TransactionRow>, zone: ZoneId = ZoneId.systemDefault()): List<TransactionRow> {
        val start=startDate?.let(LocalDate::parse)
        val end=(endDate?:startDate)?.let(LocalDate::parse)
        return rows.filter { row ->
            val day=Instant.ofEpochMilli(row.occurredAt).atZone(zone).toLocalDate()
            (search.isBlank()||listOf(row.note,row.categoryName,row.accountName,row.paymentMethodName).any{it.contains(search,true)})&&
                (type==null||row.type==type)&&(categoryId==null||row.categoryId==categoryId)&&
                (accountId==null||row.accountId==accountId)&&(paymentMethodId==null||row.paymentMethodId==paymentMethodId)&&
                (start==null||day>=start)&&(end==null||day<=end)
        }
    }
}
