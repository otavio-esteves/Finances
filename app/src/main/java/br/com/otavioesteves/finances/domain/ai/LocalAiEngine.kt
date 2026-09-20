package br.com.otavioesteves.finances.domain.ai

import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle of the on-device inference engine, independent of which runtime
 * backs it (see docs/ARCHITECTURE.md, "Decisão de Arquitetura: Motor de IA Local").
 * Until Fase 3 wires a real engine, every [LocalAiEngine] stays [AiEngineState.NotProvisioned].
 */
sealed interface AiEngineState {
    data object NotProvisioned : AiEngineState
    data class Provisioning(val progress: Float) : AiEngineState
    data class Ready(val model: InstalledModel) : AiEngineState
    data class Unsupported(val reason: UnsupportedReason) : AiEngineState
    data class Failed(val cause: AiError) : AiEngineState
}

data class InstalledModel(
    val id: String,
    val displayName: String,
    val source: ModelSource,
    val verified: Boolean,
    /** Caminho absoluto do bundle no disco, para o motor de inferência carregar (Fase 3). */
    val path: String
)

/** Where the model bundle came from — see docs/ARCHITECTURE.md, seção 2. */
enum class ModelSource { ASSET_PACK, USER_IMPORTED }

enum class UnsupportedReason { INSUFFICIENT_RAM, UNSUPPORTED_ABI, NO_STORAGE }

sealed interface AiError {
    data object ModelNotLoaded : AiError
    data object OutOfMemory : AiError
    data object Timeout : AiError
    data class MalformedOutput(val raw: String) : AiError
    data class Unknown(val throwable: Throwable) : AiError
}

interface LocalAiEngine {
    val state: StateFlow<AiEngineState>
    suspend fun warmUp(): Result<Unit>
    suspend fun release()
}
