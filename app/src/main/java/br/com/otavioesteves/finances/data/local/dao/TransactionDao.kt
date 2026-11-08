package br.com.otavioesteves.finances.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionMonthAggregateEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionAmountSnapshot
import br.com.otavioesteves.finances.data.local.mapper.toDomain
import br.com.otavioesteves.finances.domain.model.checkedMagnitudeTotal
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
        validateReplacement(listOf(transaction))
        insertTransactionUnchecked(transaction)
    }

    @androidx.room.Transaction
    suspend fun updateTransaction(transaction: TransactionEntity) {
        validateReplacement(listOf(transaction))
        updateTransactionUnchecked(transaction)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionUnchecked(transaction: TransactionEntity)

    @Update
    suspend fun updateTransactionUnchecked(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @androidx.room.Transaction
    suspend fun insertTransactions(transactions: List<TransactionEntity>) {
        validateReplacement(transactions)
        insertTransactionsUnchecked(transactions)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionsUnchecked(transactions: List<TransactionEntity>)

    @Query("SELECT id, amountCents FROM transactions")
    suspend fun getAmountSnapshots(): List<TransactionAmountSnapshot>

    // Called only by transactional wrappers so concurrent writes cannot both spend the same budget.
    private suspend fun validateReplacement(transactions: List<TransactionEntity>) {
        transactions.forEach { it.toDomain() }
        val replacingIds = transactions.filter { it.id > 0 }.map { it.id }
        require(replacingIds.distinct().size == replacingIds.size) { "Transações repetidas no mesmo lote." }
        val idSet = replacingIds.toSet()
        checkedMagnitudeTotal(getAmountSnapshots().asSequence().filterNot { it.id in idSet }.map { it.amountCents } +
            transactions.asSequence().map { it.amountCents })
    }
}
