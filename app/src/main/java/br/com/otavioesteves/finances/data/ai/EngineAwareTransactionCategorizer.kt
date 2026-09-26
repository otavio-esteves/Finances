package br.com.otavioesteves.finances.data.ai

import br.com.otavioesteves.finances.domain.ai.AiEngineState
import br.com.otavioesteves.finances.domain.ai.LocalAiEngine
import br.com.otavioesteves.finances.domain.ai.TransactionCategorizer
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.RawStatementEntry

/**
 * Resolves which [TransactionCategorizer] to use per call, based on the
 * engine's *current* state — not fixed at DI wiring time, since the user can
 * import or remove a model at runtime from the Modelo de IA screen (Fase 2a).
 * See docs/PLANO_MOTOR_IA_LOCAL.md, "Importação do modelo".
 */
class EngineAwareTransactionCategorizer(
    private val localAiEngine: LocalAiEngine,
    private val aiCategorizer: TransactionCategorizer,
    private val ruleBasedCategorizer: TransactionCategorizer
) : TransactionCategorizer {

    override suspend fun categorize(
        entries: List<RawStatementEntry>,
        categories: List<Category>,
        onProgress: (done: Int, total: Int) -> Unit
    ): Result<List<CategorySuggestion>> {
        val categorizer = if (localAiEngine.state.value is AiEngineState.Ready) {
            aiCategorizer
        } else {
            ruleBasedCategorizer
        }
        return categorizer.categorize(entries, categories, onProgress)
    }
}
