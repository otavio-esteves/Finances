package br.com.otavioesteves.finances.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.otavioesteves.finances.data.local.entity.CategoryMemoryEntity

@Dao
interface CategoryMemoryDao {
    @Query("SELECT * FROM category_memories ORDER BY descriptionKey, inputType")
    suspend fun getAll(): List<CategoryMemoryEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun remember(memories: List<CategoryMemoryEntity>)
    @Query("DELETE FROM category_memories")
    suspend fun clear()
}
