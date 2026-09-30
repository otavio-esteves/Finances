package br.com.otavioesteves.finances.domain.repository

import br.com.otavioesteves.finances.domain.model.ImportedStatement
import br.com.otavioesteves.finances.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface StatementImportRepository {
    fun getImports(): Flow<List<ImportedStatement>>
    suspend fun confirmImport(statementImport: ImportedStatement, transactions: List<Transaction>, fingerprint: String)
}
