package br.com.otavioesteves.finances.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.CategorySummaryEntity
import kotlinx.coroutines.flow.Flow
import br.com.otavioesteves.finances.domain.model.requirePersistableText

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun getAllCategoriesForBackup(): List<CategoryEntity>

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

    @Query("""
        SELECT c.id, c.name, c.type, SUM(t.amountCents) as totalAmountCents
        FROM categories c JOIN transactions t ON c.id = t.categoryId
        WHERE t.date >= :startDate AND t.date <= :endDate AND t.type != 'TRANSFER'
        GROUP BY c.id
    """)
    suspend fun getCategorySummarySnapshot(startDate: String, endDate: String): List<CategorySummaryEntity>

    suspend fun insertCategories(categories: List<CategoryEntity>) {
        categories.forEach { requirePersistableText(it.name, "nome da categoria") }
        insertCategoriesUnchecked(categories)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoriesUnchecked(categories: List<CategoryEntity>)

    @androidx.room.Transaction
    suspend fun createCategory(name: String, type: br.com.otavioesteves.finances.domain.model.CategoryType): CategoryEntity {
        val normalized = name.trim().replace(Regex("\\s+"), " ")
        require(normalized.isNotBlank()) { "Informe o nome da categoria." }
        require(normalized.length <= 80) { "Use até 80 caracteres no nome." }
        require(getAllCategoriesForBackup().none { it.type == type && it.name.equals(normalized, ignoreCase = true) }) {
            "Já existe uma categoria com esse nome e tipo."
        }
        val entity = CategoryEntity(name = normalized, type = type)
        return entity.copy(id = insertCategory(entity))
    }

    suspend fun insertCategory(category: CategoryEntity): Long {
        requirePersistableText(category.name, "nome da categoria")
        return insertCategoryUnchecked(category)
    }

    @Insert
    suspend fun insertCategoryUnchecked(category: CategoryEntity): Long

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoriesCount(): Int

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
