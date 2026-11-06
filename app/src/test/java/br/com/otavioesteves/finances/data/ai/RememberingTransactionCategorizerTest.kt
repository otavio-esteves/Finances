package br.com.otavioesteves.finances.data.ai

import br.com.otavioesteves.finances.domain.ai.TransactionCategorizer
import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.domain.repository.CategoryMemoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RememberingTransactionCategorizerTest {
    private val food = Category(1, "Alimentação", CategoryType.EXPENSE)
    private val transfer = Category(2, "Transferência", CategoryType.TRANSFER)
    private fun entry(name: String, type: TransactionType = TransactionType.EXPENSE) =
        RawStatementEntry(name, Money(100), LocalDate.of(2026, 11, 1), type)
    private fun memory(vararg entries: CategoryMemory) = object : CategoryMemoryRepository {
        override suspend fun getMemories() = entries.toList()
        override suspend fun clear() {}
    }
    @Test fun onlyUnknownEntriesReachInferenceAndOriginalOrderIsPreserved() = runTest {
        val delegated = mutableListOf<RawStatementEntry>()
        val delegate = object : TransactionCategorizer {
            override suspend fun categorize(entries: List<RawStatementEntry>, categories: List<Category>, onProgress: (Int, Int) -> Unit): Result<List<CategorySuggestion>> {
                delegated += entries
                onProgress(entries.size, entries.size)
                return Result.success(entries.map { CategorySuggestion(it, food, 0.7f) })
            }
        }
        val progress = mutableListOf<Int>()
        val categorizer = RememberingTransactionCategorizer(delegate, memory(CategoryMemory("cafe 123", TransactionType.EXPENSE, 1)))
        val result = categorizer.categorize(listOf(entry("  CAFÉ   123 "), entry("Outro"), entry("cafe 123")), listOf(food)) { done, total ->
            assertEquals(3, total); progress += done
        }.getOrThrow()
        assertEquals(listOf("Outro"), delegated.map { it.description })
        assertEquals(listOf(SuggestionSource.MEMORY, SuggestionSource.RULE, SuggestionSource.MEMORY), result.map { it.source })
        assertEquals(listOf(2, 3), progress)
    }
    @Test fun remembersTypeCorrectionWithoutOverwritingOriginalFingerprintInputs() = runTest {
        val delegate = object : TransactionCategorizer {
            override suspend fun categorize(entries: List<RawStatementEntry>, categories: List<Category>, onProgress: (Int, Int) -> Unit): Result<List<CategorySuggestion>> = error("Should not infer")
        }
        val original = entry("Pagamento")
        val result = RememberingTransactionCategorizer(delegate, memory(CategoryMemory("pagamento", TransactionType.EXPENSE, 2)))
            .categorize(listOf(original), listOf(transfer)).getOrThrow().single()
        assertEquals(TransactionType.TRANSFER, result.entry.type)
        assertEquals(original, result.originalEntry)
        assertEquals(TransactionType.EXPENSE, result.originalType)
    }
    @Test fun cancellationIsPropagated() = runTest {
        val delegate = object : TransactionCategorizer {
            override suspend fun categorize(entries: List<RawStatementEntry>, categories: List<Category>, onProgress: (Int, Int) -> Unit): Result<List<CategorySuggestion>> = throw CancellationException()
        }
        try {
            RememberingTransactionCategorizer(delegate, memory()).categorize(listOf(entry("Outro")), listOf(food))
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
