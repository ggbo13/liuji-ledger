package com.liujii.ledger

import com.liujii.ledger.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class CsvFilter115Test {
    private val zone=ZoneId.of("Asia/Taipei")
    private fun row(id:Long,type:MoneyType=MoneyType.EXPENSE,day:Int=1)=TransactionRow(
        id,type,479,1,1,java.time.LocalDate.of(2026,10,day).atTime(12,0).atZone(zone).toInstant().toEpochMilli(),
        "棉签",null,"日常","",0xFFE76F64,"生活",1,"微信",0xFF2FAE83)
    private val rows=listOf(row(1),row(2,MoneyType.INCOME,2),row(3,day=3).copy(categoryId=2,accountId=2,paymentMethodId=2,categoryName="餐饮",accountName="工作",paymentMethodName="支付宝",note="Lunch"))

    @Test fun defaultFilterKeepsAllRowsAndTheirOrder() {
        assertEquals(rows,CsvTransactionFilter().select(rows,zone))
        assertTrue(CsvTransactionFilter().select(emptyList(),zone).isEmpty())
    }

    @Test fun allFilterDimensionsIntersectWithoutMutatingRows() {
        val before=rows.toList()
        assertEquals(listOf(rows[2]),CsvTransactionFilter("lunch",MoneyType.EXPENSE,2,2,2,"2026-10-01","2026-10-03").select(rows,zone))
        assertTrue(CsvTransactionFilter(type=MoneyType.INCOME,categoryId=2).select(rows,zone).isEmpty())
        assertEquals(before,rows)
    }

    @Test fun searchMatchesNotesCategoriesBooksAndMethods() {
        for(text in listOf("LUNCH","餐饮","工作","支付宝"))assertEquals(listOf(rows[2]),CsvTransactionFilter(search=text).select(rows,zone))
        assertEquals(listOf(rows[0],rows[1]),CsvTransactionFilter(search="微信").select(rows,zone))
    }

    @Test fun datesAreInclusiveAndSingleDateMeansOnlyThatDay() {
        assertEquals(listOf(rows[0],rows[1]),CsvTransactionFilter(startDate="2026-10-01",endDate="2026-10-02").select(rows,zone))
        assertEquals(listOf(rows[1]),CsvTransactionFilter(startDate="2026-10-02").select(rows,zone))
    }

    @Test fun dateFilteringUsesTheLocalBoundary() {
        val boundary=rows[0].copy(occurredAt=Instant.parse("2026-09-30T20:00:00Z").toEpochMilli())
        val filter=CsvTransactionFilter(startDate="2026-10-01")
        assertEquals(listOf(boundary),filter.select(listOf(boundary),zone))
        assertTrue(filter.select(listOf(boundary),ZoneId.of("UTC")).isEmpty())
    }

    @Test fun selectorRequestSurvivesSerializationAndQueriesFreshRows() {
        // 模拟选择文件前保存条件、界面重建后数据库才提供真实账目。
        val filter=CsvTransactionFilter(type=MoneyType.EXPENSE,accountId=2,startDate="2026-10-03")
        val bytes=ByteArrayOutputStream().also{ObjectOutputStream(it).use{stream->stream.writeObject(filter)}}.toByteArray()
        val restored=ObjectInputStream(ByteArrayInputStream(bytes)).use{it.readObject() as CsvTransactionFilter}
        assertEquals(filter,restored)
        assertEquals(listOf(rows[2]),restored.select(rows,zone))
    }
}
