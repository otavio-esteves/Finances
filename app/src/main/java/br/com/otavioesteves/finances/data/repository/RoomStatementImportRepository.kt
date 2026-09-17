package br.com.otavioesteves.finances.data.repository

import br.com.otavioesteves.finances.data.local.dao.StatementImportDao
import br.com.otavioesteves.finances.data.local.mapper.toDomain
import br.com.otavioesteves.finances.data.local.mapper.toEntity
import br.com.otavioesteves.finances.domain.model.ImportedStatement
import br.com.otavioesteves.finances.domain.repository.StatementImportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomStatementImportRepository(
    private val statementImportDao: StatementImportDao
) : StatementImportRepository {

    override fun getImports(): Flow<List<ImportedStatement>> {
        return statementImportDao.getAllImports().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun addImport(statementImport: ImportedStatement) {
        statementImportDao.insertImport(statementImport.toEntity())
    }
}
