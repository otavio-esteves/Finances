package br.com.otavioesteves.finances.domain.model

import java.time.YearMonth

data class MonthPeriod(
    val year: Int,
    val month: Int
) {
    init {
        require(year in 1..9_999) { "year must be between 1 and 9999" }
        require(month in 1..12) { "month must be between 1 and 12" }
        YearMonth.of(year, month)
    }

    fun toYearMonth(): YearMonth = YearMonth.of(year, month)

    companion object {
        fun from(yearMonth: YearMonth): MonthPeriod {
            return MonthPeriod(
                year = yearMonth.year,
                month = yearMonth.monthValue
            )
        }
    }
}
