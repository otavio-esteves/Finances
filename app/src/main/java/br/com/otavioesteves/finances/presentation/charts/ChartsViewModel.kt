package br.com.otavioesteves.finances.presentation.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import br.com.otavioesteves.finances.domain.usecase.GetCategorySummariesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class ChartsUiState(
    val monthPeriod: MonthPeriod,
    val categorySummaries: List<CategorySummary> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val annualExpenses: List<Long> = List(12) { 0L },
    val categoryAnnualExpenses: List<Long> = List(12) { 0L },
    val isLoading: Boolean = true
)

internal fun annualExpenseTotals(transactions: List<Transaction>, year: Int, categoryId: Long? = null): List<Long> {
    val totals = LongArray(12)
    transactions.forEach { transaction ->
        if (transaction.date.year == year && transaction.type == TransactionType.EXPENSE &&
            (categoryId == null || transaction.categoryId == categoryId)
        ) {
            val index = transaction.date.monthValue - 1
            totals[index] = Math.addExact(totals[index], transaction.amount.cents)
        }
    }
    return totals.toList()
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChartsViewModel(
    private val getCategorySummaries: GetCategorySummariesUseCase,
    private val transactionsRepository: TransactionsRepository,
    private val categoriesRepository: CategoriesRepository,
    dateProvider: DateProvider
) : ViewModel() {
    private val selectedMonth = MutableStateFlow(dateProvider.getCurrentMonthPeriod())
    private val selectedCategory = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<ChartsUiState> = selectedMonth.flatMapLatest { period ->
        combine(
            getCategorySummaries(period),
            transactionsRepository.getTransactionsForYear(period.year),
            categoriesRepository.getCategories(),
            selectedCategory
        ) { summaries, transactions, categories, categoryId ->
            val expenseCategories = categories.filter { it.type == CategoryType.EXPENSE }
            val effectiveCategoryId = categoryId?.takeIf { id -> expenseCategories.any { it.id == id } }
                ?: expenseCategories.firstOrNull()?.id
            ChartsUiState(
                monthPeriod = period,
                categorySummaries = summaries,
                categories = expenseCategories,
                selectedCategoryId = effectiveCategoryId,
                annualExpenses = annualExpenseTotals(transactions, period.year),
                categoryAnnualExpenses = effectiveCategoryId?.let {
                    annualExpenseTotals(transactions, period.year, it)
                } ?: List(12) { 0L },
                isLoading = false
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChartsUiState(monthPeriod = selectedMonth.value)
    )

    fun onMonthSelected(period: MonthPeriod) {
        selectedMonth.value = period
    }

    fun onCategorySelected(categoryId: Long) {
        selectedCategory.value = categoryId
    }
}
