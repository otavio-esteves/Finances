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
import br.com.otavioesteves.finances.domain.usecase.EXAMPLE_TRANSACTIONS_NOTE
import br.com.otavioesteves.finances.domain.usecase.exampleTransactions2026
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChartsUiState(
    val monthPeriod: MonthPeriod,
    val categorySummaries: List<CategorySummary> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val annualExpenses: List<Long> = List(12) { 0L },
    val categoryAnnualExpenses: List<Long> = List(12) { 0L },
    val exampleCount: Int = 0,
    val isLoading: Boolean = true
)

data class ExampleActionState(val isBusy: Boolean = false, val error: String? = null)

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
    private val dateProvider: DateProvider
) : ViewModel() {
    private val selectedMonth = MutableStateFlow(dateProvider.getCurrentMonthPeriod())
    private val selectedCategory = MutableStateFlow<Long?>(null)
    private val _exampleAction = MutableStateFlow(ExampleActionState())
    val exampleAction = _exampleAction.asStateFlow()

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
                exampleCount = transactions.count { it.notes == EXAMPLE_TRANSACTIONS_NOTE },
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

    fun addExamples() {
        if (_exampleAction.value.isBusy) return
        _exampleAction.value = ExampleActionState(isBusy = true)
        viewModelScope.launch {
            try {
                val categories = categoriesRepository.getCategories().first { it.isNotEmpty() }
                val samples = exampleTransactions2026(categories, dateProvider.getCurrentDate())
                val existing = transactionsRepository.getTransactionsForYear(2026).first()
                    .filter { it.notes == EXAMPLE_TRANSACTIONS_NOTE }
                    .map { it.date to it.description }.toSet()
                samples.filterNot { (it.date to it.description) in existing }
                    .forEach { transactionsRepository.addTransaction(it) }
                _exampleAction.value = ExampleActionState()
            } catch (error: Exception) {
                _exampleAction.value = ExampleActionState(error = error.message ?: "Não foi possível adicionar os exemplos.")
            }
        }
    }

    fun removeExamples() {
        if (_exampleAction.value.isBusy) return
        _exampleAction.value = ExampleActionState(isBusy = true)
        viewModelScope.launch {
            try {
                transactionsRepository.getTransactionsForYear(2026).first()
                    .filter { it.notes == EXAMPLE_TRANSACTIONS_NOTE }
                    .forEach { transactionsRepository.removeTransaction(it.id) }
                _exampleAction.value = ExampleActionState()
            } catch (error: Exception) {
                _exampleAction.value = ExampleActionState(error = error.message ?: "Não foi possível remover os exemplos.")
            }
        }
    }
}
