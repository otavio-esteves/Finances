package br.com.otavioesteves.finances.data.ai

import br.com.otavioesteves.finances.domain.ai.AiEngineState
import br.com.otavioesteves.finances.domain.ai.InstalledModel
import br.com.otavioesteves.finances.domain.ai.LocalAiEngine
import br.com.otavioesteves.finances.domain.ai.ModelSource
import br.com.otavioesteves.finances.domain.ai.TransactionCategorizer
import br.com.otavioesteves.finances.domain.ai.UnsupportedReason
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EngineAwareTransactionCategorizerTest {

    private val category = Category(id = 1L, name = "Mercado", type = CategoryType.EXPENSE)
    private val entry = RawStatementEntry(
        description = "Supermercado",
        amount = Money.fromCents(1_000),
        date = LocalDate.of(2026, 1, 10),
        type = TransactionType.EXPENSE
    )

    private class FakeLocalAiEngine(initialState: AiEngineState) : LocalAiEngine {
        val stateFlow = MutableStateFlow(initialState)
        override val state = stateFlow
        override suspend fun warmUp(): Result<Unit> = Result.success(Unit)
        override suspend fun release() = Unit
    }

    private class SpyCategorizer(private val label: String) : TransactionCategorizer {
        var wasCalled = false
        override suspend fun categorize(
            entries: List<RawStatementEntry>,
            categories: List<Category>,
            onProgress: (done: Int, total: Int) -> Unit
        ): Result<List<CategorySuggestion>> {
            wasCalled = true
            return Result.success(
                entries.map {
                    CategorySuggestion(entry = it, suggestedCategory = categories.first(), confidence = 1f)
                }
            )
        }
    }

    @Test
    fun categorize_usesAiCategorizerWhenEngineIsReady() = runBlocking {
        val readyState = AiEngineState.Ready(
            InstalledModel(
                id = "abc",
                displayName = "Modelo teste",
                source = ModelSource.USER_IMPORTED,
                verified = false,
                path = "/tmp/model.litertlm"
            )
        )
        val aiCategorizer = SpyCategorizer("ai")
        val ruleBasedCategorizer = SpyCategorizer("rule")
        val router = EngineAwareTransactionCategorizer(
            localAiEngine = FakeLocalAiEngine(readyState),
            aiCategorizer = aiCategorizer,
            ruleBasedCategorizer = ruleBasedCategorizer
        )

        router.categorize(listOf(entry), listOf(category))

        assertEquals(true, aiCategorizer.wasCalled)
        assertEquals(false, ruleBasedCategorizer.wasCalled)
    }

    @Test
    fun categorize_usesRuleBasedCategorizerWhenEngineIsNotProvisioned() = runBlocking {
        val aiCategorizer = SpyCategorizer("ai")
        val ruleBasedCategorizer = SpyCategorizer("rule")
        val router = EngineAwareTransactionCategorizer(
            localAiEngine = FakeLocalAiEngine(AiEngineState.NotProvisioned),
            aiCategorizer = aiCategorizer,
            ruleBasedCategorizer = ruleBasedCategorizer
        )

        router.categorize(listOf(entry), listOf(category))

        assertEquals(false, aiCategorizer.wasCalled)
        assertEquals(true, ruleBasedCategorizer.wasCalled)
    }

    @Test
    fun categorize_usesRuleBasedCategorizerWhenEngineIsUnsupported() = runBlocking {
        val aiCategorizer = SpyCategorizer("ai")
        val ruleBasedCategorizer = SpyCategorizer("rule")
        val router = EngineAwareTransactionCategorizer(
            localAiEngine = FakeLocalAiEngine(AiEngineState.Unsupported(UnsupportedReason.INSUFFICIENT_RAM)),
            aiCategorizer = aiCategorizer,
            ruleBasedCategorizer = ruleBasedCategorizer
        )

        router.categorize(listOf(entry), listOf(category))

        assertEquals(false, aiCategorizer.wasCalled)
        assertEquals(true, ruleBasedCategorizer.wasCalled)
    }
}
