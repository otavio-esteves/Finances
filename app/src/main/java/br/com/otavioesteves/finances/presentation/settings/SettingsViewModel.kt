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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.Job
import br.com.otavioesteves.finances.domain.repository.BackupProtectionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

data class SettingsUiState(
    val isLoading: Boolean = false,
    val canCancelBackup: Boolean = false,
    val operationLabel: String = "Processando…",
    val error: String? = null,
    val successMessage: String? = null,
    val showRestoreConfirmation: Boolean = false,
    val pendingUndo: br.com.otavioesteves.finances.domain.model.ImportedStatement? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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

    private var backupJob: Job? = null
    private val historyReload = MutableStateFlow(0)
    val imports = historyReload.flatMapLatest {
        (importsRepository?.getImports() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
            .catch { error ->
                if (error is CancellationException) throw error
                _uiState.update { it.copy(error = "Não foi possível carregar o histórico de importações.") }
                emit(emptyList())
            }
    }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    fun requestUndo(item: br.com.otavioesteves.finances.domain.model.ImportedStatement) {
        if (!_uiState.value.isLoading && !_uiState.value.showRestoreConfirmation && item.canUndo) {
            _uiState.update { it.copy(pendingUndo = item) }
        }
    }
    fun cancelUndo() { _uiState.update { it.copy(pendingUndo = null) } }
    fun confirmUndo() {
        if (_uiState.value.isLoading) return
        val item = _uiState.value.pendingUndo ?: return
        _uiState.update { it.copy(pendingUndo = null, isLoading = true, error = null, successMessage = null) }
        viewModelScope.launch {
            try {
                requireNotNull(importsRepository).undoImport(item)
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

    fun createBackup(openOutput: () -> OutputStream) = createBackup(null, openOutput)

    fun createBackup(password: CharArray?, openOutput: () -> OutputStream) {
        if (_uiState.value.isLoading) { password?.fill('\u0000'); return }
        _uiState.update { it.copy(isLoading = true, canCancelBackup = true, operationLabel = "Gerando backup…",
            error = null, successMessage = null) }
        backupJob = viewModelScope.launch {
            try {
                withContext(ioDispatcher) { createBackup(openOutput(), password) }
                _uiState.update { it.copy(successMessage = "Backup gerado com sucesso") }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(error = "Backup cancelado. O arquivo de destino pode estar incompleto.") }
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = if (e is BackupProtectionException) e.message
                    else "Não foi possível criar o backup. Confira o destino e o espaço disponível; o arquivo pode estar incompleto.") }
            } finally {
                password?.fill('\u0000')
                _uiState.update { it.copy(isLoading = false, canCancelBackup = false) }
            }
        }.also { job -> job.invokeOnCompletion { cause ->
            password?.fill('\u0000')
            _uiState.update { it.copy(isLoading = false, canCancelBackup = false,
                error = it.error ?: if (cause is CancellationException) "Operação cancelada; confira o arquivo e os dados antes de continuar." else null) }
        } }
    }

    fun cancelBackupOperation() {
        if (!_uiState.value.canCancelBackup) return
        _uiState.update { it.copy(canCancelBackup = false, operationLabel = "Cancelando…") }
        backupJob?.cancel()
    }

    fun onRestoreRequest(openInput: () -> InputStream) {
        if (_uiState.value.isLoading) return
        pendingRestore = openInput
        _uiState.update { it.copy(showRestoreConfirmation = true, pendingUndo = null) }
    }

    fun onRestoreCancel() {
        if (_uiState.value.isLoading) return
        pendingRestore = null
        _uiState.update { it.copy(showRestoreConfirmation = false) }
    }

    fun onRestoreConfirm() = onRestoreConfirm(null)

    fun onRestoreConfirm(password: CharArray?) {
        if (_uiState.value.isLoading) { password?.fill('\u0000'); return }
        val openInput = pendingRestore ?: run { password?.fill('\u0000'); return }
        _uiState.update { it.copy(showRestoreConfirmation = false, isLoading = true, canCancelBackup = true,
            operationLabel = "Lendo e verificando backup…", error = null, successMessage = null) }
        var writeStarted = false
        backupJob = viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    restoreBackup(openInput(), password) {
                        writeStarted = true
                        _uiState.update { it.copy(canCancelBackup = false, operationLabel = "Restaurando dados…") }
                    }
                }
                pendingRestore = null
                historyReload.update { it + 1 }
                _uiState.update { it.copy(successMessage = "Backup restaurado com sucesso") }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(error = if (writeStarted) "Restauração interrompida. Reabra o aplicativo para conferir os dados; a gravação é atômica."
                    else "Leitura cancelada. Os dados atuais foram preservados.") }
                throw e
            } catch (e: Exception) {
                val protectedError = e as? BackupProtectionException
                _uiState.update { it.copy(error = protectedError?.message
                    ?: "Não foi possível restaurar este arquivo. Confira o formato e a integridade do backup. Os dados atuais foram preservados.",
                    showRestoreConfirmation = protectedError?.reason in listOf(BackupProtectionException.Reason.INVALID,
                        BackupProtectionException.Reason.PASSWORD_REQUIRED)) }
            } finally {
                password?.fill('\u0000')
                _uiState.update { it.copy(isLoading = false, canCancelBackup = false) }
            }
        }.also { job -> job.invokeOnCompletion { cause ->
            password?.fill('\u0000')
            _uiState.update { it.copy(isLoading = false, canCancelBackup = false,
                error = it.error ?: if (cause is CancellationException) "Operação cancelada; confira o arquivo e os dados antes de continuar." else null) }
        } }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
