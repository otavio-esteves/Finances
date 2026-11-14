package br.com.otavioesteves.finances.domain.repository

import br.com.otavioesteves.finances.domain.model.AnnualTransactionSummary
import br.com.otavioesteves.finances.domain.model.summarizeAnnualTransactions
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionEditSnapshot
import br.com.otavioesteves.finances.domain.model.StaleTransactionEditException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine

interface TransactionsRepository {
    val dataRevision: Long get() = 0L
    fun hasTransactions(): Flow<Boolean>
    fun getTransaction(id: Long): Flow<Transaction?>
    // Repositories supporting dataset replacement must capture and check the revision atomically.
    suspend fun getTransactionForEditing(id: Long): TransactionEditSnapshot? =
        getTransaction(id).first()?.let { TransactionEditSnapshot(it, dataRevision) }
    fun getTransactions(period: MonthPeriod): Flow<List<Transaction>>
    fun getTransactionsForYear(year: Int): Flow<List<Transaction>> =
        combine((1..12).map { month -> getTransactions(MonthPeriod(year, month)) }) { months ->
            months.flatMap { it }
        }
    fun getAnnualSummary(year: Int, trackedNote: String? = null): Flow<AnnualTransactionSummary> =
        getTransactionsForYear(year).map { summarizeAnnualTransactions(it, year, trackedNote) }
    fun getRecentTransactions(period: MonthPeriod, limit: Int): Flow<List<Transaction>> =
        getTransactions(period).map { it.take(limit) }
    fun getMonthlyBalance(period: MonthPeriod): Flow<Money>
    suspend fun addTransaction(transaction: Transaction)
    suspend fun removeTransaction(transactionId: Long)
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun updateTransactionIfCurrent(transaction: Transaction, dataRevision: Long) {
        if (this.dataRevision != dataRevision) throw StaleTransactionEditException()
        updateTransaction(transaction)
    }
    suspend fun removeTransactionIfCurrent(transactionId: Long, dataRevision: Long) {
        if (this.dataRevision != dataRevision) throw StaleTransactionEditException()
        removeTransaction(transactionId)
    }
}
