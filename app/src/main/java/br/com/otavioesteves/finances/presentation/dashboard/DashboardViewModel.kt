package br.com.otavioesteves.finances.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.data.repository.sumMoney
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.usecase.GetMonthlyBalanceUseCase
import br.com.otavioesteves.finances.domain.usecase.GetTransactionsByMonthUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(
    private val getMonthlyBalance: GetMonthlyBalanceUseCase,
    private val getTransactionsByMonth: GetTransactionsByMonthUseCase
) : ViewModel() {

    private val selectedMonthPeriod = MonthPeriod(year = 2026, month = 1)

    val uiState: StateFlow<DashboardUiState> = combine(
        getMonthlyBalance(selectedMonthPeriod),
        getTransactionsByMonth(selectedMonthPeriod)
    ) { monthlyBalance, transactions ->
        DashboardUiState(
            monthPeriod = selectedMonthPeriod,
            monthlyBalance = monthlyBalance,
            totalIncome = transactions
                .filter { transaction -> transaction.type == TransactionType.INCOME }
                .map { transaction -> transaction.amount }
                .sumMoney(),
            totalExpenses = transactions
                .filter { transaction -> transaction.type == TransactionType.EXPENSE }
                .map { transaction -> transaction.amount }
                .sumMoney()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(
            monthPeriod = selectedMonthPeriod,
            monthlyBalance = Money.Zero,
            totalIncome = Money.Zero,
            totalExpenses = Money.Zero
        )
    )
}
