package br.com.otavioesteves.finances.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.usecase.CreateBackupUseCase
import br.com.otavioesteves.finances.domain.usecase.RestoreBackupUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

data class SettingsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val showRestoreConfirmation: Boolean = false,
    val pendingUndo: br.com.otavioesteves.finances.domain.model.ImportedStatement? = null
)

class SettingsViewModel(
    private val createBackup: CreateBackupUseCase,
    private val restoreBackup: RestoreBackupUseCase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val importsRepository: br.com.otavioesteves.finances.domain.repository.StatementImportRepository? = null,
    private val memoryRepository: br.com.otavioesteves.finances.domain.repository.CategoryMemoryRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var pendingRestore: (() -> InputStream)? = null

    val imports = (importsRepository?.getImports() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    fun requestUndo(item: br.com.otavioesteves.finances.domain.model.ImportedStatement) {
        if (!_uiState.value.isLoading && item.canUndo) _uiState.update { it.copy(pendingUndo = item) }
    }
    fun cancelUndo() { _uiState.update { it.copy(pendingUndo = null) } }
    fun confirmUndo() {
        val item = _uiState.value.pendingUndo ?: return
        _uiState.update { it.copy(pendingUndo = null, isLoading = true) }
        viewModelScope.launch {
            try {
                requireNotNull(importsRepository).undoImport(item.id)
                _uiState.update { it.copy(isLoading = false, successMessage = "Importação desfeita") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = e.message ?: "Não foi possível desfazer") } }
        }
    }
    fun clearCategoryMemory() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                requireNotNull(memoryRepository).clear()
                _uiState.update { it.copy(isLoading = false, successMessage = "Memória das correções apagada") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = "Não foi possível apagar a memória") } }
        }
    }

    fun createBackup(openOutput: () -> OutputStream) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { createBackup(openOutput()) }
                _uiState.update { it.copy(isLoading = false, successMessage = "Backup gerado com sucesso") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Erro ao criar backup: ${e.message}") }
            }
        }
    }

    fun onRestoreRequest(openInput: () -> InputStream) {
        if (_uiState.value.isLoading) return
        pendingRestore = openInput
        _uiState.update { it.copy(showRestoreConfirmation = true) }
    }

    fun onRestoreCancel() {
        pendingRestore = null
        _uiState.update { it.copy(showRestoreConfirmation = false) }
    }

    fun onRestoreConfirm() {
        if (_uiState.value.isLoading) return
        val openInput = pendingRestore ?: return
        pendingRestore = null
        _uiState.update { it.copy(showRestoreConfirmation = false, isLoading = true, error = null, successMessage = null) }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { restoreBackup(openInput()) }
                _uiState.update { it.copy(isLoading = false, successMessage = "Backup restaurado com sucesso") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Erro ao restaurar backup: ${e.message}") }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
