package br.com.otavioesteves.finances.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.usecase.CreateBackupUseCase
import br.com.otavioesteves.finances.domain.usecase.RestoreBackupUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val showRestoreConfirmation: Boolean = false,
    val pendingRestoreJson: String? = null
)

class SettingsViewModel(
    private val createBackup: CreateBackupUseCase,
    private val restoreBackup: RestoreBackupUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun createBackupJson(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val json = createBackup()
                onSuccess(json)
                _uiState.update { it.copy(isLoading = false, successMessage = "Backup gerado com sucesso") }
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
                restoreBackup(json)
                _uiState.update { it.copy(isLoading = false, successMessage = "Backup restaurado com sucesso", pendingRestoreJson = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Erro ao restaurar backup: ${e.message}", pendingRestoreJson = null) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
