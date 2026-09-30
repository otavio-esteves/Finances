package br.com.otavioesteves.finances.data.repository

import br.com.otavioesteves.finances.data.local.dao.TransactionDao
import br.com.otavioesteves.finances.data.local.mapper.toDomain
import br.com.otavioesteves.finances.data.local.mapper.toEntity
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomTransactionsRepository(
    private val transactionDao: TransactionDao
) : TransactionsRepository {

    override fun getTransactionsForYear(year: Int): Flow<List<Transaction>> {
        val startDate = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)
        return transactionDao.getTransactionsByDateRange(startDate.toString(), endDate.toString())
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> {
        val startDate = LocalDate.of(period.year, period.month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)

        return transactionDao.getTransactionsByDateRange(startDate.toString(), endDate.toString())
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getRecentTransactions(period: MonthPeriod, limit: Int): Flow<List<Transaction>> {
        require(limit > 0)
        val startDate = LocalDate.of(period.year, period.month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)
        return transactionDao.getRecentTransactionsByDateRange(startDate.toString(), endDate.toString(), limit)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getMonthlyBalance(period: MonthPeriod): Flow<Money> {
        val startDate = LocalDate.of(period.year, period.month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)

        return transactionDao.getMonthlyBalance(startDate.toString(), endDate.toString())
            .map { Money.fromCents(it ?: 0L) }
    }

    override suspend fun addTransaction(transaction: Transaction) {
        transactionDao.insertTransaction(transaction.toEntity())
    }

    override suspend fun removeTransaction(transactionId: Long) {
        transactionDao.deleteTransactionById(transactionId)
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.updateTransaction(transaction.toEntity())
    }
}
