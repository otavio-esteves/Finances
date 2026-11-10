package br.com.otavioesteves.finances.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import br.com.otavioesteves.finances.data.local.entity.StatementImportEntity
import kotlinx.coroutines.flow.Flow
import br.com.otavioesteves.finances.domain.model.requirePersistableText

@Dao
interface StatementImportDao {
    @Query("SELECT * FROM statement_imports ORDER BY id ASC")
    suspend fun getAllImportsForBackup(): List<StatementImportEntity>

    @Query("SELECT * FROM statement_imports ORDER BY importedAt DESC")
    fun getAllImports(): Flow<List<StatementImportEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM statement_imports WHERE fingerprint = :fingerprint)")
    suspend fun hasFingerprint(fingerprint: String): Boolean

    @Query("SELECT * FROM statement_imports WHERE fingerprint IN (:fingerprints)")
    suspend fun findByFingerprints(fingerprints: List<String>): List<StatementImportEntity>

    suspend fun insertImport(statementImport: StatementImportEntity): Long {
        validateText(statementImport)
        return insertImportUnchecked(statementImport)
    }

    suspend fun insertImports(statementImports: List<StatementImportEntity>) {
        statementImports.forEach(::validateText)
        insertImportsUnchecked(statementImports)
    }

    private fun validateText(item: StatementImportEntity) {
        requirePersistableText(item.fileName, "nome do arquivo")
        requirePersistableText(item.sourceLabel, "conta/cartão")
        requirePersistableText(item.fingerprint, "identificação da importação")
    }

    @Insert
    suspend fun insertImportUnchecked(statementImport: StatementImportEntity): Long

    @Insert
    suspend fun insertImportsUnchecked(statementImports: List<StatementImportEntity>)

    @Query("SELECT * FROM statement_imports WHERE id = :id")
    suspend fun findById(id: Long): StatementImportEntity?

    @Query("DELETE FROM statement_imports WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM statement_imports")
    suspend fun deleteAll()
}
