package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetTransactionUseCase(
    private val repository: TransactionsRepository
) {
    operator fun invoke(id: Long, period: br.com.otavioesteves.finances.domain.model.MonthPeriod): Flow<Transaction?> {
        return repository.getTransactions(period).map { transactions ->
            transactions.find { it.id == id }
        }
    }
}
