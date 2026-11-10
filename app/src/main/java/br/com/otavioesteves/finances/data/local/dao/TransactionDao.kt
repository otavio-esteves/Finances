package br.com.otavioesteves.finances.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionMonthAggregateEntity
import br.com.otavioesteves.finances.data.local.mapper.toDomain
import br.com.otavioesteves.finances.domain.model.checkedMagnitudeTotal
import br.com.otavioesteves.finances.domain.model.requirePersistableText
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT EXISTS(SELECT 1 FROM transactions)")
    fun hasTransactions(): Flow<Boolean>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    fun getTransactionById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC, id DESC")
    fun getTransactionsByDateRange(startDate: String, endDate: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC, id DESC LIMIT :limit")
    fun getRecentTransactionsByDateRange(startDate: String, endDate: String, limit: Int): Flow<List<TransactionEntity>>

    @Query("""
        SELECT SUM(
            CASE 
                WHEN type = 'INCOME' THEN amountCents 
                WHEN type = 'EXPENSE' THEN -amountCents 
                ELSE 0 
            END
        ) FROM transactions 
        WHERE date >= :startDate AND date <= :endDate
    """)
    fun getMonthlyBalance(startDate: String, endDate: String): Flow<Long?>

    @Query("""
        SELECT SUM(CASE WHEN type = 'INCOME' THEN amountCents
            WHEN type = 'EXPENSE' THEN -amountCents ELSE 0 END)
        FROM transactions WHERE date >= :startDate AND date <= :endDate
    """)
    suspend fun getBalanceSnapshot(startDate: String, endDate: String): Long?

    @Query("""
        SELECT CAST(substr(date, 6, 2) AS INTEGER) AS month, categoryId,
            SUM(CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END) AS expenseCents,
            SUM(CASE WHEN type = 'EXPENSE' THEN 1 ELSE 0 END) AS expenseCount,
            SUM(CASE WHEN :trackedNote IS NOT NULL AND notes = :trackedNote THEN 1 ELSE 0 END) AS trackedNoteCount
        FROM transactions
        WHERE date >= :startDate AND date <= :endDate
            AND (type = 'EXPENSE' OR (:trackedNote IS NOT NULL AND notes = :trackedNote))
        GROUP BY month, categoryId
        ORDER BY month, categoryId
    """)
    fun getAnnualSummary(startDate: String, endDate: String, trackedNote: String?): Flow<List<TransactionMonthAggregateEntity>>

    @androidx.room.Transaction
    suspend fun insertTransaction(transaction: TransactionEntity) {
        val total = validateReplacement(listOf(transaction))
        insertTransactionUnchecked(transaction)
        saveMagnitudeTotal(total)
    }

    @androidx.room.Transaction
    suspend fun updateTransaction(transaction: TransactionEntity) {
        if (findAmount(transaction.id) == null) return
        val total = validateReplacement(listOf(transaction))
        updateTransactionUnchecked(transaction)
        saveMagnitudeTotal(total)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionUnchecked(transaction: TransactionEntity)

    @Update
    suspend fun updateTransactionUnchecked(transaction: TransactionEntity)

    @androidx.room.Transaction
    suspend fun deleteTransactionById(id: Long) {
        val total = magnitudeTotal()
        val amount = findAmount(id) ?: return
        deleteTransactionByIdUnchecked(id)
        saveMagnitudeTotal(total - amount)
    }

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionByIdUnchecked(id: Long)

    @androidx.room.Transaction
    suspend fun deleteAll() {
        deleteAllUnchecked()
        saveMagnitudeTotal(0)
    }

    @Query("DELETE FROM transactions")
    suspend fun deleteAllUnchecked()

    @androidx.room.Transaction
    suspend fun deleteImportedTransactions(id: Long) {
        val total = magnitudeTotal()
        val removed = importedMagnitude(id) ?: 0L
        deleteImportedTransactionsUnchecked(id)
        saveMagnitudeTotal(total - removed)
    }

    @Query("SELECT SUM(amountCents) FROM transactions WHERE importId = :id")
    suspend fun importedMagnitude(id: Long): Long?

    @Query("DELETE FROM transactions WHERE importId = :id")
    suspend fun deleteImportedTransactionsUnchecked(id: Long)

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @androidx.room.Transaction
    suspend fun insertTransactions(transactions: List<TransactionEntity>) {
        val total = validateReplacement(transactions)
        insertTransactionsUnchecked(transactions)
        saveMagnitudeTotal(total)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionsUnchecked(transactions: List<TransactionEntity>)

    @Query("SELECT amountCents FROM transactions WHERE id = :id")
    suspend fun findAmount(id: Long): Long?

    @Query("SELECT amountCents FROM transaction_total WHERE id = 1")
    suspend fun storedMagnitudeTotal(): Long?

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions")
    suspend fun initialMagnitudeTotal(): Long

    @Query("INSERT OR REPLACE INTO transaction_total (id, amountCents) VALUES (1, :total)")
    suspend fun saveMagnitudeTotal(total: Long)

    // A fresh in-memory database may not have a seed row; initialize once.
    private suspend fun magnitudeTotal(): Long = storedMagnitudeTotal() ?: initialMagnitudeTotal().also {
        saveMagnitudeTotal(it)
    }

    private suspend fun validateReplacement(transactions: List<TransactionEntity>): Long {
        transactions.forEach { it.toDomain().requirePersistableText() }
        val replacingIds = transactions.filter { it.id > 0 }.map { it.id }
        require(replacingIds.distinct().size == replacingIds.size) { "Transações repetidas no mesmo lote." }
        var remaining = magnitudeTotal()
        replacingIds.forEach { remaining -= findAmount(it) ?: 0L }
        return checkedMagnitudeTotal(sequenceOf(remaining) + transactions.asSequence().map { it.amountCents }).cents
    }
}
