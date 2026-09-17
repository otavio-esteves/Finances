package br.com.otavioesteves.finances.domain.repository

import br.com.otavioesteves.finances.domain.model.ImportedStatement
import kotlinx.coroutines.flow.Flow

interface StatementImportRepository {
    fun getImports(): Flow<List<ImportedStatement>>
    suspend fun addImport(statementImport: ImportedStatement)
}
