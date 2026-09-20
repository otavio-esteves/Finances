package br.com.otavioesteves.finances.presentation.dashboard

import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.presentation.transactions.TransactionItem

data class DashboardUiState(
    val monthPeriod: MonthPeriod,
    val monthlyBalance: Money,
    val categorySummaries: List<CategorySummary> = emptyList(),
    val recentTransactions: List<TransactionItem> = emptyList()
)
