package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import kotlinx.coroutines.flow.first

/** Counts likely duplicates across imported batches without blocking legitimate repeated purchases. */
class FindPotentialOverlapsUseCase(private val transactionsRepository: TransactionsRepository) {
    suspend operator fun invoke(entries: List<RawStatementEntry>): Int {
        val existing = entries.map { MonthPeriod(it.date.year, it.date.monthValue) }.distinct()
            .flatMap { transactionsRepository.getTransactions(it).first() }
            .map { Triple(it.date, it.amount.cents, normalizeDescription(it.description)) }
            .toSet()
        return entries.count {
            Triple(it.date, it.amount.cents, normalizeDescription(it.description)) in existing
        }
    }

    private fun normalizeDescription(value: String): String = value.trim().lowercase()
        .replace(Regex("\\s+"), " ")
}
