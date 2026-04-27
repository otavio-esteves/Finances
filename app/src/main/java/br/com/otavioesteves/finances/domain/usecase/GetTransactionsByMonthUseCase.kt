package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import kotlinx.coroutines.flow.Flow

class GetTransactionsByMonthUseCase(
    private val repository: TransactionsRepository
) {
    operator fun invoke(period: MonthPeriod): Flow<List<Transaction>> {
        return repository.getTransactions(period)
    }
}
