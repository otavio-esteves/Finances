package br.com.otavioesteves.finances.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.usecase.CreateBackupUseCase
import br.com.otavioesteves.finances.domain.usecase.RestoreBackupUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val pendingRestoreJson: String? = null
)

class SettingsViewModel(
    private val createBackup: CreateBackupUseCase,
    private val restoreBackup: RestoreBackupUseCase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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
