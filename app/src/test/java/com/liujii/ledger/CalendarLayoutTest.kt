package com.liujii.ledger

import com.liujii.ledger.ui.calendarWeeks
import com.liujii.ledger.ui.formatCalendarAmount
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class CalendarLayoutTest {
    @Test fun everyMonthHasSevenColumnsAndEachDateAppearsExactlyOnce() {
        for(year in 2000..2035)for(monthNumber in 1..12) {
            val month=YearMonth.of(year,monthNumber)
            val weeks=calendarWeeks(month)
            assertTrue(weeks.size in 4..6)
            assertTrue(weeks.all{it.size==7})
            assertEquals((1..month.lengthOfMonth()).map{month.atDay(it)},weeks.flatten().filterNotNull())
            weeks.forEach{week->week.forEachIndexed{column,date->if(date!=null)assertEquals(column+1,date.dayOfWeek.value)}}
        }
    }

    @Test fun screenshotMonthKeepsAllSundaysInSeventhColumn() {
        val weeks=calendarWeeks(YearMonth.of(2026,10))
        assertEquals(listOf(4,11,18,25,null),weeks.map{it[6]?.dayOfMonth})
        assertEquals(31,weeks.last()[5]?.dayOfMonth)
    }

    @Test fun sixWeekMonthDoesNotLoseLastDays() {
        val month=YearMonth.of(2026,3)
        val weeks=calendarWeeks(month)
        assertEquals(6,weeks.size)
        assertEquals(month.atDay(1),weeks.first()[6])
        assertEquals(month.atDay(31),weeks.last()[1])
    }

    @Test fun leapFebruaryIncludesTwentyNinthAndFourWeekFebruaryFits() {
        assertEquals(29,calendarWeeks(YearMonth.of(2028,2)).flatten().filterNotNull().size)
        assertEquals(4,calendarWeeks(YearMonth.of(2027,2)).size)
    }

    @Test fun smallAmountsKeepCentsAndLargeAmountsKeepTwoDecimals() {
        assertEquals("0.01",formatCalendarAmount(1))
        assertEquals("15.00",formatCalendarAmount(1500))
        assertEquals("125.49",formatCalendarAmount(12549))
        assertEquals("999.99",formatCalendarAmount(99999))
        assertEquals("1.00千",formatCalendarAmount(100000))
        assertEquals("1.00万",formatCalendarAmount(1000000))
        assertEquals("1.00亿",formatCalendarAmount(10000000000))
    }
}
