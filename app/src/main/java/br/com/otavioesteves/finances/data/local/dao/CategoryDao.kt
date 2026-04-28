package br.com.otavioesteves.finances.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.CategorySummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("""
        SELECT c.id, c.name, c.type, SUM(t.amountCents) as totalAmountCents
        FROM categories c
        JOIN transactions t ON c.id = t.categoryId
        WHERE t.date >= :startDate AND t.date <= :endDate AND t.type != 'TRANSFER'
        GROUP BY c.id
    """)
    fun getCategorySummaries(startDate: String, endDate: String): Flow<List<CategorySummaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoriesCount(): Int

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
