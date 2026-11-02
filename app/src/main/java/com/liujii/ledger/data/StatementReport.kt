package com.liujii.ledger.data

import java.time.LocalDate

internal enum class StatementScope {
    MONTH,
    YEAR,
}

internal data class StatementDateRange(
    val start: LocalDate,
    val end: LocalDate,
)

internal fun statementDateRange(scope: StatementScope, anchor: LocalDate): StatementDateRange =
    when (scope) {
        StatementScope.MONTH -> StatementDateRange(
            start = anchor.withDayOfMonth(1),
            end = anchor.withDayOfMonth(anchor.lengthOfMonth()),
        )
        StatementScope.YEAR -> StatementDateRange(
            start = LocalDate.of(anchor.year, 1, 1),
            end = LocalDate.of(anchor.year, 12, 31),
        )
    }
