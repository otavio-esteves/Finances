package br.com.otavioesteves.finances.presentation.importstatement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.SuggestionSource
import br.com.otavioesteves.finances.domain.model.StatementKind
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.usecase.ConfirmStatementImportUseCase
import br.com.otavioesteves.finances.domain.usecase.ImportStatementUseCase
import br.com.otavioesteves.finances.domain.usecase.FindPotentialOverlapsUseCase
import br.com.otavioesteves.finances.domain.usecase.SynthesizeStatementUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import br.com.otavioesteves.finances.utils.readBoundedDocument
import java.io.InputStream

class ImportStatementViewModel(
    private val importStatement: ImportStatementUseCase,
    private val synthesizeStatement: SynthesizeStatementUseCase,
    private val confirmStatementImport: ConfirmStatementImportUseCase,
    private val categoriesRepository: CategoriesRepository,
    private val findPotentialOverlaps: FindPotentialOverlapsUseCase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private var loadJob: Job? = null

    private val _uiState = MutableStateFlow<ImportStatementUiState>(ImportStatementUiState.Idle)
    val uiState: StateFlow<ImportStatementUiState> = _uiState.asStateFlow()

    fun onFileSelected(fileName: String, content: ByteArray, kind: StatementKind = StatementKind.BANK_STATEMENT) =
        onFileSelected(fileName, kind) { ByteArrayInputStream(content) }

    fun onFileSelected(fileName: String, kind: StatementKind = StatementKind.BANK_STATEMENT, openStream: () -> InputStream) {
        loadJob?.cancel()
        _uiState.value = ImportStatementUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                val content = withContext(ioDispatcher) { readBounded(openStream()) }
                val entries = withContext(cpuDispatcher) { importStatement(fileName, content, kind) }
                if (entries.isEmpty()) {
                    _uiState.value = ImportStatementUiState.Error(
                        "Nenhuma transação encontrada neste arquivo."
                    )
                    return@launch
                }

                val potentialOverlapCount = findPotentialOverlaps(entries)
                val suggestions = withContext(cpuDispatcher) { synthesizeStatement(entries) }
                val categories = categoriesRepository.getCategories().first()

                _uiState.value = ImportStatementUiState.ReviewingSuggestions(
                    fileName = fileName,
                    suggestions = suggestions,
                    availableCategories = categories,
                    potentialOverlapCount = potentialOverlapCount
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = ImportStatementUiState.Error(
                    e.message ?: "Não foi possível ler o extrato."
                )
            }
        }
    }

    fun onCategorySelected(index: Int, category: Category) {
        val current = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        if (current.isConfirming || index !in current.suggestions.indices) return
        if (category !in current.availableCategories || category.type.name != current.suggestions[index].entry.type.name) return
        val updatedSuggestions = current.suggestions.toMutableList().apply {
            this[index] = this[index].copy(
                suggestedCategory = category,
                confidence = 1f,
                source = SuggestionSource.USER
            )
        }
        _uiState.value = current.copy(suggestions = updatedSuggestions)
    }

    fun onTypeSelected(index: Int, type: TransactionType) {
        val current = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        if (current.isConfirming || index !in current.suggestions.indices) return
        val categoryType = CategoryType.valueOf(type.name)
        val category = current.availableCategories.firstOrNull { it.type == categoryType } ?: return
        val updated = current.suggestions.toMutableList().apply {
            this[index] = this[index].copy(
                entry = this[index].entry.copy(type = type),
                suggestedCategory = category,
                confidence = 1f,
                source = SuggestionSource.USER
            )
        }
        _uiState.value = current.copy(suggestions = updated)
    }

    fun onOverlapAcknowledged(acknowledged: Boolean) {
        val current = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        if (!current.isConfirming) _uiState.value = current.copy(overlapAcknowledged = acknowledged)
    }

    fun onConfirmImport() {
        val current = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        if (!current.canConfirm) return

        _uiState.value = current.copy(isConfirming = true)
        viewModelScope.launch {
            try {
                val result = withContext(cpuDispatcher) { confirmStatementImport(current.fileName, current.suggestions) }
                _uiState.value = ImportStatementUiState.Success(result.transactionCount)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = ImportStatementUiState.Error(
                    e.message ?: "Erro ao confirmar a importação."
                )
            }
        }
    }

    fun onStartOver() {
        loadJob?.cancel()
        _uiState.value = ImportStatementUiState.Idle
    }

    private fun readBounded(input: InputStream): ByteArray = readBoundedDocument(
        input, MAX_STATEMENT_BYTES, "Arquivo muito grande. O limite para CSV, OFX e PDF é 10 MB."
    )

    private companion object {
        const val MAX_STATEMENT_BYTES = 10 * 1024 * 1024
    }
}
