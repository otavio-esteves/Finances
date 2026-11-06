package br.com.otavioesteves.finances.domain.repository

import br.com.otavioesteves.finances.domain.model.ImportedStatement
import br.com.otavioesteves.finances.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface StatementImportRepository {
    fun getImports(): Flow<List<ImportedStatement>>
    suspend fun undoImport(importId: Long) { error("Desfazer indisponível") }
    suspend fun confirmReviewedImport(statementImport: ImportedStatement, transactions: List<Transaction>, fingerprint: String, legacyFingerprint: String?, memories: List<br.com.otavioesteves.finances.domain.model.CategoryMemory>) {
        confirmImport(statementImport, transactions, fingerprint, legacyFingerprint)
    }
    suspend fun confirmImport(statementImport: ImportedStatement, transactions: List<Transaction>, fingerprint: String, legacyFingerprint: String? = null)
}
