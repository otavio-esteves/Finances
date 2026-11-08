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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import br.com.otavioesteves.finances.utils.readBoundedDocument
import java.io.InputStream
import br.com.otavioesteves.finances.domain.model.StatementPasswordRequiredException
import br.com.otavioesteves.finances.utils.MoneyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.concurrent.atomic.AtomicLong

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
    private val loadRevision = AtomicLong()
    private var validationJob: Job? = null
    private var validationRevision = 0
    private data class PendingFile(val name: String, val kind: StatementKind, val openStream: () -> InputStream)
    private var pendingFile: PendingFile? = null

    private val _uiState = MutableStateFlow<ImportStatementUiState>(ImportStatementUiState.Idle)
    val uiState: StateFlow<ImportStatementUiState> = _uiState.asStateFlow()

    fun onFileSelected(fileName: String, content: ByteArray, kind: StatementKind = StatementKind.BANK_STATEMENT) =
        onFileSelected(fileName, kind) { ByteArrayInputStream(content) }

    fun onFileSelected(fileName: String, kind: StatementKind = StatementKind.BANK_STATEMENT, openStream: () -> InputStream) {
        startReading(PendingFile(fileName, kind, openStream))
    }

    fun onPasswordSubmitted(password: String) {
        if (_uiState.value !is ImportStatementUiState.NeedsPassword || password.isEmpty()) return
        pendingFile?.let { startReading(it, password) }
    }

    private fun startReading(file: PendingFile, password: String? = null) {
        if ((_uiState.value as? ImportStatementUiState.ReviewingSuggestions)?.isConfirming == true) return
        val revision = loadRevision.incrementAndGet()
        loadJob?.cancel()
        validationJob?.cancel()
        validationRevision++
        pendingFile = file
        val (fileName, kind, openStream) = file
        _uiState.value = ImportStatementUiState.Loading(fileName)
        loadJob = viewModelScope.launch {
            val requestContext = currentCoroutineContext()
            fun showStage(stage: ImportProcessingStage, done: Int = 0, total: Int? = null) {
                _uiState.update { current ->
                    if (loadRevision.get() == revision && requestContext.isActive && current is ImportStatementUiState.Loading) {
                        ImportStatementUiState.Loading(fileName, stage, done, total)
                    } else current
                }
            }
            try {
                val content = withContext(ioDispatcher) { readBounded(openStream()) }
                showStage(ImportProcessingStage.EXTRACTING)
                val entries = withContext(cpuDispatcher) { importStatement(fileName, content, kind, password) }
                requestContext.ensureActive()
                if (loadRevision.get() != revision) return@launch
                if (entries.isEmpty()) {
                    _uiState.value = ImportStatementUiState.Error(
                        "Nenhuma transação encontrada neste arquivo."
                    )
                    return@launch
                }
                showStage(ImportProcessingStage.CHECKING_OVERLAPS)
                val overlapMatches = withContext(cpuDispatcher) { findPotentialOverlaps.findMatches(entries) }
                showStage(ImportProcessingStage.CATEGORIZING, total = entries.size)
                val suggestions = withContext(cpuDispatcher) {
                    synthesizeStatement(entries) { done, total ->
                        // The final callback can precede a failed result. Only the review represents completion.
                        if (total == entries.size && done in 0 until total) {
                            showStage(ImportProcessingStage.CATEGORIZING, done, total)
                        }
                    }
                }
                showStage(ImportProcessingStage.PREPARING_REVIEW)
                val categories = categoriesRepository.getCategories().first()
                requestContext.ensureActive()
                if (loadRevision.get() != revision) return@launch
                pendingFile = null

                _uiState.value = ImportStatementUiState.ReviewingSuggestions(
                    fileName = fileName,
                    suggestions = suggestions,
                    availableCategories = categories,
                    overlapMatches = overlapMatches
                )
            } catch (e: StatementPasswordRequiredException) {
                currentCoroutineContext().ensureActive()
                if (loadRevision.get() != revision) return@launch
                _uiState.value = ImportStatementUiState.NeedsPassword(fileName, if (e.incorrectPassword) e.message else null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                if (loadRevision.get() != revision) return@launch
                _uiState.value = ImportStatementUiState.Error(
                    e.message ?: "Não foi possível ler o extrato."
                )
            }
        }
    }

    fun onCategorySelected(index: Int, category: Category) {
        val current = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        if (current.isConfirming || index !in current.suggestions.indices || index in current.excludedIndices) return
        if (category !in current.availableCategories || category.type.name != current.suggestions[index].entry.type.name) return
        val updatedSuggestions = current.suggestions.toMutableList().apply {
            this[index] = this[index].copy(
                suggestedCategory = category,
                confidence = 1f,
                source = SuggestionSource.USER,
                rememberCorrection = true
            )
        }
        _uiState.value = current.copy(suggestions = updatedSuggestions, selectedIndices = current.selectedIndices - index)
    }

    fun onFilterSelected(filter: ImportReviewFilter) {
        val current = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        if (!current.isConfirming) _uiState.value = current.copy(filter = filter, selectedIndices = emptySet())
    }

    fun onSelectionModeChanged(enabled: Boolean) {
        val current = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        if (!current.isConfirming) _uiState.value = current.copy(selectionMode = enabled, selectedIndices = emptySet())
    }

    fun onEntrySelectionChanged(index: Int, selected: Boolean) {
        val current = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        if (current.isConfirming || !current.selectionMode || index !in current.visibleIndices || index in current.excludedIndices) return
        _uiState.value = current.copy(selectedIndices = if (selected) current.selectedIndices + index else current.selectedIndices - index)
    }

    fun onSelectVisibleEntries() {
        val current = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        if (current.isConfirming || !current.selectionMode) return
        val visible = current.visibleIndices.filterNot { it in current.excludedIndices }.toSet()
        _uiState.value = current.copy(selectedIndices = if (visible == current.selectedIndices) emptySet() else visible)
    }

    fun onBulkCategorySelected(category: Category) {
        val current = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        if (current.isConfirming || current.selectedIndices.isEmpty() || category !in current.bulkCategories) return
        val updated = current.suggestions.mapIndexed { index, suggestion ->
            if (index in current.selectedIndices && index !in current.excludedIndices) suggestion.copy(
                suggestedCategory = category, confidence = 1f, source = SuggestionSource.USER, rememberCorrection = true
            ) else suggestion
        }
        _uiState.value = current.copy(suggestions = updated, selectedIndices = emptySet(), selectionMode = false)
    }

    fun onEntryExcluded(index: Int, excluded: Boolean) {
        val current = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        if (current.isConfirming || index !in current.suggestions.indices) return
        _uiState.value = current.copy(
            excludedIndices = if (excluded) current.excludedIndices + index else current.excludedIndices - index,
            selectedIndices = current.selectedIndices - index,
            overlapAcknowledged = false, differenceAcknowledged = false, ocrAcknowledged = false
        )
    }

    fun onTypeSelected(index: Int, type: TransactionType) {
        val current = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        if (current.isConfirming || index !in current.suggestions.indices || index in current.excludedIndices) return
        val categoryType = CategoryType.valueOf(type.name)
        val category = current.availableCategories.firstOrNull { it.type == categoryType } ?: return
        val updated = current.suggestions.toMutableList().apply {
            this[index] = this[index].copy(
                entry = this[index].entry.copy(type = type),
                suggestedCategory = category,
                confidence = 1f,
                source = SuggestionSource.USER,
                rememberCorrection = true
            )
        }
        _uiState.value = current.copy(suggestions = updated, differenceAcknowledged = false, selectedIndices = current.selectedIndices - index)
    }

    fun onOverlapAcknowledged(acknowledged: Boolean) {
        val current = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        if (!current.isConfirming) _uiState.value = current.copy(overlapAcknowledged = acknowledged)
    }

    fun onOcrAcknowledged(acknowledged: Boolean) {
        val current = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        if (!current.isConfirming) _uiState.value = current.copy(ocrAcknowledged = acknowledged)
    }

    fun onEntryEdited(index: Int, description: String, amount: String, date: String): String? {
        val current = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return "Revisão indisponível."
        if (current.isConfirming || index !in current.suggestions.indices || index in current.excludedIndices) return "Revisão indisponível."
        if (description.isBlank()) return "Informe uma descrição."
        val money = MoneyFormatter.parse(amount)?.takeUnless { it.isNegative() } ?: return "Informe um valor válido, sem sinal."
        val parsedDate = runCatching {
            LocalDate.parse(date.trim(), DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT))
        }.getOrNull()?.takeIf { it.year in 1..9999 } ?: return "Informe uma data válida no formato dd/MM/aaaa, com ano entre 1 e 9999."
        val updated = current.suggestions.toMutableList().apply {
            this[index] = this[index].copy(
                entry = this[index].entry.copy(description = description.trim(), amount = money, date = parsedDate)
            )
        }
        validationJob?.cancel()
        val revision = ++validationRevision
        _uiState.value = current.copy(suggestions = updated, differenceAcknowledged = false, ocrAcknowledged = false, overlapAcknowledged = false,
            isCheckingOverlaps = true, validationError = null, selectedIndices = current.selectedIndices - index)
        validationJob = viewModelScope.launch {
            try {
                val overlaps = withContext(cpuDispatcher) { findPotentialOverlaps.findMatches(updated.map { it.entry }) }
                val latest = _uiState.value as? ImportStatementUiState.ReviewingSuggestions
                if (revision == validationRevision && latest != null) _uiState.value = latest.copy(
                    overlapMatches = overlaps, isCheckingOverlaps = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val latest = _uiState.value as? ImportStatementUiState.ReviewingSuggestions
                if (revision == validationRevision && latest != null) _uiState.value = latest.copy(
                    isCheckingOverlaps = false, validationError = "Não foi possível conferir duplicidades. Selecione o arquivo novamente.")
            }
        }
        return null
    }

    fun onSourceLabelChanged(value: String) {
        val state = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        if (!state.isConfirming) _uiState.value = state.copy(sourceLabel = value.take(120))
    }
    fun onExpectedTotalChanged(value: String) {
        val state = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        if (!state.isConfirming) _uiState.value = state.copy(expectedExpenseTotal = value.take(24), differenceAcknowledged = false)
    }
    fun onDifferenceAcknowledged(value: Boolean) {
        val state = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        if (!state.isConfirming) _uiState.value = state.copy(differenceAcknowledged = value)
    }

    fun onConfirmImport() {
        val current = _uiState.value as? ImportStatementUiState.ReviewingSuggestions ?: return
        if (!current.canConfirm) return

        _uiState.value = current.copy(isConfirming = true)
        viewModelScope.launch {
            try {
                val result = withContext(cpuDispatcher) {
                    confirmStatementImport(current.fileName, current.includedSuggestions, current.sourceLabel,
                        fingerprintSuggestions = current.suggestions)
                }
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
        if ((_uiState.value as? ImportStatementUiState.ReviewingSuggestions)?.isConfirming == true) return
        loadRevision.incrementAndGet()
        loadJob?.cancel()
        validationJob?.cancel()
        validationRevision++
        pendingFile = null
        _uiState.value = ImportStatementUiState.Idle
    }

    private fun readBounded(input: InputStream): ByteArray = readBoundedDocument(
        input, MAX_STATEMENT_BYTES, "Arquivo muito grande. O limite para CSV, OFX e PDF é 10 MB."
    )

    private companion object {
        const val MAX_STATEMENT_BYTES = 10 * 1024 * 1024
    }
}
