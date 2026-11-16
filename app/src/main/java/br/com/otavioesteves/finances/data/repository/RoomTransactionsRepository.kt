package br.com.otavioesteves.finances.data.repository

import br.com.otavioesteves.finances.data.local.dao.TransactionDao
import br.com.otavioesteves.finances.data.local.mapper.toDomain
import br.com.otavioesteves.finances.data.local.mapper.toEntity
import br.com.otavioesteves.finances.domain.model.AnnualTransactionSummary
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionEditSnapshot
import br.com.otavioesteves.finances.domain.model.StaleTransactionEditException
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomTransactionsRepository(
    private val transactionDao: TransactionDao,
    private val database: br.com.otavioesteves.finances.data.local.AppDatabase? = null
) : TransactionsRepository {
    override val dataRevision: Long get() = database?.dataRevision ?: 0L

    override fun hasTransactions(): Flow<Boolean> = transactionDao.hasTransactions().distinctUntilChanged()

    override fun getTransaction(id: Long): Flow<Transaction?> =
        transactionDao.getTransactionById(id).distinctUntilChanged().map { it?.toDomain() }

    override suspend fun getTransactionForEditing(id: Long): TransactionEditSnapshot? {
        val db = database ?: return transactionDao.getTransactionById(id).first()?.toDomain()
            ?.let { TransactionEditSnapshot(it, 0) }
        return db.withTransaction {
            transactionDao.getTransactionById(id).first()?.toDomain()
                ?.let { TransactionEditSnapshot(it, db.dataRevision) }
        }
    }

    override fun getTransactionsForYear(year: Int): Flow<List<Transaction>> {
        val startDate = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)
        return transactionDao.getTransactionsByDateRange(startDate.toString(), endDate.toString())
            .distinctUntilChanged()
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getAnnualSummary(year: Int, trackedNote: String?): Flow<AnnualTransactionSummary> {
        val startDate = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)
        return transactionDao.getAnnualSummary(startDate.toString(), endDate.toString(), trackedNote)
            .distinctUntilChanged()
            .map { rows ->
                val monthly = LongArray(12)
                val byCategory = mutableMapOf<Long, LongArray>()
                var noteCount = 0
                rows.forEach { row ->
                    noteCount = Math.addExact(noteCount, row.trackedNoteCount)
                    if (row.expenseCount > 0) {
                        val month = row.month - 1
                        monthly[month] = Math.addExact(monthly[month], row.expenseCents)
                        byCategory.getOrPut(row.categoryId) { LongArray(12) }[month] = row.expenseCents
                    }
                }
                AnnualTransactionSummary(year, monthly.toList(), byCategory.mapValues { it.value.toList() }, noteCount)
            }
    }

    override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> {
        val startDate = LocalDate.of(period.year, period.month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)

        return transactionDao.getTransactionsByDateRange(startDate.toString(), endDate.toString())
            .distinctUntilChanged()
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getRecentTransactions(period: MonthPeriod, limit: Int): Flow<List<Transaction>> {
        require(limit > 0)
        val startDate = LocalDate.of(period.year, period.month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)
        return transactionDao.getRecentTransactionsByDateRange(startDate.toString(), endDate.toString(), limit)
            .distinctUntilChanged()
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getMonthlyBalance(period: MonthPeriod): Flow<Money> {
        val startDate = LocalDate.of(period.year, period.month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)

        return transactionDao.getMonthlyBalance(startDate.toString(), endDate.toString())
            .distinctUntilChanged()
            .map { Money.fromCents(it ?: 0L) }
    }

    override suspend fun addTransaction(transaction: Transaction) {
        transactionDao.insertTransaction(transaction.toEntity())
    }

    override suspend fun addExamples2026IfCurrent(today: LocalDate, dataRevision: Long) {
        val db = requireNotNull(database) { "A inserção protegida exige o banco de dados." }
        db.withTransaction {
            if (db.dataRevision != dataRevision) throw StaleTransactionEditException()
            val categories = db.categoryDao().getAllCategoriesForBackup().map { it.toDomain() }
            val samples = br.com.otavioesteves.finances.domain.usecase.exampleTransactions2026(categories, today)
            val existing = transactionDao.getTransactionsByNote("2026-01-01", "2026-12-31",
                br.com.otavioesteves.finances.domain.usecase.EXAMPLE_TRANSACTIONS_NOTE)
                .map { java.time.YearMonth.from(it.date) to it.description }.toSet()
            // Keep the monthly identity of older examples whose dates were clamped on insertion.
            val missing = samples.filterNot { (java.time.YearMonth.from(it.date) to it.description) in existing }
            transactionDao.insertTransactions(missing.map { it.toEntity() })
        }
    }

    override suspend fun removeTransaction(transactionId: Long) {
        transactionDao.deleteTransactionById(transactionId)
    }

    override suspend fun removeTransactionsByNoteIfCurrent(year: Int, note: String, dataRevision: Long) {
        require(year in 1..9999)
        val db = requireNotNull(database) { "A exclusão protegida exige o banco de dados." }
        db.withTransaction {
            if (db.dataRevision != dataRevision) throw StaleTransactionEditException()
            transactionDao.deleteTransactionsByNote(
                LocalDate.of(year, 1, 1).toString(), LocalDate.of(year, 12, 31).toString(), note
            )
        }
    }

    override suspend fun removeTransactionIfCurrent(transactionId: Long, dataRevision: Long) {
        val db = requireNotNull(database) { "A exclusão protegida exige o banco de dados." }
        db.withTransaction {
            if (db.dataRevision != dataRevision) throw StaleTransactionEditException()
            transactionDao.deleteTransactionById(transactionId)
        }
    }

    override suspend fun updateTransactionIfCurrent(transaction: Transaction, dataRevision: Long) {
        val db = requireNotNull(database) { "A edição protegida exige o banco de dados." }
        db.withTransaction {
            if (db.dataRevision != dataRevision) throw StaleTransactionEditException()
            updateTransaction(transaction)
        }
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        val db = database
        if (db == null) {
            transactionDao.updateTransaction(transaction.toEntity())
            return
        }
        db.withTransaction {
            val original = requireNotNull(transactionDao.getTransactionById(transaction.id).first()) { "Transação não encontrada" }
            val categoryChanged = original.categoryId != transaction.categoryId || original.type != transaction.type
            val memoryKey = original.categoryMemoryDescriptionKey
                ?: br.com.otavioesteves.finances.domain.model.categoryMemoryKey(original.description)
            val inputType = original.categoryMemoryInputType ?: original.type
            // Keep batch ownership and the first input key through every later edit.
            transactionDao.updateTransaction(transaction.toEntity().copy(
                importId = original.importId,
                origin = original.origin,
                categoryMemoryDescriptionKey = original.categoryMemoryDescriptionKey ?: memoryKey.takeIf { categoryChanged },
                categoryMemoryInputType = original.categoryMemoryInputType ?: inputType.takeIf { categoryChanged }
            ))
            if (categoryChanged) {
                db.categoryMemoryDao().remember(listOf(br.com.otavioesteves.finances.data.local.entity.CategoryMemoryEntity(
                    memoryKey, inputType, transaction.categoryId
                )))
            }
        }
    }
}
