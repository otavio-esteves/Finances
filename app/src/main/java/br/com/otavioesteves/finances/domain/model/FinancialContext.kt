package br.com.otavioesteves.finances.domain.model

data class FinancialContext(
    val period: MonthPeriod,
    val monthlyBalance: Money,
    val categorySummaries: List<CategorySummary>
)
