package br.com.otavioesteves.finances.presentation.dashboard

import androidx.lifecycle.ViewModel
import br.com.otavioesteves.finances.domain.state.SelectedMonthState
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.usecase.GetCategorySummariesUseCase
import br.com.otavioesteves.finances.domain.usecase.GetMonthlyBalanceUseCase
import br.com.otavioesteves.finances.domain.usecase.GetTransactionsByMonthUseCase
import br.com.otavioesteves.finances.presentation.transactions.TransactionItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

private const val RECENT_TRANSACTIONS_LIMIT = 6

class DashboardViewModel(
    private val getMonthlyBalance: GetMonthlyBalanceUseCase,
    private val getTransactionsByMonth: GetTransactionsByMonthUseCase,
    private val getCategorySummaries: GetCategorySummariesUseCase,
    private val categoriesRepository: CategoriesRepository,
    dateProvider: DateProvider,
    private val selectedMonth: SelectedMonthState = SelectedMonthState(dateProvider.getCurrentMonthPeriod())
) : ViewModel() {

    private val _selectedMonthPeriod = selectedMonth.period
    val selectedMonthPeriod: StateFlow<MonthPeriod> = _selectedMonthPeriod

    val uiState: StateFlow<DashboardUiState> = _selectedMonthPeriod.flatMapLatest { period ->
        combine(
            getMonthlyBalance(period),
            getTransactionsByMonth(period, RECENT_TRANSACTIONS_LIMIT),
            getCategorySummaries(period),
            categoriesRepository.getCategories(),
            if (period.year == 1 && period.month == 1) kotlinx.coroutines.flow.flowOf(emptyList())
            else getCategorySummaries(period.previousMonth())
        ) { monthlyBalance, transactions, categorySummaries, categories, previousSummaries ->
            val categoryMap = categories.associateBy { it.id }
            DashboardUiState(
                monthPeriod = period,
                monthlyBalance = monthlyBalance,
                categorySummaries = categorySummaries,
                comparisons = br.com.otavioesteves.finances.domain.model.compareExpenses(categorySummaries, previousSummaries),
                recentTransactions = transactions
                    .map { transaction ->
                        TransactionItem(transaction = transaction, category = categoryMap[transaction.categoryId])
                    }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(
            monthPeriod = _selectedMonthPeriod.value,
            monthlyBalance = Money.Zero
        )
    )

    fun onMonthSelected(period: MonthPeriod) {
        selectedMonth.select(period)
    }
}
