package br.com.otavioesteves.finances.presentation.addtransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.usecase.AddTransactionUseCase
import br.com.otavioesteves.finances.utils.MoneyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class AddTransactionViewModel(
    private val addTransactionUseCase: AddTransactionUseCase,
    private val categoriesRepository: CategoriesRepository,
    dateProvider: DateProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState(date = dateProvider.getCurrentDate()))
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            categoriesRepository.getCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
    }

    fun onDescriptionChange(description: String) {
        _uiState.update { it.copy(description = description, descriptionError = null) }
    }

    fun onAmountChange(amount: String) {
        _uiState.update { it.copy(amount = amount, amountError = null) }
    }

    fun onTypeChange(type: TransactionType) {
        _uiState.update { it.copy(type = type) }
    }

    fun onCategoryChange(category: Category) {
        _uiState.update { it.copy(selectedCategory = category, categoryError = null) }
    }

    fun onDateChange(date: LocalDate) {
        _uiState.update { it.copy(date = date, dateError = null) }
    }

    fun onNotesChange(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun saveTransaction() {
        val currentState = _uiState.value
        var hasError = false

        // Validate Description
        if (currentState.description.isBlank()) {
            _uiState.update { it.copy(descriptionError = "Descrição é obrigatória") }
            hasError = true
        }

        // Validate Amount
        val parsedAmount = MoneyFormatter.parse(currentState.amount)
        if (parsedAmount == null || !parsedAmount.isPositive()) {
            _uiState.update { it.copy(amountError = "Valor deve ser maior que zero") }
            hasError = true
        }

        // Validate Category
        if (currentState.selectedCategory == null) {
            _uiState.update { it.copy(categoryError = "Categoria é obrigatória") }
            hasError = true
        }

        // Wait, date is practically never null in this UI state since it's LocalDate
        // But if needed we can validate
        
        if (hasError) return

        viewModelScope.launch {
            val newTransaction = Transaction(
                id = 0, // Let repository/database generate
                description = currentState.description.trim(),
                amount = parsedAmount!!,
                categoryId = currentState.selectedCategory!!.id,
                date = currentState.date,
                type = currentState.type,
                notes = currentState.notes.trim().takeIf { it.isNotEmpty() }
            )
            addTransactionUseCase(newTransaction)
            _uiState.update { it.copy(isSaveSuccessful = true) }
        }
    }
}
