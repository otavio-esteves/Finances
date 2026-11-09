package br.com.otavioesteves.finances.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.StatementImportEntity
import br.com.otavioesteves.finances.data.DefaultDateProvider
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.usecase.ConfirmStatementImportUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.DuplicateStatementImportException
import br.com.otavioesteves.finances.domain.model.ImportedStatement
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class RoomStatementImportRepositoryTest {
    private suspend fun withDatabase(block: suspend (AppDatabase) -> Unit) {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            database.categoryDao().insertCategories(listOf(CategoryEntity(1, "Outros", CategoryType.EXPENSE)))
            block(database)
        } finally { database.close() }
    }

    private val suggestions get() = listOf(CategorySuggestion(
        RawStatementEntry("Mercado", Money(1000), LocalDate.of(2026, 1, 10), TransactionType.EXPENSE),
        Category(1, "Outros", CategoryType.EXPENSE), 1f
    ))

    @Test
    fun identicalPurchasesInDifferentAccountsSurviveBackupAndRemainDeduplicated() = runBlocking {
        withDatabase { db ->
            val imports = RoomStatementImportRepository(db)
            val confirm = ConfirmStatementImportUseCase(imports, DefaultDateProvider())
            confirm("a.csv", suggestions, "Cartão A")
            confirm("b.csv", suggestions, "Cartão B")
            assertEquals(2, db.transactionDao().getAllTransactions().size)
            val backupRepository = RoomBackupRepository(db)
            backupRepository.restoreBackup(backupRepository.createBackup())
            for (source in listOf(" cartão   a ", "Cartão B")) {
                assertTrue(runCatching { confirm("renomeado.csv", suggestions, source) }
                    .exceptionOrNull() is DuplicateStatementImportException)
            }
            val first = imports.getImports().first().single { it.sourceLabel == "Cartão A" }
            imports.undoImport(first.id)
            confirm("a.csv", suggestions, "Cartão A")
            assertEquals(2, db.transactionDao().getAllTransactions().size)
            assertEquals(2, imports.getImports().first().size)
        }
    }

    @Test
    fun legacyV1AndV2ClaimsOnlyBlockTheirOriginalAccount() = runBlocking {
        for (fingerprint in listOf(
            "3cc5e72d2a688589a5da1cebcc6c19dbe4eade96203752a39b602c7692504bf7",
            "cd8dc7d242aac8c78a89422219e8543054e06b7887579ea8149a0681b83b8b2e"
        )) withDatabase { db ->
            db.statementImportDao().insertImport(StatementImportEntity(fileName = "antigo.csv",
                importedAt = LocalDateTime.of(2026, 1, 15, 10, 0), transactionCount = 1,
                fingerprint = fingerprint, sourceLabel = " Cartão   A "))
            val confirm = ConfirmStatementImportUseCase(RoomStatementImportRepository(db), DefaultDateProvider())
            assertTrue(runCatching { confirm("a.csv", suggestions, "cartão a") }
                .exceptionOrNull() is DuplicateStatementImportException)
            confirm("b.csv", suggestions, "Cartão B")
            assertEquals(1, db.transactionDao().getAllTransactions().size)
            assertEquals(2, db.statementImportDao().getAllImportsForBackup().size)
        }
    }

    @Test
    fun concurrentImportsOfTheSameAccountCommitOnlyOneBatch() = runBlocking {
        withDatabase { db ->
            val confirm = ConfirmStatementImportUseCase(RoomStatementImportRepository(db), DefaultDateProvider())
            val results = coroutineScope {
                listOf("Cartão A", " cartão   a ").map { source ->
                    async(Dispatchers.IO) { runCatching { confirm("a.csv", suggestions, source) } }
                }.map { it.await() }
            }
            assertEquals(1, results.count { it.isSuccess })
            assertTrue(results.single { it.isFailure }.exceptionOrNull() is DuplicateStatementImportException)
            assertEquals(1, db.transactionDao().getAllTransactions().size)
            assertEquals(1, db.statementImportDao().getAllImportsForBackup().size)
        }
    }

    @Test
    fun confirmImportRecognizesLegacyFingerprintWithoutInserting() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            database.categoryDao().insertCategories(listOf(CategoryEntity(1, "Outros", CategoryType.EXPENSE)))
            val repository = RoomStatementImportRepository(database)
            val statement = ImportedStatement(0, "original.csv", LocalDateTime.of(2026, 1, 15, 10, 0), 1)
            val entry = Transaction(0, "Mercado", Money.fromCents(1000), 1, LocalDate.of(2026, 1, 10), TransactionType.EXPENSE)
            repository.confirmImport(statement, listOf(entry), "legacy-v1")
            assertTrue(runCatching {
                repository.confirmImport(statement.copy(fileName = "new.csv"), listOf(entry), "canonical-v2", listOf("legacy-v1"))
            }.exceptionOrNull() is DuplicateStatementImportException)
            assertEquals(1, database.transactionDao().getAllTransactions().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun confirmImport_isAtomicAndRejectsDuplicateBatch() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            database.categoryDao().insertCategories(
                listOf(CategoryEntity(id = 1, name = "Alimentação", type = CategoryType.EXPENSE))
            )
            val repository = RoomStatementImportRepository(database)
            val statement = ImportedStatement(0, "extrato.csv", LocalDateTime.of(2026, 1, 15, 10, 0), 2)
            val valid = Transaction(0, "Mercado", Money.fromCents(1000), 1, LocalDate.of(2026, 1, 10), TransactionType.EXPENSE)
            val invalid = valid.copy(description = "Sem categoria", categoryId = 999)

            assertTrue(runCatching {
                repository.confirmImport(statement, listOf(valid, invalid), "fingerprint-1")
            }.isFailure)
            assertTrue(database.transactionDao().getAllTransactions().isEmpty())
            assertTrue(repository.getImports().first().isEmpty())

            repository.confirmImport(statement.copy(transactionCount = 1), listOf(valid), "fingerprint-1")
            assertTrue(runCatching {
                repository.confirmImport(statement.copy(transactionCount = 1), listOf(valid), "fingerprint-1")
            }.exceptionOrNull() is DuplicateStatementImportException)
            assertEquals(1, database.transactionDao().getAllTransactions().size)
            assertEquals(1, repository.getImports().first().size)
        } finally {
            database.close()
        }
    }
}
