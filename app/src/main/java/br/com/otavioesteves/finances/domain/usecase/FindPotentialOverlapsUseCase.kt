package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import kotlinx.coroutines.flow.first

/** Matches by date, magnitude and description; the user decides whether they are the same purchase. */
class FindPotentialOverlapsUseCase(private val transactionsRepository: TransactionsRepository) {
    suspend operator fun invoke(entries: List<RawStatementEntry>): Int = findMatches(entries).size

    suspend fun findMatches(entries: List<RawStatementEntry>): Map<Int, List<Transaction>> {
        val existing = entries.map { MonthPeriod(it.date.year, it.date.monthValue) }.distinct()
            .flatMap { transactionsRepository.getTransactions(it).first() }
            .groupBy { Triple(it.date, it.amount.cents, normalizeDescription(it.description)) }
        return entries.mapIndexedNotNull { index, entry ->
            existing[Triple(entry.date, entry.amount.cents, normalizeDescription(entry.description))]
                ?.let { index to it }
        }.toMap()
    }

    private fun normalizeDescription(value: String): String = value.trim().lowercase()
        .replace(Regex("\\s+"), " ")
}
