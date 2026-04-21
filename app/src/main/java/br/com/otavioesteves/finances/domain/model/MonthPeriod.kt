package br.com.otavioesteves.finances.domain.model

data class MonthPeriod(
    val year: Int,
    val month: Int
) {
    init {
        require(month in 1..12) { "month must be between 1 and 12" }
    }
}
