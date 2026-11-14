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
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class RoomBackupRepositoryTest {
    @Test
    fun backupAboveFiftyMegabytesRoundTripsThroughRoomAndDocumentStreams() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val file = java.io.File.createTempFile("backup-roundtrip-", ".json", context.cacheDir)
        try {
            database.categoryDao().insertCategories(listOf(CategoryEntity(1, "Outros", CategoryType.EXPENSE)))
            val note = "a".repeat(60_000)
            database.transactionDao().insertTransactions((1L..900L).map { id ->
                TransactionEntity(id, "Compra $id", 100, 1, LocalDate.of(2026, 11, 1), TransactionType.EXPENSE, note)
            })
            val repository = RoomBackupRepository(database)
            CreateBackupUseCase(repository)(file.outputStream())
            assertTrue(file.length().toString(), file.length() > 50L * 1024 * 1024)
            database.transactionDao().deleteAll()
            RestoreBackupUseCase(repository)(file.inputStream())
            val restored = database.transactionDao().getAllTransactions()
            assertEquals(900, restored.size)
            assertTrue(restored.all { it.notes == note && it.amountCents == 100L })
            assertEquals(90_000L, database.transactionDao().storedMagnitudeTotal())
        } finally { file.delete(); database.close() }
    }

    @Test
    fun metadataDaosRejectOversizedRowsBeforeInsertingAnyPartOfTheBatch() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            val large = "a".repeat(3 * 1024 * 1024)
            assertTrue(runCatching { database.categoryDao().insertCategories(listOf(
                CategoryEntity(1, "Outros", CategoryType.EXPENSE), CategoryEntity(2, large, CategoryType.EXPENSE)
            )) }.isFailure)
            assertTrue(database.categoryDao().getAllCategoriesForBackup().isEmpty())
            database.categoryDao().insertCategories(listOf(CategoryEntity(1, "Outros", CategoryType.EXPENSE)))
            val now = LocalDateTime.of(2026, 11, 1, 9, 0)
            assertTrue(runCatching { database.chatMessageDao().insertMessages(listOf(
                ChatMessageEntity(1, br.com.otavioesteves.finances.domain.model.ChatRole.USER, "Válida", now),
                ChatMessageEntity(2, br.com.otavioesteves.finances.domain.model.ChatRole.USER, large, now)
            )) }.isFailure)
            assertTrue(database.chatMessageDao().getAllMessagesForBackup().isEmpty())
            assertTrue(runCatching { database.statementImportDao().insertImports(listOf(
                StatementImportEntity(1, "a.csv", now, 0, null), StatementImportEntity(2, "b.csv", now, 0, large)
            )) }.isFailure)
            assertTrue(database.statementImportDao().getAllImportsForBackup().isEmpty())
            assertTrue(runCatching { database.categoryMemoryDao().remember(listOf(
                br.com.otavioesteves.finances.data.local.entity.CategoryMemoryEntity("compra", TransactionType.EXPENSE, 1),
                br.com.otavioesteves.finances.data.local.entity.CategoryMemoryEntity(large, TransactionType.EXPENSE, 1)
            )) }.isFailure)
            assertTrue(database.categoryMemoryDao().getAll().isEmpty())
        } finally { database.close() }
    }

    @Test
    fun oversizedBackupFieldsCannotReplaceExistingDataOrPoisonRoomQueries() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            database.categoryDao().insertCategories(listOf(CategoryEntity(1, "Outros", CategoryType.EXPENSE)))
            database.transactionDao().insertTransaction(TransactionEntity(1, "Original", 100, 1,
                LocalDate.of(2026, 11, 1), TransactionType.EXPENSE, null))
            val repository = RoomBackupRepository(database)
            val original = repository.createBackup()
            val transaction = original.transactions.single()
            val text = "a".repeat(3 * 1024 * 1024)
            val candidates = listOf(
                original.copy(transactions = listOf(transaction.copy(notes = text))),
                original.copy(transactions = listOf(transaction.copy(description = text))),
                original.copy(transactions = listOf(transaction.copy(iconKey = text))),
                original.copy(transactions = listOf(transaction.copy(sourceLabel = text))),
                original.copy(transactions = listOf(transaction.copy(categoryMemoryDescriptionKey = text, categoryMemoryInputType = transaction.type))),
                original.copy(categories = original.categories.map { it.copy(name = text) }),
                original.copy(chatMessages = listOf(br.com.otavioesteves.finances.domain.model.ChatMessageExportModel(
                    1, br.com.otavioesteves.finances.domain.model.ChatRole.USER, text, "2026-11-01T09:00"))),
                original.copy(statementImports = listOf(br.com.otavioesteves.finances.domain.model.StatementImportExportModel(
                    1, text, "2026-11-01T09:00", 0, null))),
                original.copy(categoryMemories = listOf(br.com.otavioesteves.finances.domain.model.CategoryMemoryExportModel(
                    text, TransactionType.EXPENSE, 1)))
            )
            for (candidate in candidates) {
                assertTrue(runCatching { repository.restoreBackup(candidate) }.exceptionOrNull() is IllegalArgumentException)
                assertEquals(original, repository.createBackup())
            }
            // Exercise the same streaming path used by Settings, with a field that fits the token
            // guard but exceeds the decoded storage budget because it contains multibyte text.
            val invalidJson = CreateBackupUseCase(repository)().replace("Original", "é".repeat(40_000))
            assertTrue(runCatching { RestoreBackupUseCase(repository)(invalidJson.byteInputStream()) }.isFailure)
            assertEquals(original, repository.createBackup())
        } finally { database.close() }
    }

    @Test
    fun oversizedManualWritesAreRejectedAndBoundarySizedRowsStayReadable() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            database.categoryDao().insertCategories(listOf(CategoryEntity(1, "Outros", CategoryType.EXPENSE)))
            val row = TransactionEntity(1, "Original", 100, 1, LocalDate.of(2026, 11, 1), TransactionType.EXPENSE, null)
            database.transactionDao().insertTransaction(row)
            val text = "a".repeat(3 * 1024 * 1024)
            for (candidate in listOf(row.copy(id = 0, notes = text), row.copy(description = text),
                row.copy(iconKey = text), row.copy(sourceLabel = text))) {
                assertTrue(runCatching { database.transactionDao().insertTransaction(candidate) }.exceptionOrNull() is IllegalArgumentException)
                assertEquals(listOf(row), database.transactionDao().getAllTransactions())
                assertEquals(100L, database.transactionDao().storedMagnitudeTotal())
            }
            val boundary = "é".repeat(br.com.otavioesteves.finances.domain.model.MAX_PERSISTED_TEXT_BYTES / 2)
            database.transactionDao().updateTransaction(row.copy(notes = boundary))
            assertEquals(boundary, database.transactionDao().getAllTransactions().single().notes)
            assertFalse(CreateBackupUseCase(RoomBackupRepository(database))().isBlank())
        } finally { database.close() }
    }

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
