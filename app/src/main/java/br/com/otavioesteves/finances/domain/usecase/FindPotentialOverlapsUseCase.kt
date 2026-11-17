package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.LocalDate
import java.util.RandomAccess

/** Matches by date, magnitude and description; the user decides whether they are the same purchase. */
class FindPotentialOverlapsUseCase(private val transactionsRepository: TransactionsRepository) {
    suspend operator fun invoke(entries: List<RawStatementEntry>): Int = findMatches(entries).size

    suspend fun findMatches(entries: List<RawStatementEntry>): Map<Int, List<Transaction>> {
        val context = currentCoroutineContext()
        val existing = mutableMapOf<Triple<LocalDate, Long, String>, MutableList<Transaction>>()
        val seenIds = hashSetOf<Long>()
        for (period in entries.map { MonthPeriod(it.date.year, it.date.monthValue) }.distinct()) {
            context.ensureActive()
            for (transaction in transactionsRepository.getTransactions(period).first()) {
                context.ensureActive()
                if (seenIds.add(transaction.id)) {
                    val key = Triple(transaction.date, transaction.amount.cents, normalizeDescription(transaction.description))
                    existing.getOrPut(key) { mutableListOf() }.add(transaction)
                }
            }
        }
        val result = mutableMapOf<Int, List<Transaction>>()
        entries.forEachIndexed { index, entry ->
            context.ensureActive()
            val groups = listOfNotNull(entry.description, entry.legacyDecodedOfxDescription, entry.legacyOfxDescription,
                entry.legacyPdfDescription).map(::normalizeDescription).distinct().mapNotNull { description ->
                existing[Triple(entry.date, entry.amount.cents, description)]
            }
            // Historical IDs are unique and normalized descriptions form disjoint groups.
            // Share the groups instead of copying every candidate for every incoming row.
            when (groups.size) {
                0 -> Unit
                1 -> result[index] = groups.single()
                else -> result[index] = CombinedMatches(groups)
            }
        }
        return result
    }

    private fun normalizeDescription(value: String): String = value.trim().lowercase()
        .replace(WHITESPACE, " ")

    private class CombinedMatches(private val groups: List<List<Transaction>>) : AbstractList<Transaction>(), RandomAccess {
        override val size = groups.sumOf { it.size }
        override fun get(index: Int): Transaction {
            if (index !in 0 until size) throw IndexOutOfBoundsException("Index: $index, size: $size")
            var offset = index
            for (group in groups) {
                if (offset < group.size) return group[offset]
                offset -= group.size
            }
            throw IndexOutOfBoundsException("Index: $index, size: $size")
        }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
