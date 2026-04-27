package br.com.otavioesteves.finances.domain.repository

import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionsRepository {
    fun getTransactions(period: MonthPeriod): Flow<List<Transaction>>
    suspend fun addTransaction(transaction: Transaction)
    suspend fun removeTransaction(transactionId: Long)
    suspend fun updateTransaction(transaction: Transaction)
}
