package br.com.otavioesteves.finances.presentation.categories

import androidx.lifecycle.ViewModel
import br.com.otavioesteves.finances.domain.state.SelectedMonthState
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.usecase.GetTransactionsByMonthUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher

data class CategoryDetailsUiState(
    val period: MonthPeriod,
    val category: Category? = null,
    val transactions: List<Transaction> = emptyList(),
    val total: Money = Money.Zero,
    val isLoading: Boolean = true,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryDetailsViewModel(
    categoryId: Long,
    period: MonthPeriod,
    categoriesRepository: CategoriesRepository,
    getTransactionsByMonth: GetTransactionsByMonthUseCase,
    computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    monthSelection: SelectedMonthState = SelectedMonthState(period)
) : ViewModel() {
    val uiState = monthSelection.period.flatMapLatest { selectedPeriod ->
        combine(categoriesRepository.getCategories(), getTransactionsByMonth(selectedPeriod)) { categories, transactions ->
            val category = categories.firstOrNull { it.id == categoryId }
            val rows = transactions.filter { it.categoryId == categoryId }
            CategoryDetailsUiState(
                period = selectedPeriod,
                category = category,
                transactions = rows,
                total = rows.fold(Money.Zero) { total, transaction -> total + transaction.amount },
                isLoading = false
            )
        }
    }.catch { emit(CategoryDetailsUiState(monthSelection.period.value, isLoading = false, error = "Não foi possível carregar os lançamentos.")) }
        .flowOn(computationDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryDetailsUiState(monthSelection.period.value))
}
