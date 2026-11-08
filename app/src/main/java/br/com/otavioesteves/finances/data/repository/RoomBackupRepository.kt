package br.com.otavioesteves.finances.data.repository

import androidx.room.withTransaction
import br.com.otavioesteves.finances.data.local.ESSENTIAL_CATEGORIES
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
import br.com.otavioesteves.finances.data.local.entity.StatementImportEntity
import br.com.otavioesteves.finances.data.local.entity.ChatMessageEntity
import br.com.otavioesteves.finances.data.local.mapper.toDomain
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
                origin = it.origin,
                iconKey = it.iconKey,
                importId = it.importId,
                sourceLabel = it.sourceLabel,
                categoryMemoryDescriptionKey = it.categoryMemoryDescriptionKey,
                categoryMemoryInputType = it.categoryMemoryInputType
            )
        }
        val statementImports = database.statementImportDao().getAllImportsForBackup().map {
            StatementImportExportModel(it.id, it.fileName, it.importedAt.toString(), it.transactionCount, it.fingerprint, it.sourceLabel, it.canUndo)
        }
        val chatMessages = database.chatMessageDao().getAllMessagesForBackup().map {
            ChatMessageExportModel(it.id, it.role, it.content, it.createdAt.toString())
        }
        BackupModel(
            categories = categories,
            transactions = transactions,
            statementImports = statementImports,
            chatMessages = chatMessages,
            categoryMemories = database.categoryMemoryDao().getAll().map {
                br.com.otavioesteves.finances.domain.model.CategoryMemoryExportModel(it.descriptionKey, it.inputType, it.categoryId)
            }
        )
    }

    override suspend fun restoreBackup(backup: BackupModel) {
        database.withTransaction {
            val categoryIds = backup.categories.map { it.id }.toSet()
            require(categoryIds.size == backup.categories.size) { "Backup inválido: categorias duplicadas" }
            require(backup.categories.all { it.id > 0 && it.name.isNotBlank() }) {
                "Backup inválido: categoria sem nome ou ID válido"
            }
            require(backup.transactions.all { it.categoryId in categoryIds }) {
                "Backup inválido: transação com categoria inexistente"
            }
            require(backup.transactions.map { it.id }.distinct().size == backup.transactions.size &&
                backup.transactions.all { it.id > 0 && it.description.isNotBlank() && it.amountCents >= 0 }
            ) { "Backup inválido: transações repetidas ou inválidas" }
            val categoryTypes = backup.categories.associate { it.id to it.type }
            require(backup.transactions.all { categoryTypes[it.categoryId]?.name == it.type.name }) {
                "Backup inválido: tipo de transação incompatível com a categoria"
            }
            require(backup.chatMessages.map { it.id }.distinct().size == backup.chatMessages.size &&
                backup.chatMessages.all { it.id > 0 }
            ) { "Backup inválido: mensagens repetidas ou inválidas" }
            require(backup.statementImports.map { it.id }.distinct().size == backup.statementImports.size &&
                backup.statementImports.all { it.id > 0 && it.transactionCount >= 0 }
            ) { "Backup inválido: importações repetidas ou inválidas" }
            val fingerprints = backup.statementImports.mapNotNull { it.fingerprint }
            require(fingerprints.size == fingerprints.toSet().size) {
                "Backup inválido: importações duplicadas"
            }

            val importIds = backup.statementImports.map { it.id }.toSet()
            require(backup.transactions.all { it.importId == null || (it.importId in importIds && it.origin == br.com.otavioesteves.finances.domain.model.TransactionOrigin.IMPORTED) }) {
                "Backup inválido: vínculo de importação inexistente"
            }
            require(backup.categoryMemories.all { it.categoryId in categoryIds && it.descriptionKey.isNotBlank() } &&
                backup.categoryMemories.map { it.descriptionKey to it.inputType }.distinct().size == backup.categoryMemories.size) {
                "Backup inválido: memória de categorias inválida"
            }
            // Validate every date, provenance pair and aggregate before replacing existing data.
            val transactions = backup.transactions.map {
                TransactionEntity(
                    id = it.id,
                    description = it.description,
                    amountCents = it.amountCents,
                    categoryId = it.categoryId,
                    date = try {
                        LocalDate.parse(it.date)
                    } catch (error: java.time.DateTimeException) {
                        throw IllegalArgumentException("Backup inválido: data de transação inválida.", error)
                    },
                    type = it.type,
                    notes = it.notes,
                    origin = it.origin,
                    iconKey = it.iconKey,
                    importId = it.importId,
                    sourceLabel = it.sourceLabel,
                    categoryMemoryDescriptionKey = it.categoryMemoryDescriptionKey,
                    categoryMemoryInputType = it.categoryMemoryInputType
                ).also { entity -> entity.toDomain() }
            }
            br.com.otavioesteves.finances.domain.model.checkedMagnitudeTotal(transactions.asSequence().map { it.amountCents })
            database.categoryMemoryDao().clear()
            database.transactionDao().deleteAll()
            database.categoryDao().deleteAll()
            database.statementImportDao().deleteAll()
            database.chatMessageDao().deleteAll()

            val categories = backup.categories.map {
                CategoryEntity(it.id, it.name, it.type)
            }
            database.categoryDao().insertCategories(categories)
            database.categoryDao().insertCategories(
                ESSENTIAL_CATEGORIES.filter { required -> categories.none { it.type == required.type } }
            )

            database.transactionDao().insertTransactions(transactions)
            database.statementImportDao().insertImports(backup.statementImports.map {
                StatementImportEntity(it.id, it.fileName, LocalDateTime.parse(it.importedAt), it.transactionCount, it.fingerprint, it.sourceLabel, it.canUndo)
            })
            database.chatMessageDao().insertMessages(backup.chatMessages.map {
                ChatMessageEntity(it.id, it.role, it.content, LocalDateTime.parse(it.createdAt))
            })
            database.categoryMemoryDao().remember(backup.categoryMemories.map {
                br.com.otavioesteves.finances.data.local.entity.CategoryMemoryEntity(it.descriptionKey, it.inputType, it.categoryId)
            })
            database.markDataRestored()
        }
    }
}
