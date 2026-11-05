package br.com.otavioesteves.finances.presentation.categories

import androidx.lifecycle.ViewModel
import br.com.otavioesteves.finances.domain.state.SelectedMonthState
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CancellationException
import br.com.otavioesteves.finances.domain.usecase.GetCategorySummariesUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class CategoriesViewModel(
    private val getCategorySummaries: GetCategorySummariesUseCase,
    dateProvider: DateProvider,
    private val categoriesRepository: CategoriesRepository,
    private val monthSelection: SelectedMonthState = SelectedMonthState(dateProvider.getCurrentMonthPeriod())
) : ViewModel() {

    val selectedMonthPeriod: StateFlow<MonthPeriod> = monthSelection.period
    private val _uiState = MutableStateFlow<CategoriesUiState>(CategoriesUiState.Loading)
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private val _creationState = MutableStateFlow(CategoryCreationState())
    val creationState: StateFlow<CategoryCreationState> = _creationState.asStateFlow()

    fun showCreateCategory() { _creationState.value = CategoryCreationState(isOpen = true) }
    fun dismissCreateCategory() {
        if (!_creationState.value.isSaving) _creationState.value = CategoryCreationState()
    }
    fun createCategory(name: String, type: CategoryType) {
        if (_creationState.value.isSaving) return
        val normalized = name.trim().replace(Regex("\\s+"), " ")
        if (normalized.isBlank() || normalized.length > 80) {
            _creationState.value = CategoryCreationState(isOpen = true, error = "Informe um nome com até 80 caracteres.")
            return
        }
        _creationState.value = CategoryCreationState(isOpen = true, isSaving = true)
        viewModelScope.launch {
            try {
                categoriesRepository.createCategory(normalized, type)
                _creationState.value = CategoryCreationState()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _creationState.value = CategoryCreationState(isOpen = true, error = e.message ?: "Não foi possível criar a categoria.")
            }
        }
    }

    init {
        viewModelScope.launch {
            selectedMonthPeriod.collect { loadCategorySummaries(it) }
        }
    }

    fun onEvent(event: CategoriesEvent) {
        when (event) {
            is CategoriesEvent.OnMonthChanged -> {
                monthSelection.select(event.monthPeriod)
            }

            is CategoriesEvent.OnCategoryClicked -> Unit

            CategoriesEvent.OnRetryClicked -> {
                loadCategorySummaries(selectedMonthPeriod.value)
            }
        }
    }

    private fun loadCategorySummaries(period: MonthPeriod) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val summaries = getCategorySummaries(period)
            val rows = combine(summaries, categoriesRepository.getCategories()) { totals, categories ->
                val amounts = totals.associate { it.category.id to it.totalAmount }
                categories.map { CategorySummary(it, amounts[it.id] ?: Money.Zero) }
            }
            rows.onStart {
                    _uiState.value = CategoriesUiState.Loading
                }
                .catch { exception ->
                    _uiState.value = CategoriesUiState.Error(
                        monthPeriod = period,
                        message = exception.message ?: "Erro desconhecido"
                    )
                }
                .collect { categories ->
                    _uiState.value = if (categories.isEmpty()) {
                        CategoriesUiState.Empty(monthPeriod = period)
                    } else {
                        CategoriesUiState.Success(
                            monthPeriod = period,
                            categories = categories
                        )
                    }
                }
        }
    }
}


data class CategoryCreationState(
    val isOpen: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)
