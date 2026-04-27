package br.com.otavioesteves.finances.presentation.dashboard

import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Money

data class DashboardUiState(
    val monthPeriod: MonthPeriod,
    val monthlyBalance: Money,
    val totalIncome: Money,
    val totalExpenses: Money
)
