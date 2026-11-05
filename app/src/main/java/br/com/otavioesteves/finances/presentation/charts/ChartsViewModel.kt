package br.com.otavioesteves.finances.presentation.charts

import androidx.lifecycle.ViewModel
import br.com.otavioesteves.finances.domain.state.SelectedMonthState
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.summarizeAnnualTransactions
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import br.com.otavioesteves.finances.domain.usecase.GetCategorySummariesUseCase
import br.com.otavioesteves.finances.domain.usecase.EXAMPLE_TRANSACTIONS_NOTE
import br.com.otavioesteves.finances.domain.usecase.exampleTransactions2026
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
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
    val summary = summarizeAnnualTransactions(transactions, year, trackedNote = null)
    return if (categoryId == null) summary.monthlyExpenses
        else summary.categoryMonthlyExpenses[categoryId] ?: List(12) { 0L }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChartsViewModel(
    private val getCategorySummaries: GetCategorySummariesUseCase,
    private val transactionsRepository: TransactionsRepository,
    private val categoriesRepository: CategoriesRepository,
    private val dateProvider: DateProvider,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val monthSelection: SelectedMonthState = SelectedMonthState(dateProvider.getCurrentMonthPeriod())
) : ViewModel() {
    val selectedMonthPeriod = monthSelection.period
    private val selectedMonth = selectedMonthPeriod
    private val selectedCategory = MutableStateFlow<Long?>(null)
    private val _exampleAction = MutableStateFlow(ExampleActionState())
    val exampleAction = _exampleAction.asStateFlow()

    // Month and category changes reuse the same compact annual data while the year stays selected.
    private val annualSummary = selectedMonth.map { it.year }.distinctUntilChanged()
        .flatMapLatest { year -> transactionsRepository.getAnnualSummary(year, EXAMPLE_TRANSACTIONS_NOTE) }
        .flowOn(computationDispatcher)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val expenseCategories = categoriesRepository.getCategories()
        .map { categories -> categories.filter { it.type == CategoryType.EXPENSE } }
        .distinctUntilChanged()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val uiState: StateFlow<ChartsUiState> = selectedMonth.flatMapLatest { period ->
        combine(
            getCategorySummaries(period),
            annualSummary,
            expenseCategories,
            selectedCategory
        ) { summaries, annual, categories, categoryId ->
            if (annual.year != period.year) return@combine ChartsUiState(monthPeriod = period)
            val effectiveCategoryId = categoryId?.takeIf { id -> categories.any { it.id == id } }
                ?: categories.firstOrNull()?.id
            ChartsUiState(
                monthPeriod = period,
                categorySummaries = summaries,
                categories = categories,
                selectedCategoryId = effectiveCategoryId,
                annualExpenses = annual.monthlyExpenses,
                categoryAnnualExpenses = effectiveCategoryId?.let { annual.categoryMonthlyExpenses[it] } ?: List(12) { 0L },
                exampleCount = annual.trackedNoteCount,
                isLoading = false
            )
        }
    }.flowOn(computationDispatcher).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChartsUiState(monthPeriod = selectedMonth.value)
    )

    fun onMonthSelected(period: MonthPeriod) {
        monthSelection.select(period)
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
            } catch (error: CancellationException) {
                throw error
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
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _exampleAction.value = ExampleActionState(error = error.message ?: "Não foi possível remover os exemplos.")
            }
        }
    }
}
