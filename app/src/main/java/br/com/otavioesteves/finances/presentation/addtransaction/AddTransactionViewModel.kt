package br.com.otavioesteves.finances.presentation.addtransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.usecase.AddTransactionUseCase
import br.com.otavioesteves.finances.domain.usecase.DeleteTransactionUseCase
import br.com.otavioesteves.finances.domain.usecase.GetTransactionUseCase
import br.com.otavioesteves.finances.domain.usecase.UpdateTransactionUseCase
import br.com.otavioesteves.finances.utils.MoneyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class AddTransactionViewModel(
    private val addTransactionUseCase: AddTransactionUseCase,
    private val updateTransactionUseCase: UpdateTransactionUseCase,
    private val getTransactionUseCase: GetTransactionUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    private val categoriesRepository: CategoriesRepository,
    dateProvider: DateProvider,
    private val transactionId: Long? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState(date = dateProvider.getCurrentDate()))
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val categories = categoriesRepository.getCategories().first()
            _uiState.update { it.copy(categories = categories) }
            
            if (transactionId != null && transactionId > 0) {
                loadTransaction(transactionId)
            }
        }
    }

    private suspend fun loadTransaction(id: Long) {
        _uiState.update { it.copy(isLoading = true) }
        
        val currentDate = _uiState.value.date
        val period = MonthPeriod(currentDate.year, currentDate.monthValue)
        
        val transaction = getTransactionUseCase(id, period).first()
        
        if (transaction != null) {
            _uiState.update { state ->
                state.copy(
                    description = transaction.description,
                    amount = (transaction.amount.cents.toDouble() / 100).toString().replace('.', ','),
                    type = transaction.type,
                    selectedCategory = state.categories.find { it.id == transaction.categoryId },
                    date = transaction.date,
                    notes = transaction.notes ?: "",
                    isLoading = false
                )
            }
        } else {
            _uiState.update { it.copy(isLoading = false, error = "Transação não encontrada") }
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

    fun onDeleteRequest() {
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    fun onDeleteCancel() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun onDeleteConfirm() {
        val id = transactionId ?: return
        _uiState.update { it.copy(showDeleteConfirmation = false, isLoading = true) }
        viewModelScope.launch {
            try {
                deleteTransactionUseCase(id)
                _uiState.update { it.copy(isSaveSuccessful = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Erro ao excluir transação") }
            }
        }
    }

    fun saveTransaction() {
        val currentState = _uiState.value
        var hasError = false

        if (currentState.description.isBlank()) {
            _uiState.update { it.copy(descriptionError = "Descrição é obrigatória") }
            hasError = true
        }

        val parsedAmount = MoneyFormatter.parse(currentState.amount)
        if (parsedAmount == null || !parsedAmount.isPositive()) {
            _uiState.update { it.copy(amountError = "Valor deve ser maior que zero") }
            hasError = true
        }

        if (currentState.selectedCategory == null) {
            _uiState.update { it.copy(categoryError = "Categoria é obrigatória") }
            hasError = true
        }
        
        if (hasError) return

        viewModelScope.launch {
            try {
                val transaction = Transaction(
                    id = transactionId ?: 0,
                    description = currentState.description.trim(),
                    amount = parsedAmount!!,
                    categoryId = currentState.selectedCategory!!.id,
                    date = currentState.date,
                    type = currentState.type,
                    notes = currentState.notes.trim().takeIf { it.isNotEmpty() }
                )
                
                if (transactionId != null && transactionId > 0) {
                    updateTransactionUseCase(transaction)
                } else {
                    addTransactionUseCase(transaction)
                }
                _uiState.update { it.copy(isSaveSuccessful = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Erro ao salvar transação") }
            }
        }
    }
}
