package br.com.otavioesteves.finances.data.repository

import androidx.room.withTransaction
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.dao.StatementImportDao
import br.com.otavioesteves.finances.data.local.mapper.toDomain
import br.com.otavioesteves.finances.data.local.mapper.toEntity
import br.com.otavioesteves.finances.domain.model.ImportedStatement
import br.com.otavioesteves.finances.domain.model.DuplicateStatementImportException
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.scopedStatementFingerprint
import br.com.otavioesteves.finances.domain.model.statementImportSourceKey
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
        fingerprint: String,
        legacyFingerprint: String?
    ) {
        confirmReviewedImport(statementImport, transactions, fingerprint, legacyFingerprint, emptyList())
    }

    override suspend fun undoImport(importId: Long) {
        database.withTransaction {
            val batch = requireNotNull(statementImportDao.findById(importId)) { "Importação não encontrada" }
            require(batch.canUndo) { "Esta importação antiga não possui vínculos para desfazer com segurança." }
            statementImportDao.deleteImportedTransactions(importId)
            statementImportDao.deleteById(importId)
        }
    }

    override suspend fun confirmReviewedImport(
        statementImport: ImportedStatement, transactions: List<Transaction>, fingerprint: String,
        legacyFingerprint: String?, memories: List<br.com.otavioesteves.finances.domain.model.CategoryMemory>,
        additionalLegacyFingerprints: List<String>
    ) {
        database.withTransaction {
            val sourceKey = statementImportSourceKey(statementImport.sourceLabel)
            val candidates = (listOfNotNull(fingerprint, legacyFingerprint) + additionalLegacyFingerprints).distinct()
            val duplicate = candidates.any { contentFingerprint ->
                statementImportDao.hasFingerprint(scopedStatementFingerprint(contentFingerprint, statementImport.sourceLabel)) ||
                    statementImportDao.findByFingerprint(contentFingerprint)?.let {
                        statementImportSourceKey(it.sourceLabel) == sourceKey
                    } == true
            }
            if (duplicate) {
                throw DuplicateStatementImportException()
            }
            // The unique fingerprint claim and all inserts commit or roll back together.
            val importId = statementImportDao.insertImport(statementImport.toEntity().copy(
                fingerprint = scopedStatementFingerprint(fingerprint, statementImport.sourceLabel), canUndo = true
            ))
            database.transactionDao().insertTransactions(transactions.map {
                it.toEntity().copy(importId = importId, sourceLabel = statementImport.sourceLabel,
                    origin = br.com.otavioesteves.finances.domain.model.TransactionOrigin.IMPORTED)
            })
            database.categoryMemoryDao().remember(memories.map {
                br.com.otavioesteves.finances.data.local.entity.CategoryMemoryEntity(it.descriptionKey, it.inputType, it.categoryId)
            })
        }
    }
}
