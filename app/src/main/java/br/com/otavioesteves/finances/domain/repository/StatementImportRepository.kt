package br.com.otavioesteves.finances.domain.repository

import br.com.otavioesteves.finances.domain.model.ImportedStatement
import br.com.otavioesteves.finances.domain.model.CategoryMemory
import br.com.otavioesteves.finances.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface StatementImportRepository {
    /** Capture the dataset generation before reading categories or checking overlaps. */
    suspend fun getDataRevision(): Long = 0L
    fun getImports(): Flow<List<ImportedStatement>>
    /** Undo only the batch from the displayed dataset generation. */
    suspend fun undoImport(statementImport: ImportedStatement) { error("Desfazer indisponível") }
    suspend fun confirmReviewedImport(
        statementImport: ImportedStatement,
        transactions: List<Transaction>,
        fingerprint: String,
        legacyFingerprints: List<String>,
        memories: List<CategoryMemory>
    ) {
        if (statementImport.dataRevision == null || statementImport.dataRevision != getDataRevision()) {
            throw br.com.otavioesteves.finances.domain.model.StaleStatementImportException()
        }
        confirmImport(statementImport, transactions, fingerprint, legacyFingerprints)
    }
    suspend fun confirmImport(
        statementImport: ImportedStatement,
        transactions: List<Transaction>,
        fingerprint: String,
        legacyFingerprints: List<String> = emptyList()
    )
}
