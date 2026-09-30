package br.com.otavioesteves.finances.data.repository

import androidx.room.withTransaction
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
import br.com.otavioesteves.finances.data.local.entity.StatementImportEntity
import br.com.otavioesteves.finances.data.local.entity.ChatMessageEntity
import br.com.otavioesteves.finances.domain.model.BackupModel
import br.com.otavioesteves.finances.domain.model.CategoryExportModel
import br.com.otavioesteves.finances.domain.model.TransactionExportModel
import br.com.otavioesteves.finances.domain.model.StatementImportExportModel
import br.com.otavioesteves.finances.domain.model.ChatMessageExportModel
import br.com.otavioesteves.finances.domain.repository.BackupRepository
import java.time.LocalDate
import java.time.LocalDateTime

class RoomBackupRepository(
    private val database: AppDatabase
) : BackupRepository {

    override suspend fun createBackup(): BackupModel = database.withTransaction {
        val categories = database.categoryDao().getAllCategoriesForBackup().map {
            CategoryExportModel(it.id, it.name, it.type)
        }
        val transactions = database.transactionDao().getAllTransactions().map {
            TransactionExportModel(
                id = it.id,
                description = it.description,
                amountCents = it.amountCents,
                categoryId = it.categoryId,
                date = it.date.toString(),
                type = it.type,
                notes = it.notes,
                origin = it.origin
            )
        }
        val statementImports = database.statementImportDao().getAllImportsForBackup().map {
            StatementImportExportModel(it.id, it.fileName, it.importedAt.toString(), it.transactionCount, it.fingerprint)
        }
        val chatMessages = database.chatMessageDao().getAllMessagesForBackup().map {
            ChatMessageExportModel(it.id, it.role, it.content, it.createdAt.toString())
        }
        BackupModel(
            categories = categories,
            transactions = transactions,
            statementImports = statementImports,
            chatMessages = chatMessages
        )
    }

    override suspend fun restoreBackup(backup: BackupModel) {
        database.withTransaction {
            val categoryIds = backup.categories.map { it.id }.toSet()
            require(categoryIds.size == backup.categories.size) { "Backup inválido: categorias duplicadas" }
            require(backup.transactions.all { it.categoryId in categoryIds }) {
                "Backup inválido: transação com categoria inexistente"
            }
            val fingerprints = backup.statementImports.mapNotNull { it.fingerprint }
            require(fingerprints.size == fingerprints.toSet().size) {
                "Backup inválido: importações duplicadas"
            }

            database.transactionDao().deleteAll()
            database.categoryDao().deleteAll()
            database.statementImportDao().deleteAll()
            database.chatMessageDao().deleteAll()

            val categories = backup.categories.map {
                CategoryEntity(it.id, it.name, it.type)
            }
            database.categoryDao().insertCategories(categories)

            val transactions = backup.transactions.map {
                TransactionEntity(
                    id = it.id,
                    description = it.description,
                    amountCents = it.amountCents,
                    categoryId = it.categoryId,
                    date = LocalDate.parse(it.date),
                    type = it.type,
                    notes = it.notes,
                    origin = it.origin
                )
            }
            database.transactionDao().insertTransactions(transactions)
            database.statementImportDao().insertImports(backup.statementImports.map {
                StatementImportEntity(it.id, it.fileName, LocalDateTime.parse(it.importedAt), it.transactionCount, it.fingerprint)
            })
            database.chatMessageDao().insertMessages(backup.chatMessages.map {
                ChatMessageEntity(it.id, it.role, it.content, LocalDateTime.parse(it.createdAt))
            })
        }
    }
}
