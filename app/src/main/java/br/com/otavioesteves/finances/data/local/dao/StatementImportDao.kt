package br.com.otavioesteves.finances.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import br.com.otavioesteves.finances.data.local.entity.StatementImportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StatementImportDao {
    @Query("SELECT * FROM statement_imports ORDER BY id ASC")
    suspend fun getAllImportsForBackup(): List<StatementImportEntity>

    @Query("SELECT * FROM statement_imports ORDER BY importedAt DESC")
    fun getAllImports(): Flow<List<StatementImportEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM statement_imports WHERE fingerprint = :fingerprint)")
    suspend fun hasFingerprint(fingerprint: String): Boolean

    @Insert
    suspend fun insertImport(statementImport: StatementImportEntity): Long

    @Insert
    suspend fun insertImports(statementImports: List<StatementImportEntity>)

    @Query("DELETE FROM statement_imports")
    suspend fun deleteAll()
}
