package br.com.otavioesteves.finances.presentation.dashboard

import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.presentation.transactions.TransactionItem

data class DashboardUiState(
    val monthPeriod: MonthPeriod,
    val monthlyBalance: Money,
    val categorySummaries: List<CategorySummary> = emptyList(),
    val recentTransactions: List<TransactionItem> = emptyList(),
    val comparisons: List<br.com.otavioesteves.finances.domain.model.ExpenseComparison> = emptyList(),
    val hasAnyTransactions: Boolean? = null
) {
    val monthlyIncome: Money get() = totalFor(CategoryType.INCOME)
    val monthlyExpenses: Money get() = totalFor(CategoryType.EXPENSE)

    private fun totalFor(type: CategoryType): Money = categorySummaries.filter { it.category.type == type }
        .fold(Money.Zero) { total, summary -> total + summary.totalAmount }
}
