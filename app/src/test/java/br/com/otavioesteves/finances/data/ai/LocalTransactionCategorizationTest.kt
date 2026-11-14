package br.com.otavioesteves.finances.data.ai

import br.com.otavioesteves.finances.domain.ai.TransactionCategorizer
import br.com.otavioesteves.finances.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LocalTransactionCategorizationTest {
    private val expense = Category(1, "Mercado", CategoryType.EXPENSE)
    private val entries = (1..41).map { RawStatementEntry("Compra $it", Money(100), LocalDate.of(2026, 11, 1), TransactionType.EXPENSE) }
    private class Rules(private val category: Category) : TransactionCategorizer {
        var count = 0
        override suspend fun categorize(entries: List<RawStatementEntry>, categories: List<Category>, onProgress: (Int, Int) -> Unit): Result<List<CategorySuggestion>> {
            count += entries.size
            return Result.success(entries.map { CategorySuggestion(it, category, 0.3f) })
        }
    }

    @Test fun validResponsesPreserveOrderProgressAndPerEntryFallbackAcrossChunks() = runTest {
        val rules = Rules(expense)
        val policy = LocalTransactionCategorization(rules, 0.5f)
        val progress = mutableListOf<Pair<Int, Int>>()
        var calls = 0
        val results = policy.categorize(entries, listOf(expense), { done, total -> progress += done to total }) { prompt ->
            calls++
            assertTrue(prompt.contains("Compra"))
            """[{"index":0,"category":"Mercado","confidence":0.9}]"""
        }
        assertEquals(entries, results.map { it.entry })
        assertEquals(listOf(20 to 41, 40 to 41, 41 to 41), progress)
        assertEquals(3, calls)
        assertEquals(38, rules.count)
        assertEquals(listOf(0, 20, 40), results.indices.filter { results[it].source == SuggestionSource.AI })
    }

    @Test fun malformedResponsesRetryOnceThenFallBackWithoutLosingEntries() = runTest {
        val rules = Rules(expense)
        var calls = 0
        val results = LocalTransactionCategorization(rules, 0.5f)
            .categorize(entries.take(2), listOf(expense), { _, _ -> }) { calls++; "not JSON" }
        assertEquals(2, calls)
        assertEquals(2, rules.count)
        assertEquals(entries.take(2), results.map { it.entry })
    }

    @Test fun cancellationDuringSynchronousInferencePreventsParsingRetryFallbackAndFurtherChunks() = runTest {
        for (response in listOf("not JSON", "[]")) {
            val rules = Rules(expense)
            var calls = 0
            var progress = 0
            val task = async {
                LocalTransactionCategorization(rules, 0.5f).categorize(entries, listOf(expense), { _, _ -> progress++ }) {
                    calls++
                    currentCoroutineContext().cancel()
                    response
                }
            }
            assertTrue(runCatching { task.await() }.exceptionOrNull() is CancellationException)
            assertEquals(1, calls)
            assertEquals(0, rules.count)
            assertEquals(0, progress)
        }
    }

    @Test fun cancelledInferenceFailureIsNotSwallowedAsMalformedOutput() = runTest {
        val rules = Rules(expense)
        var calls = 0
        val task = async {
            LocalTransactionCategorization(rules, 0.5f).categorize(entries, listOf(expense), { _, _ -> }) {
                calls++
                currentCoroutineContext().cancel()
                throw IllegalStateException("native inference failed after cancellation")
            }
        }
        assertTrue(runCatching { task.await() }.exceptionOrNull() is CancellationException)
        assertEquals(1, calls)
        assertEquals(0, rules.count)
    }

    @Test fun cancellingAtProgressStopsBeforeTheNextChunk() = runTest {
        val rules = Rules(expense)
        var calls = 0
        val task = async {
            val context = currentCoroutineContext()
            LocalTransactionCategorization(rules, 0.5f).categorize(entries, listOf(expense), { _, _ ->
                context.cancel()
            }) { calls++; "[]" }
        }
        assertTrue(runCatching { task.await() }.exceptionOrNull() is CancellationException)
        assertEquals(1, calls)
        assertEquals(20, rules.count)
    }

    @Test fun ruleFallbackAlsoStopsWhenCancelledByProgress() = runTest {
        val rules = RuleBasedLocalAiRepository(br.com.otavioesteves.finances.data.DefaultDateProvider())
        var progress = 0
        val task = async {
            val context = currentCoroutineContext()
            rules.categorize(entries, listOf(expense)) { _, _ ->
                progress++
                context.cancel()
            }
        }
        assertTrue(runCatching { task.await() }.exceptionOrNull() is CancellationException)
        assertEquals(1, progress)
    }
}
