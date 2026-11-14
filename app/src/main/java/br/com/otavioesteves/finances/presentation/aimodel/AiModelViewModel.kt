package br.com.otavioesteves.finances.presentation.aimodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.ai.AiEngineState
import br.com.otavioesteves.finances.domain.ai.LocalAiEngine
import br.com.otavioesteves.finances.domain.ai.ModelImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.io.InputStream

data class AiModelUiState(
    val engineState: AiEngineState = AiEngineState.NotProvisioned,
    val isImporting: Boolean = false,
    val errorMessage: String? = null,
    val hasImportedModel: Boolean = false,
    val isRemoving: Boolean = false
)

class AiModelViewModel(
    private val localAiEngine: LocalAiEngine,
    private val modelImporter: ModelImporter
) : ViewModel() {

    private val isImporting = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val isRemoving = MutableStateFlow(false)
    private var refreshJob: Job? = null

    val uiState: StateFlow<AiModelUiState> = combine(
        localAiEngine.state,
        isImporting,
        errorMessage,
        modelImporter.hasImportedModel,
        isRemoving
    ) { engineState, importing, error, hasModel, removing ->
        AiModelUiState(engineState, importing, error, hasModel, removing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiModelUiState(engineState = localAiEngine.state.value, hasImportedModel = modelImporter.hasImportedModel.value)
    )

    fun onResume() {
        if (isImporting.value || isRemoving.value || refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            try {
                modelImporter.refreshAvailability()
                if (localAiEngine.state.value is AiEngineState.Ready) localAiEngine.warmUp().onFailure {
                    errorMessage.value = "O modelo instalado não pôde ser carregado neste aparelho."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                errorMessage.value = failure.message ?: "Não foi possível conferir o modelo instalado."
            }
        }
    }

    fun onModelSelected(displayName: String, openStream: () -> InputStream) {
        if (isImporting.value || isRemoving.value) return
        refreshJob?.cancel()
        isImporting.value = true
        errorMessage.value = null
        viewModelScope.launch {
            try {
                modelImporter.importModel(displayName, openStream)
                    .onFailure { errorMessage.value = it.message ?: "Não foi possível importar o modelo selecionado." }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                errorMessage.value = failure.message ?: "Não foi possível importar o modelo selecionado."
            } finally {
                isImporting.value = false
            }
        }
    }

    fun onRemoveModel() {
        if (isImporting.value || isRemoving.value) return
        refreshJob?.cancel()
        isRemoving.value = true
        errorMessage.value = null
        viewModelScope.launch {
            try {
                modelImporter.removeImportedModel()
                    .onFailure { errorMessage.value = "Não foi possível remover o modelo." }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                errorMessage.value = failure.message ?: "Não foi possível remover o modelo."
            } finally {
                isRemoving.value = false
            }
        }
    }

    fun clearError() {
        errorMessage.value = null
    }
}
