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

    @Query("SELECT * FROM statement_imports WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun findByFingerprint(fingerprint: String): StatementImportEntity?

    @Insert
    suspend fun insertImport(statementImport: StatementImportEntity): Long

    @Insert
    suspend fun insertImports(statementImports: List<StatementImportEntity>)

    @Query("SELECT * FROM statement_imports WHERE id = :id")
    suspend fun findById(id: Long): StatementImportEntity?

    @Query("DELETE FROM statement_imports WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions WHERE importId = :id")
    suspend fun deleteImportedTransactions(id: Long)

    @Query("DELETE FROM statement_imports")
    suspend fun deleteAll()
}
