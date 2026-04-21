package br.com.otavioesteves.finances.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.data.repository.CategoriesRepositoryImpl
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.usecase.GetCategorySummariesUseCase
import br.com.otavioesteves.finances.ui.state.CategoriesUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class CategoriesViewModel(
    private val getCategorySummaries: GetCategorySummariesUseCase =
        GetCategorySummariesUseCase(CategoriesRepositoryImpl())
) : ViewModel() {

    private val _uiState = MutableStateFlow<CategoriesUiState>(CategoriesUiState.Loading)
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()
    private val selectedPeriod = MonthPeriod(year = 2026, month = 1)

    init {
        fetchCategories()
    }

    private fun fetchCategories() {
        viewModelScope.launch {
            getCategorySummaries(selectedPeriod)
                .onStart {
                    _uiState.value = CategoriesUiState.Loading
                }
                .catch { exception ->
                    _uiState.value = CategoriesUiState.Error(exception.message ?: "Erro desconhecido")
                }
                .collect { categories ->
                    _uiState.value = CategoriesUiState.Success(
                        monthPeriod = selectedPeriod,
                        categories = categories
                    )
                }
        }
    }
}
