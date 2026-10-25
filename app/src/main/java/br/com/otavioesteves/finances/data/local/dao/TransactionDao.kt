package br.com.otavioesteves.finances.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionMonthAggregateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)
}
