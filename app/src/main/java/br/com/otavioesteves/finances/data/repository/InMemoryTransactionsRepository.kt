package br.com.otavioesteves.finances.data.repository

import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.sumMoney
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

    override fun getMonthlyBalance(period: MonthPeriod): Flow<Money> {
        return getTransactions(period).map { transactions ->
            transactions
                .map { it.toBalanceContribution() }
                .sumMoney()
        }
    }

    private fun Transaction.toBalanceContribution(): Money {
        return when (type) {
            br.com.otavioesteves.finances.domain.model.TransactionType.INCOME -> amount
            br.com.otavioesteves.finances.domain.model.TransactionType.EXPENSE -> -amount
            br.com.otavioesteves.finances.domain.model.TransactionType.TRANSFER -> Money.Zero
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
