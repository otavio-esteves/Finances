package br.com.otavioesteves.finances.data.ai

import br.com.otavioesteves.finances.domain.ai.AiEngineState
import br.com.otavioesteves.finances.domain.ai.InstalledModel
import br.com.otavioesteves.finances.domain.ai.LocalAiEngine
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A shared runtime for chat and categorization; conversations remain scoped to each request. */
class LiteRtEngineSession {
    private val resource = SerializedResource<InstalledModel, Engine> { it.close() }

    suspend fun <R> withEngine(localAiEngine: LocalAiEngine, block: suspend (Engine) -> R): R =
        withContext(Dispatchers.IO) {
            resource.use(
                key = {
                    (localAiEngine.state.value as? AiEngineState.Ready)?.model
                        ?: error("O modelo de IA não está disponível.")
                },
                create = { model ->
                    val engine = Engine(EngineConfig(modelPath = model.path))
                    try {
                        engine.initialize()
                        engine
                    } catch (failure: Throwable) {
                        runCatching { engine.close() }
                        throw failure
                    }
                },
                block = block
            )
        }

    /** Waits for running inference and closes the runtime before changing the model files. */
    suspend fun <R> invalidate(block: suspend () -> R): R = withContext(Dispatchers.IO) {
        resource.invalidate(block)
    }

    suspend fun release() { invalidate { } }
}
