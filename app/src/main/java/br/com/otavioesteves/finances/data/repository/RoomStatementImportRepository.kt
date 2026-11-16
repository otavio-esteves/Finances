package br.com.otavioesteves.finances.data.repository

import androidx.room.withTransaction
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.dao.StatementImportDao
import br.com.otavioesteves.finances.data.local.mapper.toDomain
import br.com.otavioesteves.finances.data.local.mapper.toEntity
import br.com.otavioesteves.finances.domain.model.ImportedStatement
import br.com.otavioesteves.finances.domain.model.DuplicateStatementImportException
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.statementSourceKey
import br.com.otavioesteves.finances.domain.repository.StatementImportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomStatementImportRepository(
    private val database: AppDatabase
) : StatementImportRepository {

    private val statementImportDao: StatementImportDao = database.statementImportDao()

    override suspend fun getDataRevision(): Long = database.withTransaction { database.dataRevision }

    override fun getImports(): Flow<List<ImportedStatement>> {
        return database.invalidationTracker.createFlow("statement_imports").map {
            database.withTransaction {
                val revision = database.dataRevision
                statementImportDao.getAllImportsForBackup().sortedByDescending { it.importedAt }
                    .map { it.toDomain().copy(dataRevision = revision) }
            }
        }
    }

    override suspend fun confirmImport(
        statementImport: ImportedStatement,
        transactions: List<Transaction>,
        fingerprint: String,
        legacyFingerprints: List<String>
    ) {
        database.withTransaction {
            confirmReviewedImport(
                statementImport.copy(dataRevision = statementImport.dataRevision ?: database.dataRevision),
                transactions, fingerprint, legacyFingerprints, emptyList()
            )
        }
    }

    override suspend fun undoImport(statementImport: ImportedStatement) {
        database.withTransaction {
            if (statementImport.dataRevision == null || statementImport.dataRevision != database.dataRevision) {
                throw br.com.otavioesteves.finances.domain.model.StaleStatementImportException()
            }
            val importId = statementImport.id
            val batch = requireNotNull(statementImportDao.findById(importId)) { "Importação não encontrada" }
            require(batch.toDomain() == statementImport.copy(dataRevision = null)) { "A importação foi alterada. Confira o histórico novamente." }
            require(batch.canUndo) { "Esta importação antiga não possui vínculos para desfazer com segurança." }
            database.transactionDao().deleteImportedTransactions(importId)
            statementImportDao.deleteById(importId)
        }
    }

    override suspend fun confirmReviewedImport(
        statementImport: ImportedStatement, transactions: List<Transaction>, fingerprint: String,
        legacyFingerprints: List<String>, memories: List<br.com.otavioesteves.finances.domain.model.CategoryMemory>
    ) {
        database.withTransaction {
            if (statementImport.dataRevision == null || statementImport.dataRevision != database.dataRevision) {
                throw br.com.otavioesteves.finances.domain.model.StaleStatementImportException()
            }
            val categories = database.categoryDao().getAllCategoriesForBackup().associateBy { it.id }
            require(transactions.all { categories[it.categoryId]?.type?.name == it.type.name }) {
                "As categorias foram alteradas. Selecione o arquivo novamente e confira os lançamentos."
            }
            require(memories.all { it.categoryId in categories }) { "Categoria memorizada inexistente." }
            val sourceKey = statementSourceKey(statementImport.sourceLabel)
            if (statementImportDao.hasFingerprint(fingerprint) ||
                statementImportDao.findByFingerprints(legacyFingerprints).any { statementSourceKey(it.sourceLabel) == sourceKey }
            ) {
                throw DuplicateStatementImportException()
            }
            // The unique fingerprint claim and all inserts commit or roll back together.
            val importId = statementImportDao.insertImport(statementImport.toEntity().copy(fingerprint = fingerprint, canUndo = true))
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
