package com.liujii.ledger.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal enum class ChartGestureAxis { UNDECIDED, HORIZONTAL, VERTICAL }

internal fun chartIndexAt(x: Float, width: Float, pointCount: Int): Int? {
    if (pointCount <= 0 || !width.isFinite() || width <= 0f || !x.isFinite()) return null
    if (pointCount == 1) return 0
    return (x.coerceIn(0f, width) / width * (pointCount - 1)).roundToInt().coerceIn(0, pointCount - 1)
}

internal fun chartAxisAfterSlop(
    axis: ChartGestureAxis,
    deltaX: Float,
    deltaY: Float,
    touchSlop: Float,
): ChartGestureAxis {
    // 方向一经确定就保持到本次手势结束，纵向交给页面滚动。
    if (axis != ChartGestureAxis.UNDECIDED) return axis
    if (!deltaX.isFinite() || !deltaY.isFinite()) return axis
    if (max(abs(deltaX), abs(deltaY)) <= touchSlop.coerceAtLeast(0f)) return axis
    return if (abs(deltaX) > abs(deltaY)) ChartGestureAxis.HORIZONTAL else ChartGestureAxis.VERTICAL
}
