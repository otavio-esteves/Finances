package br.com.otavioesteves.finances.data.repository

import androidx.room.withTransaction
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.dao.StatementImportDao
import br.com.otavioesteves.finances.data.local.mapper.toDomain
import br.com.otavioesteves.finances.data.local.mapper.toEntity
import br.com.otavioesteves.finances.domain.model.ImportedStatement
import br.com.otavioesteves.finances.domain.model.DuplicateStatementImportException
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.repository.StatementImportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomStatementImportRepository(
    private val database: AppDatabase
) : StatementImportRepository {

    private val statementImportDao: StatementImportDao = database.statementImportDao()

    override fun getImports(): Flow<List<ImportedStatement>> {
        return statementImportDao.getAllImports().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun confirmImport(
        statementImport: ImportedStatement,
        transactions: List<Transaction>,
        fingerprint: String
    ) {
        database.withTransaction {
            if (statementImportDao.hasFingerprint(fingerprint)) {
                throw DuplicateStatementImportException()
            }
            // The unique fingerprint claim and all inserts commit or roll back together.
            statementImportDao.insertImport(statementImport.toEntity().copy(fingerprint = fingerprint))
            database.transactionDao().insertTransactions(transactions.map { it.toEntity() })
        }
    }
}
