package br.com.otavioesteves.finances.data.repository

import androidx.room.withTransaction
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
import br.com.otavioesteves.finances.domain.model.BackupModel
import br.com.otavioesteves.finances.domain.model.CategoryExportModel
import br.com.otavioesteves.finances.domain.model.TransactionExportModel
import br.com.otavioesteves.finances.domain.repository.BackupRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class RoomBackupRepository(
    private val database: AppDatabase
) : BackupRepository {

    override suspend fun createBackup(): BackupModel {
        val categories = database.categoryDao().getAllCategories().first().map {
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
                notes = it.notes
            )
        }
        return BackupModel(categories = categories, transactions = transactions)
    }

    override suspend fun restoreBackup(backup: BackupModel) {
        database.withTransaction {
            database.transactionDao().deleteAll()
            database.categoryDao().deleteAll()

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
                    notes = it.notes
                )
            }
            database.transactionDao().insertTransactions(transactions)
        }
    }
}
