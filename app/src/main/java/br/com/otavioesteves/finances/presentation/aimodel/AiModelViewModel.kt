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
import java.io.InputStream

data class AiModelUiState(
    val engineState: AiEngineState = AiEngineState.NotProvisioned,
    val isImporting: Boolean = false,
    val errorMessage: String? = null
)

class AiModelViewModel(
    private val localAiEngine: LocalAiEngine,
    private val modelImporter: ModelImporter
) : ViewModel() {

    private val isImporting = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AiModelUiState> = combine(
        localAiEngine.state,
        isImporting,
        errorMessage
    ) { engineState, importing, error ->
        AiModelUiState(engineState = engineState, isImporting = importing, errorMessage = error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiModelUiState(engineState = localAiEngine.state.value)
    )

    init {
        if (localAiEngine.state.value is AiEngineState.Ready) {
            viewModelScope.launch {
                localAiEngine.warmUp().onFailure {
                    errorMessage.value = "O modelo instalado parece corrompido. Importe novamente."
                }
            }
        }
    }

    fun onModelSelected(displayName: String, openStream: () -> InputStream) {
        if (isImporting.value) return
        isImporting.value = true
        errorMessage.value = null
        viewModelScope.launch {
            modelImporter.importModel(displayName, openStream)
                .onFailure { errorMessage.value = "Não foi possível importar o modelo selecionado." }
            isImporting.value = false
        }
    }

    fun onRemoveModel() {
        viewModelScope.launch {
            modelImporter.removeImportedModel()
                .onFailure { errorMessage.value = "Não foi possível remover o modelo." }
        }
    }

    fun clearError() {
        errorMessage.value = null
    }
}
