package br.com.otavioesteves.finances.data.ai

import br.com.otavioesteves.finances.domain.ai.AiEngineState
import br.com.otavioesteves.finances.domain.ai.InstalledModel
import br.com.otavioesteves.finances.domain.ai.LocalAiEngine
import br.com.otavioesteves.finances.domain.ai.ModelSource
import br.com.otavioesteves.finances.domain.ai.UnsupportedReason
import br.com.otavioesteves.finances.domain.model.ChatMessage
import br.com.otavioesteves.finances.domain.model.ChatRole
import br.com.otavioesteves.finances.domain.model.FinancialContext
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.repository.LocalAiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class EngineAwareLocalAiRepositoryTest {

    private val context = FinancialContext(
        period = MonthPeriod(year = 2026, month = 1),
        monthlyBalance = Money.Zero,
        categorySummaries = emptyList()
    )

    private class FakeLocalAiEngine(initialState: AiEngineState) : LocalAiEngine {
        val stateFlow = MutableStateFlow(initialState)
        override val state = stateFlow
        override suspend fun warmUp(): Result<Unit> = Result.success(Unit)
        override suspend fun release() = Unit
    }

    private class SpyRepository(private val label: String) : LocalAiRepository {
        var wasCalled = false
        override suspend fun sendMessage(message: String, context: FinancialContext): ChatMessage {
            wasCalled = true
            return ChatMessage(id = 0, role = ChatRole.ASSISTANT, content = label, createdAt = LocalDateTime.now())
        }
    }

    @Test
    fun sendMessage_usesAiRepositoryWhenEngineIsReady() = runBlocking {
        val readyState = AiEngineState.Ready(
            InstalledModel(
                id = "abc",
                displayName = "Modelo teste",
                source = ModelSource.USER_IMPORTED,
                verified = false,
                path = "/tmp/model.litertlm"
            )
        )
        val aiRepository = SpyRepository("ai")
        val ruleBasedRepository = SpyRepository("rule")
        val router = EngineAwareLocalAiRepository(
            localAiEngine = FakeLocalAiEngine(readyState),
            aiRepository = aiRepository,
            ruleBasedRepository = ruleBasedRepository
        )

        router.sendMessage("oi", context)

        assertEquals(true, aiRepository.wasCalled)
        assertEquals(false, ruleBasedRepository.wasCalled)
    }

    @Test
    fun sendMessage_usesRuleBasedRepositoryWhenEngineIsNotProvisioned() = runBlocking {
        val aiRepository = SpyRepository("ai")
        val ruleBasedRepository = SpyRepository("rule")
        val router = EngineAwareLocalAiRepository(
            localAiEngine = FakeLocalAiEngine(AiEngineState.NotProvisioned),
            aiRepository = aiRepository,
            ruleBasedRepository = ruleBasedRepository
        )

        router.sendMessage("oi", context)

        assertEquals(false, aiRepository.wasCalled)
        assertEquals(true, ruleBasedRepository.wasCalled)
    }

    @Test
    fun sendMessage_usesRuleBasedRepositoryWhenEngineIsUnsupported() = runBlocking {
        val aiRepository = SpyRepository("ai")
        val ruleBasedRepository = SpyRepository("rule")
        val router = EngineAwareLocalAiRepository(
            localAiEngine = FakeLocalAiEngine(AiEngineState.Unsupported(UnsupportedReason.INSUFFICIENT_RAM)),
            aiRepository = aiRepository,
            ruleBasedRepository = ruleBasedRepository
        )

        router.sendMessage("oi", context)

        assertEquals(false, aiRepository.wasCalled)
        assertEquals(true, ruleBasedRepository.wasCalled)
    }
}
