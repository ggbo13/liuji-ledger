package com.liujii.ledger

import com.liujii.ledger.data.ChartGestureAxis
import com.liujii.ledger.data.chartAxisAfterSlop
import com.liujii.ledger.data.chartIndexAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartInteraction115Test {
    @Test fun emptyAndUnmeasuredChartHaveNoSelection() {
        assertNull(chartIndexAt(10f, 100f, 0))
        assertNull(chartIndexAt(10f, 100f, -1))
        assertNull(chartIndexAt(10f, 0f, 3))
        assertNull(chartIndexAt(10f, -1f, 3))
        assertNull(chartIndexAt(Float.NaN, 100f, 3))
        assertNull(chartIndexAt(10f, Float.POSITIVE_INFINITY, 3))
    }

    @Test fun singleDayAlwaysSelectsItsOnlyPoint() {
        assertEquals(0, chartIndexAt(-100f, 100f, 1))
        assertEquals(0, chartIndexAt(50f, 100f, 1))
        assertEquals(0, chartIndexAt(200f, 100f, 1))
    }

    @Test fun selectsNearestDayAndClampsBothEnds() {
        assertEquals(0, chartIndexAt(-10f, 100f, 5))
        assertEquals(0, chartIndexAt(0f, 100f, 5))
        assertEquals(1, chartIndexAt(24f, 100f, 5))
        assertEquals(2, chartIndexAt(50f, 100f, 5))
        assertEquals(4, chartIndexAt(100f, 100f, 5))
        assertEquals(4, chartIndexAt(200f, 100f, 5))
    }

    @Test fun directionWaitsUntilSlopIsExceeded() {
        assertEquals(ChartGestureAxis.UNDECIDED, chartAxisAfterSlop(ChartGestureAxis.UNDECIDED, 8f, -8f, 8f))
        assertEquals(ChartGestureAxis.HORIZONTAL, chartAxisAfterSlop(ChartGestureAxis.UNDECIDED, -9f, 2f, 8f))
        assertEquals(ChartGestureAxis.VERTICAL, chartAxisAfterSlop(ChartGestureAxis.UNDECIDED, 2f, -9f, 8f))
        assertEquals(ChartGestureAxis.VERTICAL, chartAxisAfterSlop(ChartGestureAxis.UNDECIDED, 9f, 9f, 8f))
    }

    @Test fun horizontalAndVerticalLocksCannotReverseDuringGesture() {
        val vertical = chartAxisAfterSlop(ChartGestureAxis.UNDECIDED, 1f, 20f, 8f)
        assertEquals(ChartGestureAxis.VERTICAL, chartAxisAfterSlop(vertical, 200f, 0f, 8f))
        val horizontal = chartAxisAfterSlop(ChartGestureAxis.UNDECIDED, 20f, 1f, 8f)
        assertEquals(ChartGestureAxis.HORIZONTAL, chartAxisAfterSlop(horizontal, 0f, 200f, 8f))
    }
}
