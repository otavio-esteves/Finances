package br.com.otavioesteves.finances.domain.repository

import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine

interface TransactionsRepository {
    fun getTransaction(id: Long): Flow<Transaction?>
    fun getTransactions(period: MonthPeriod): Flow<List<Transaction>>
    fun getTransactionsForYear(year: Int): Flow<List<Transaction>> =
        combine((1..12).map { month -> getTransactions(MonthPeriod(year, month)) }) { months ->
            months.flatMap { it }
        }
    fun getRecentTransactions(period: MonthPeriod, limit: Int): Flow<List<Transaction>> =
        getTransactions(period).map { it.take(limit) }
    fun getMonthlyBalance(period: MonthPeriod): Flow<Money>
    suspend fun addTransaction(transaction: Transaction)
    suspend fun removeTransaction(transactionId: Long)
    suspend fun updateTransaction(transaction: Transaction)
}
