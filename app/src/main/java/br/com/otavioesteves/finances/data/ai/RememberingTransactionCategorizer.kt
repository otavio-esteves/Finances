package br.com.otavioesteves.finances.data.ai

import br.com.otavioesteves.finances.domain.ai.TransactionCategorizer
import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.domain.repository.CategoryMemoryRepository
import kotlinx.coroutines.CancellationException

/** Resolve explicit user choices first and send only unknown descriptions to inference. */
class RememberingTransactionCategorizer(
    private val delegate: TransactionCategorizer,
    private val memory: CategoryMemoryRepository
) : TransactionCategorizer {
    override suspend fun categorize(entries: List<RawStatementEntry>, categories: List<Category>,
        onProgress: (Int, Int) -> Unit): Result<List<CategorySuggestion>> = try {
        val choices = memory.getMemories().associateBy { it.descriptionKey to it.inputType }
        val categoriesById = categories.associateBy { it.id }
        val remembered = entries.map { entry ->
            choices[categoryMemoryKey(entry.description) to entry.type]?.let { categoriesById[it.categoryId] }?.let { category ->
                CategorySuggestion(entry.copy(type = TransactionType.valueOf(category.type.name)), category, 1f,
                    SuggestionSource.MEMORY, originalType = entry.type, originalEntry = entry)
            }
        }
        val knownCount = remembered.count { it != null }
        onProgress(knownCount, entries.size)
        val unknown = entries.filterIndexed { index, _ -> remembered[index] == null }
        val inferred = if (unknown.isEmpty()) emptyList() else delegate.categorize(unknown, categories) { done, _ ->
            onProgress(knownCount + done, entries.size)
        }.getOrThrow()
        require(inferred.size == unknown.size) { "Categorização incompleta" }
        val iterator = inferred.iterator()
        Result.success(remembered.map { it ?: iterator.next() })
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { Result.failure(e) }
}
