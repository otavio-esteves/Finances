package br.com.otavioesteves.finances.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.ChatMessageEntity
import br.com.otavioesteves.finances.data.local.entity.StatementImportEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
import br.com.otavioesteves.finances.domain.model.BackupModel
import br.com.otavioesteves.finances.domain.model.CategoryExportModel
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.ChatRole
import br.com.otavioesteves.finances.domain.model.TransactionOrigin
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.usecase.CreateBackupUseCase
import br.com.otavioesteves.finances.domain.usecase.RestoreBackupUseCase
import br.com.otavioesteves.finances.utils.readBackupDocument
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class RoomBackupRepositoryTest {
    @Test
    fun invalidUtf8BackupIsRejectedBeforeReplacingFinancialData() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            database.categoryDao().insertCategories(listOf(CategoryEntity(1, "Outros", CategoryType.EXPENSE)))
            database.transactionDao().insertTransaction(TransactionEntity(1, "Original", 100, 1,
                LocalDate.of(2026, 11, 1), TransactionType.EXPENSE, null))
            val repository = RoomBackupRepository(database)
            val original = repository.createBackup()
            val json = CreateBackupUseCase(repository)()
            val bytes = json.toByteArray(Charsets.UTF_8)
            bytes[json.indexOf("Original")] = 0xff.toByte()
            assertTrue(runCatching {
                RestoreBackupUseCase(repository)(readBackupDocument(ByteArrayInputStream(bytes)))
            }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(original, repository.createBackup())
        } finally { database.close() }
    }

    @Test
    fun restoringEmptyOrPartialCategoriesKeepsEveryTransactionTypeUsable() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            val repository = RoomBackupRepository(database)
            repository.restoreBackup(BackupModel(categories = emptyList(), transactions = emptyList()))
            assertEquals(CategoryType.entries.toSet(), database.categoryDao().getAllCategoriesForBackup().map { it.type }.toSet())
            repository.restoreBackup(BackupModel(
                categories = listOf(CategoryExportModel(77, "Personalizada", CategoryType.EXPENSE)),
                transactions = emptyList()
            ))
            val categories = database.categoryDao().getAllCategoriesForBackup()
            assertEquals(CategoryType.entries.toSet(), categories.map { it.type }.toSet())
            assertEquals("Personalizada", categories.single { it.id == 77L }.name)
            assertEquals(1, categories.count { it.type == CategoryType.EXPENSE })
        } finally { database.close() }
    }

    @Test
    fun zeroAmountRoundTripSucceedsAndNegativeBackupLeavesDatabaseIntact() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            database.categoryDao().insertCategories(listOf(CategoryEntity(1, "Outros", CategoryType.EXPENSE)))
            database.transactionDao().insertTransaction(
                TransactionEntity(1, "Sem movimento", 0, 1, LocalDate.of(2026, 1, 10), TransactionType.EXPENSE, null)
            )
            val repository = RoomBackupRepository(database)
            val backup = repository.createBackup()
            repository.restoreBackup(backup)
            assertEquals(0L, database.transactionDao().getAllTransactions().single().amountCents)
            val invalid = backup.copy(transactions = backup.transactions.map { it.copy(amountCents = -1) })
            assertTrue(runCatching { repository.restoreBackup(invalid) }.isFailure)
            assertEquals(0L, database.transactionDao().getAllTransactions().single().amountCents)
        } finally {
            database.close()
        }
    }

    @Test
    fun backupAndRestore_preservesImportedTransactionsAndRelatedHistory() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            val importedAt = LocalDateTime.of(2026, 1, 15, 10, 0)
            database.categoryDao().insertCategories(listOf(CategoryEntity(1, "Alimentação", CategoryType.EXPENSE)))
            database.transactionDao().insertTransaction(
                TransactionEntity(1, "Mercado", 5_000, 1, LocalDate.of(2026, 1, 10), TransactionType.EXPENSE, null, TransactionOrigin.IMPORTED, "market")
            )
            database.statementImportDao().insertImport(StatementImportEntity(1, "extrato.csv", importedAt, 1, "fingerprint"))
            database.chatMessageDao().insertMessage(ChatMessageEntity(1, ChatRole.USER, "Quanto gastei?", importedAt))

            val repository = RoomBackupRepository(database)
            val backup = repository.createBackup()
            database.transactionDao().insertTransaction(
                TransactionEntity(2, "Temporária", 100, 1, LocalDate.of(2026, 1, 11), TransactionType.EXPENSE, null)
            )
            repository.restoreBackup(backup)

            assertEquals(5, backup.version)
            assertEquals(TransactionOrigin.IMPORTED, database.transactionDao().getAllTransactions().single().origin)
            assertEquals("market", database.transactionDao().getAllTransactions().single().iconKey)
            assertEquals("fingerprint", database.statementImportDao().getAllImportsForBackup().single().fingerprint)
            assertEquals("Quanto gastei?", database.chatMessageDao().getAllMessagesForBackup().single().content)
        } finally {
            database.close()
        }
    }

    @Test
    fun restoreBackup_rejectsDuplicateTransactionIdsWithoutChangingDatabase() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            database.categoryDao().insertCategories(listOf(CategoryEntity(1, "Alimentação", CategoryType.EXPENSE)))
            database.transactionDao().insertTransaction(
                TransactionEntity(1, "Original", 5_000, 1, LocalDate.of(2026, 1, 10), TransactionType.EXPENSE, null)
            )
            val repository = RoomBackupRepository(database)
            val backup = repository.createBackup()
            val invalid = backup.copy(transactions = backup.transactions + backup.transactions.single().copy(description = "Duplicada"))

            assertTrue(runCatching { repository.restoreBackup(invalid) }.isFailure)
            assertEquals("Original", database.transactionDao().getAllTransactions().single().description)
        } finally {
            database.close()
        }
    }
}
