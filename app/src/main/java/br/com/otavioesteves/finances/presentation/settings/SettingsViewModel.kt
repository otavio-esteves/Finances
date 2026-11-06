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

data class SettingsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val showRestoreConfirmation: Boolean = false,
    val pendingRestoreJson: String? = null,
    val pendingUndo: br.com.otavioesteves.finances.domain.model.ImportedStatement? = null
)

class SettingsViewModel(
    private val createBackup: CreateBackupUseCase,
    private val restoreBackup: RestoreBackupUseCase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val importsRepository: br.com.otavioesteves.finances.domain.repository.StatementImportRepository? = null,
    private val memoryRepository: br.com.otavioesteves.finances.domain.repository.CategoryMemoryRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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

    fun createBackupJson(write: suspend (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val json = withContext(cpuDispatcher) { createBackup() }
                withContext(ioDispatcher) { write(json) }
                _uiState.update { it.copy(isLoading = false, successMessage = "Backup gerado com sucesso") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Erro ao criar backup") }
            }
        }
    }

    fun onRestoreRequest(json: String) {
        _uiState.update { it.copy(showRestoreConfirmation = true, pendingRestoreJson = json) }
    }

    fun onRestoreCancel() {
        _uiState.update { it.copy(showRestoreConfirmation = false, pendingRestoreJson = null) }
    }

    fun onRestoreConfirm() {
        val json = _uiState.value.pendingRestoreJson ?: return
        _uiState.update { it.copy(showRestoreConfirmation = false, isLoading = true) }
        viewModelScope.launch {
            try {
                withContext(cpuDispatcher) { restoreBackup(json) }
                _uiState.update { it.copy(isLoading = false, successMessage = "Backup restaurado com sucesso", pendingRestoreJson = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Erro ao restaurar backup: ${e.message}", pendingRestoreJson = null) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
