package br.com.otavioesteves.finances.presentation.addtransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Category
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
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.math.BigDecimal

class AddTransactionViewModel(
    private val addTransactionUseCase: AddTransactionUseCase,
    private val updateTransactionUseCase: UpdateTransactionUseCase,
    private val getTransactionUseCase: GetTransactionUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase,
    private val categoriesRepository: CategoriesRepository,
    dateProvider: DateProvider,
    private val transactionId: Long? = null
) : ViewModel() {

    val isEditMode: Boolean = transactionId != null && transactionId > 0

    private val _uiState = MutableStateFlow(AddTransactionUiState(date = dateProvider.getCurrentDate()))
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()
    private var editingTransaction: Transaction? = null

    init {
        viewModelScope.launch {
            try {
                categoriesRepository.getCategories().collect { categories ->
                    _uiState.update { state ->
                        val selectedId = state.selectedCategory?.id ?: editingTransaction?.categoryId
                        state.copy(
                            categories = categories,
                            selectedCategory = categories.firstOrNull {
                                it.id == selectedId && it.type.name == state.type.name
                            }
                        )
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(error = "Erro ao carregar categorias") } }
        }
        if (isEditMode) {
            _uiState.update { it.copy(isLoading = true) }
            viewModelScope.launch {
                try { loadTransaction(requireNotNull(transactionId)) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = "Erro ao carregar transação") } }
            }
        }
    }

    private suspend fun loadTransaction(id: Long) {
        _uiState.update { it.copy(isLoading = true) }
        
        val transaction = getTransactionUseCase(id).first()
        
        if (transaction != null) {
            editingTransaction = transaction
            _uiState.update { state ->
                state.copy(
                    description = transaction.description,
                    amount = BigDecimal.valueOf(transaction.amount.cents, 2).toPlainString().replace('.', ','),
                    type = transaction.type,
                    selectedCategory = state.categories.find { it.id == transaction.categoryId },
                    date = transaction.date,
                    notes = transaction.notes ?: "",
                    sourceLabel = transaction.sourceLabel ?: "",
                    iconKey = transaction.iconKey,
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
        _uiState.update { state ->
            state.copy(
                type = type,
                selectedCategory = state.selectedCategory?.takeIf { it.type.name == type.name },
                categoryError = null
            )
        }
    }

    fun onCategoryChange(category: Category) {
        _uiState.update { it.copy(selectedCategory = category, categoryError = null) }
    }

    fun showCreateCategory() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(showCategoryCreation = true, categoryCreationError = null) }
    }

    fun dismissCreateCategory() {
        if (_uiState.value.isCreatingCategory) return
        _uiState.update { it.copy(showCategoryCreation = false, categoryCreationError = null) }
    }

    fun createCategory(name: String) {
        val state = _uiState.value
        if (!state.showCategoryCreation || state.isCreatingCategory || state.isSaving) return
        val normalized = name.trim().replace(Regex("\\s+"), " ")
        if (normalized.isBlank() || normalized.length > 80) {
            _uiState.update { it.copy(categoryCreationError = "Informe um nome com até 80 caracteres.") }
            return
        }
        _uiState.update { it.copy(isCreatingCategory = true, categoryCreationError = null) }
        viewModelScope.launch {
            try {
                val category = categoriesRepository.createCategory(normalized, CategoryType.valueOf(state.type.name))
                _uiState.update { current ->
                    current.copy(
                        categories = (current.categories + category).distinctBy { it.id },
                        selectedCategory = if (current.type == state.type) category else current.selectedCategory,
                        categoryError = null,
                        showCategoryCreation = false,
                        isCreatingCategory = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isCreatingCategory = false,
                    categoryCreationError = e.message ?: "Não foi possível criar a categoria.") }
            }
        }
    }

    fun onDateChange(date: LocalDate) {
        _uiState.update { it.copy(date = date, dateError = null) }
    }

    fun onSourceLabelChange(value: String) {
        _uiState.update { it.copy(sourceLabel = value.take(120)) }
    }

    fun onNotesChange(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun onIconChange(iconKey: String?) {
        _uiState.update { it.copy(iconKey = iconKey) }
    }

    fun onDeleteRequest() {
        if (_uiState.value.isSaving || _uiState.value.isLoading || _uiState.value.isSaveSuccessful) return
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    fun onDeleteCancel() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun onDeleteConfirm() {
        if (_uiState.value.isSaving || _uiState.value.isLoading || _uiState.value.isSaveSuccessful) return
        val id = transactionId ?: return
        _uiState.update { it.copy(showDeleteConfirmation = false, isLoading = true) }
        viewModelScope.launch {
            try {
                deleteTransactionUseCase(id)
                _uiState.update { it.copy(isSaveSuccessful = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Erro ao excluir transação") }
            }
        }
    }

    fun saveTransaction() {
        val currentState = _uiState.value
        if (currentState.isSaving || currentState.isLoading || currentState.isSaveSuccessful) return
        var hasError = false

        if (currentState.description.isBlank()) {
            _uiState.update { it.copy(descriptionError = "Descrição é obrigatória") }
            hasError = true
        }

        val parsedAmount = MoneyFormatter.parse(currentState.amount)
        val amountError = when {
            parsedAmount == null -> "Informe um valor válido"
            parsedAmount.isNegative() -> "Valor não pode ser negativo"
            !isEditMode && parsedAmount.isZero() -> "Valor deve ser maior que zero"
            else -> null
        }
        if (amountError != null) {
            _uiState.update { it.copy(amountError = amountError) }
            hasError = true
        }

        if (currentState.selectedCategory?.type?.name != currentState.type.name) {
            _uiState.update { it.copy(categoryError = "Selecione uma categoria do mesmo tipo") }
            hasError = true
        }
        
        if (hasError) return

        // Claim the save before launching so a second tap cannot enqueue another insert.
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val existing = editingTransaction
                if (isEditMode && existing == null) {
                    _uiState.update { it.copy(error = "Transação não encontrada") }
                    return@launch
                }
                val transaction = (existing ?: Transaction(
                    id = 0,
                    description = currentState.description.trim(),
                    amount = parsedAmount!!,
                    categoryId = currentState.selectedCategory!!.id,
                    date = currentState.date,
                    type = currentState.type
                )).copy(
                    description = currentState.description.trim(),
                    amount = parsedAmount!!,
                    categoryId = currentState.selectedCategory!!.id,
                    date = currentState.date,
                    type = currentState.type,
                    notes = currentState.notes.trim().takeIf { it.isNotEmpty() },
                    sourceLabel = currentState.sourceLabel.trim().takeIf { it.isNotEmpty() },
                    iconKey = currentState.iconKey
                )

                if (isEditMode) {
                    updateTransactionUseCase(transaction)
                } else {
                    addTransactionUseCase(transaction)
                }
                _uiState.update { it.copy(isSaveSuccessful = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(error = e.message ?: "Dados inválidos para salvar a transação") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Erro ao salvar transação") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
