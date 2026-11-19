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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModel(
    private val getCategorySummaries: GetCategorySummariesUseCase,
    dateProvider: DateProvider,
    private val categoriesRepository: CategoriesRepository,
    private val monthSelection: SelectedMonthState = SelectedMonthState(dateProvider.getCurrentMonthPeriod())
) : ViewModel() {

    val selectedMonthPeriod: StateFlow<MonthPeriod> = monthSelection.period
    private val reloadRequests = MutableStateFlow(0L)
    private var lastMonthlyState: CategoriesUiState = CategoriesUiState.Loading
    val uiState: StateFlow<CategoriesUiState> = combine(selectedMonthPeriod, reloadRequests) { period, _ -> period }
        .flatMapLatest { period ->
            combine(getCategorySummaries(period), categoriesRepository.getCategories()) { totals, categories ->
                val amounts = totals.associate { it.category.id to it.totalAmount }
                val state: CategoriesUiState = if (categories.isEmpty()) CategoriesUiState.Empty(period)
                else CategoriesUiState.Success(period, categories.map { CategorySummary(it, amounts[it.id] ?: Money.Zero) })
                state.also { lastMonthlyState = it }
            }.onStart {
                // Preserve cached rows and scroll position while the same month refreshes on return.
                val loadedPeriod = when (val state = lastMonthlyState) {
                    is CategoriesUiState.Success -> state.monthPeriod
                    is CategoriesUiState.Empty -> state.monthPeriod
                    else -> null
                }
                if (loadedPeriod != period) emit(CategoriesUiState.Loading)
            }.catch { error ->
                if (error is CancellationException) throw error
                emit(CategoriesUiState.Error(period, error.message ?: "Erro desconhecido").also { lastMonthlyState = it })
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0), CategoriesUiState.Loading)

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

    fun onEvent(event: CategoriesEvent) {
        when (event) {
            is CategoriesEvent.OnMonthChanged -> {
                monthSelection.select(event.monthPeriod)
            }

            is CategoriesEvent.OnCategoryClicked -> Unit

            CategoriesEvent.OnRetryClicked -> {
                reloadRequests.update { it + 1 }
            }
        }
    }

}


data class CategoryCreationState(
    val isOpen: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)
