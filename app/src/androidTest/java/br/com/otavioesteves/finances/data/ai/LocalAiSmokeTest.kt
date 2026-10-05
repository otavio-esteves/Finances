package br.com.otavioesteves.finances.data.ai

import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import br.com.otavioesteves.finances.data.DefaultDateProvider
import br.com.otavioesteves.finances.domain.ai.AiEngineState
import br.com.otavioesteves.finances.domain.ai.InstalledModel
import br.com.otavioesteves.finances.domain.ai.LocalAiEngine
import br.com.otavioesteves.finances.domain.ai.ModelSource
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.FinancialContext
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.SuggestionSource
import br.com.otavioesteves.finances.domain.model.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Run only on a dedicated test package with a model copied into its private files directory. */
@RunWith(AndroidJUnit4::class)
class LocalAiSmokeTest {
    @Test
    fun initializesBundleAndGeneratesText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val path = InstrumentationRegistry.getArguments().getString("validationModelPath")
        assumeTrue("A validationModelPath argument is required", !path.isNullOrBlank())
        val model = File(requireNotNull(path))
        assertTrue("Model bundle is missing", model.isFile && model.length() > 0)

        val started = SystemClock.elapsedRealtime()
        Engine(EngineConfig(modelPath = model.absolutePath)).use { engine ->
            engine.initialize()
            val initialized = SystemClock.elapsedRealtime()
            val answer = engine.createConversation(
                ConversationConfig(systemInstruction = Contents.of("Responda brevemente em português."))
            ).use { conversation ->
                conversation.sendMessage("Diga apenas: teste concluído.").contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
            }
            val finished = SystemClock.elapsedRealtime()
            instrumentation.sendStatus(0, Bundle().apply {
                putString("validation_result", "load_ms=${initialized - started}; reply_ms=${finished - initialized}; reply=${answer.take(120)}")
            })
            assertTrue("The model did not generate text", answer.isNotBlank())
        }
    }

    @Test
    fun appAdaptersGenerateChatAndCategorization() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val path = InstrumentationRegistry.getArguments().getString("validationModelPath")
        assumeTrue("A validationModelPath argument is required", !path.isNullOrBlank())
        val model = File(requireNotNull(path))
        assertTrue("Model bundle is missing", model.isFile && model.length() > 0)

        val engine = object : LocalAiEngine {
            override val state: StateFlow<AiEngineState> = MutableStateFlow(
                AiEngineState.Ready(InstalledModel("validation", "validation", ModelSource.USER_IMPORTED, false, model.absolutePath))
            )
            override suspend fun warmUp(): Result<Unit> = Result.success(Unit)
            override suspend fun release() = Unit
        }
        val dateProvider = DefaultDateProvider()
        val fallback = RuleBasedLocalAiRepository(dateProvider)
        val context = FinancialContext(
            period = MonthPeriod(2026, 1),
            monthlyBalance = Money.fromCents(50_000),
            categorySummaries = listOf(CategorySummary(Category(1, "Alimentação", CategoryType.EXPENSE), Money.fromCents(12_345)))
        )
        val session = LiteRtEngineSession()
        try {
            val firstRuntime = session.withEngine(engine) { it }
            val chatStarted = SystemClock.elapsedRealtime()
            val reply = LiteRtLocalAiRepository(engine, fallback, dateProvider, session)
                .sendMessage("Diga olá e mencione o mês dos dados.", context)
            val chatMs = SystemClock.elapsedRealtime() - chatStarted
            val repeatedStarted = SystemClock.elapsedRealtime()
            val repeatedReply = LiteRtLocalAiRepository(engine, fallback, dateProvider, session)
                .sendMessage("Diga olá novamente.", context)
            val repeatedMs = SystemClock.elapsedRealtime() - repeatedStarted
            assertTrue(repeatedReply.content.isNotBlank())
            assertSame(firstRuntime, session.withEngine(engine) { it })
            assertTrue(reply.content.isNotBlank())

            val categories = listOf(
                Category(1, "Alimentação", CategoryType.EXPENSE),
                Category(2, "Saúde", CategoryType.EXPENSE),
                Category(3, "Outros", CategoryType.EXPENSE)
            )
            val categorizationStarted = SystemClock.elapsedRealtime()
            val suggestions = LiteRtTransactionCategorizer(engine, fallback, engineSession = session).categorize(
                listOf(RawStatementEntry("Compra no mercado", Money.fromCents(5_000), LocalDate.of(2026, 1, 10), TransactionType.EXPENSE)),
                categories
            ).getOrThrow()
            val categorizationMs = SystemClock.elapsedRealtime() - categorizationStarted
            assertTrue(suggestions.size == 1)
            instrumentation.sendStatus(0, Bundle().apply {
                putString("validation_result", "chat_ms=$chatMs; repeated_chat_ms=$repeatedMs; reply=${reply.content.take(120)}; categorization_ms=$categorizationMs; " +
                    "category=${suggestions.single().suggestedCategory?.name}; source=${suggestions.single().source}")
            })
            assertTrue("The categorizer fell back to rules", suggestions.single().source == SuggestionSource.AI)
            assertSame(firstRuntime, session.withEngine(engine) { it })
        } finally {
            session.release()
        }
    }
}
