package br.com.otavioesteves.finances.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.MonthPeriod
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
    dateProvider: DateProvider
) : ViewModel() {

    private val initialMonthPeriod = dateProvider.getCurrentMonthPeriod()
    private val _uiState = MutableStateFlow<CategoriesUiState>(CategoriesUiState.Loading)
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    private var selectedMonthPeriod: MonthPeriod = initialMonthPeriod
    private var loadJob: Job? = null

    init {
        loadCategorySummaries(selectedMonthPeriod)
    }

    fun onEvent(event: CategoriesEvent) {
        when (event) {
            is CategoriesEvent.OnMonthChanged -> {
                if (event.monthPeriod != selectedMonthPeriod) {
                    selectedMonthPeriod = event.monthPeriod
                    loadCategorySummaries(selectedMonthPeriod)
                }
            }

            is CategoriesEvent.OnCategoryClicked -> Unit

            CategoriesEvent.OnRetryClicked -> {
                loadCategorySummaries(selectedMonthPeriod)
            }
        }
    }

    private fun loadCategorySummaries(period: MonthPeriod) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            getCategorySummaries(period)
                .onStart {
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
