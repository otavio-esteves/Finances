package br.com.otavioesteves.finances.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import br.com.otavioesteves.finances.data.local.entity.StatementImportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StatementImportDao {
    @Query("SELECT * FROM statement_imports ORDER BY importedAt DESC")
    fun getAllImports(): Flow<List<StatementImportEntity>>

    @Insert
    suspend fun insertImport(statementImport: StatementImportEntity): Long
}
