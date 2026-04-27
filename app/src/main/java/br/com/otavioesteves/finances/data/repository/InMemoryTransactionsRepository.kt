package br.com.otavioesteves.finances.data.repository

import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class InMemoryTransactionsRepository internal constructor(
    private val store: InMemoryFinanceStore = InMemoryRepositorySupport.sharedStore
) : TransactionsRepository {

    override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> {
        return store.transactions.map { transactions ->
            transactions.filter { transaction ->
                transaction.date.year == period.year && transaction.date.monthValue == period.month
            }.sortedBy { transaction -> transaction.date }
        }
    }

    override suspend fun addTransaction(transaction: Transaction) {
        store.addTransaction(transaction)
    }

    override suspend fun removeTransaction(transactionId: Long) {
        store.removeTransaction(transactionId)
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        store.updateTransaction(transaction)
    }
}
