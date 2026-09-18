package br.com.otavioesteves.finances.presentation.importstatement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.usecase.ConfirmStatementImportUseCase
import br.com.otavioesteves.finances.domain.usecase.ImportStatementUseCase
import br.com.otavioesteves.finances.domain.usecase.SynthesizeStatementUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ImportStatementViewModel(
    private val importStatement: ImportStatementUseCase,
    private val synthesizeStatement: SynthesizeStatementUseCase,
    private val confirmStatementImport: ConfirmStatementImportUseCase,
    private val categoriesRepository: CategoriesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportStatementUiState>(ImportStatementUiState.Idle)
    val uiState: StateFlow<ImportStatementUiState> = _uiState.asStateFlow()

    fun onFileSelected(fileName: String, content: ByteArray) {
        _uiState.value = ImportStatementUiState.Loading
        viewModelScope.launch {
            try {
                val entries = importStatement(fileName, content)
                if (entries.isEmpty()) {
                    _uiState.value = ImportStatementUiState.Error(
                        "Nenhuma transação encontrada neste arquivo."
                    )
                    return@launch
                }

                val suggestions = synthesizeStatement(entries)
                val categories = categoriesRepository.getCategories().first()

                _uiState.value = ImportStatementUiState.ReviewingSuggestions(
                    fileName = fileName,
                    suggestions = suggestions,
                    availableCategories = categories
                )
            } catch (e: Exception) {
                _uiState.value = ImportStatementUiState.Error(
                    e.message ?: "Não foi possível ler o extrato."
                )
            }
        }
    }

    fun onCategorySelected(index: Int, category: Category) {
        val current = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        val updatedSuggestions = current.suggestions.toMutableList().apply {
            this[index] = this[index].copy(suggestedCategory = category, confidence = 1f)
        }
        _uiState.value = current.copy(suggestions = updatedSuggestions)
    }

    fun onConfirmImport() {
        val current = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        if (!current.canConfirm) return

        _uiState.value = current.copy(isConfirming = true)
        viewModelScope.launch {
            try {
                val result = confirmStatementImport(current.fileName, current.suggestions)
                _uiState.value = ImportStatementUiState.Success(result.transactionCount)
            } catch (e: Exception) {
                _uiState.value = ImportStatementUiState.Error(
                    e.message ?: "Erro ao confirmar a importação."
                )
            }
        }
    }

    fun onStartOver() {
        _uiState.value = ImportStatementUiState.Idle
    }
}
