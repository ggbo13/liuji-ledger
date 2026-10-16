package com.liujii.ledger

import com.liujii.ledger.ui.currentDateAt
import com.liujii.ledger.ui.millisUntilNextLocalMidnight
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentDate115Test {
    @Test fun sameInstantUsesLocalDateInEachZone() {
        val now = Instant.parse("2026-10-14T17:00:00Z")
        assertEquals(LocalDate.of(2026, 10, 15), currentDateAt(now, ZoneId.of("Asia/Taipei")))
        assertEquals(LocalDate.of(2026, 10, 14), currentDateAt(now, ZoneId.of("America/Los_Angeles")))
    }

    @Test fun midnightDelayCrossesMonthAndYear() {
        val now = Instant.parse("2026-12-31T15:59:59.250Z")
        val zone = ZoneId.of("Asia/Taipei")
        assertEquals(750L, millisUntilNextLocalMidnight(now, zone))
        assertEquals(LocalDate.of(2027, 1, 1), currentDateAt(now.plusMillis(750), zone))
        assertEquals(86_400_000L, millisUntilNextLocalMidnight(now.plusMillis(750), zone))
    }

    @Test fun daylightSavingDaysHaveTwentyThreeOrTwentyFiveHours() {
        val zone = ZoneId.of("America/New_York")
        val spring = LocalDate.of(2026, 3, 8).atStartOfDay(zone).toInstant()
        val autumn = LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant()
        assertEquals(23 * 3_600_000L, millisUntilNextLocalMidnight(spring, zone))
        assertEquals(25 * 3_600_000L, millisUntilNextLocalMidnight(autumn, zone))
    }

    @Test fun subMillisecondBeforeMidnightStillHasPositiveDelay() {
        assertEquals(1L, millisUntilNextLocalMidnight(Instant.parse("2026-10-14T23:59:59.999999999Z"), ZoneId.of("UTC")))
    }

    @Test fun midnightGapUsesActualStartOfFollowingDay() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val now = LocalDate.of(2018, 11, 3).atTime(23, 30).atZone(zone).toInstant()
        assertEquals(30 * 60_000L, millisUntilNextLocalMidnight(now, zone))
        assertEquals(LocalDate.of(2018, 11, 4), currentDateAt(now.plusSeconds(30 * 60), zone))
    }
}
