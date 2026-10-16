package com.liujii.ledger

import com.liujii.ledger.data.parseMoneyCents
import org.junit.Assert.*
import org.junit.Test

class MoneyInputTest {
    @Test fun preservesCents() {
        assertEquals(1999L, parseMoneyCents("19.99"))
        assertEquals(100L, parseMoneyCents("1"))
        assertEquals(1L, parseMoneyCents("0.01"))
    }
    @Test fun rejectsExcessPrecisionAndOverflowWithoutThrowing() {
        assertNull(parseMoneyCents("1.999"))
        assertNull(parseMoneyCents("9223372036854775808"))
        assertNull(parseMoneyCents("1..2"))
        assertNull(parseMoneyCents(""))
    }
}
