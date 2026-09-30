package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import kotlinx.coroutines.flow.Flow

class GetTransactionsByMonthUseCase(
    private val repository: TransactionsRepository
) {
    operator fun invoke(period: MonthPeriod, limit: Int? = null): Flow<List<Transaction>> {
        return if (limit == null) repository.getTransactions(period)
        else repository.getRecentTransactions(period, limit)
    }
}
