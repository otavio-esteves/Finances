package br.com.otavioesteves.finances.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.StatementImportEntity
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.DuplicateStatementImportException
import br.com.otavioesteves.finances.domain.model.ImportedStatement
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.repository.StatementImportRepository
import br.com.otavioesteves.finances.domain.usecase.ConfirmStatementImportUseCase
import br.com.otavioesteves.finances.data.DefaultDateProvider
import br.com.otavioesteves.finances.data.statement.CsvOfxStatementParser
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class RoomStatementImportRepositoryTest {
    private suspend fun withDatabase(block: suspend (AppDatabase) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            db.categoryDao().insertCategories(listOf(CategoryEntity(1, "Outros", CategoryType.EXPENSE)))
            block(db)
        } finally { db.close() }
    }
    private fun transaction() = Transaction(0, "Tarifa", Money(1000), 1, LocalDate.of(2026, 11, 1), TransactionType.EXPENSE)
    private fun statement(source: String?) = ImportedStatement(0, "conta.csv", LocalDateTime.of(2026, 11, 1, 9, 0), 1, sourceLabel = source)

    private suspend fun assertLegacyClaimsRecognized(oldEntry: RawStatementEntry, corrected: RawStatementEntry) {
        val oldCategory = Category(1, "Outros", CategoryType.valueOf(oldEntry.type.name))
        val correctedCategory = Category(1, "Outros", CategoryType.valueOf(corrected.type.name))
        val oldFingerprints = mutableListOf<String>()
        val capture = object : StatementImportRepository {
            override fun getImports() = flowOf(emptyList<ImportedStatement>())
            override suspend fun confirmImport(statementImport: ImportedStatement, transactions: List<Transaction>, fingerprint: String, legacyFingerprint: String?) {
                oldFingerprints += listOfNotNull(fingerprint, legacyFingerprint)
            }
        }
        ConfirmStatementImportUseCase(capture, DefaultDateProvider())("old.file", listOf(CategorySuggestion(oldEntry, oldCategory, 1f)), "Conta A")
        for (fingerprint in oldFingerprints) withDatabase { db ->
            db.categoryDao().insertCategories(listOf(CategoryEntity(1, correctedCategory.name, correctedCategory.type)))
            db.statementImportDao().insertImport(StatementImportEntity(fileName = "old.file", importedAt = LocalDateTime.of(2026, 11, 1, 9, 0),
                transactionCount = 1, fingerprint = fingerprint, sourceLabel = "Conta A"))
            assertTrue(runCatching {
                ConfirmStatementImportUseCase(RoomStatementImportRepository(db), DefaultDateProvider())(
                    "new.file", listOf(CategorySuggestion(corrected, correctedCategory, 1f)), "Conta A")
            }.exceptionOrNull() is DuplicateStatementImportException)
            assertTrue(db.transactionDao().getAllTransactions().isEmpty())
            assertEquals(1, db.statementImportDao().getAllImportsForBackup().size)
        }
    }

    @Test fun decodedOfxCannotRepeatBatchesSavedWithEncodedTextInEitherLegacyVersion() = runBlocking {
        val oldEntry = RawStatementEntry("A &amp; B", Money(1000), LocalDate.of(2026, 11, 1), TransactionType.EXPENSE)
        val ofx = "<OFX><BANKTRANLIST><STMTTRN><DTPOSTED>20261101<TRNAMT>-10.00<MEMO>A &amp; B</STMTTRN></BANKTRANLIST></OFX>"
        assertLegacyClaimsRecognized(oldEntry, CsvOfxStatementParser().parse("new.ofx", ofx.toByteArray()).single())
    }

    @Test fun correctedPdfDirectionsStillRecognizeBothLegacyFingerprintVersions() = runBlocking {
        for (row in listOf("01/11/2026 Mercado +10,00", "01/11/2026 Pagamento do cartão -10,00",
            "01/11/2026 Pagamento da fatura estornado -10,00")) {
            val parser = br.com.otavioesteves.finances.data.statement.PdfStatementParser { "Fatura\n$row" }
            val corrected = parser.parse("new.pdf", "%PDF-1.4".toByteArray()).single()
            val old = corrected.copy(type = requireNotNull(corrected.legacyInputType), legacyInputType = null)
            assertLegacyClaimsRecognized(old, corrected)
        }
    }

    @Test fun distinctAccountsAreIndependentButEquivalentLabelsStayProtectedAfterBackupRestore() = runBlocking {
        withDatabase { db ->
            val repository = RoomStatementImportRepository(db)
            val first = statement("Cartão pessoal")
            val second = statement("Conta conjunta")
            repository.confirmImport(first, listOf(transaction()), "same-content")
            repository.confirmImport(second, listOf(transaction()), "same-content")
            assertEquals(2, db.transactionDao().getAllTransactions().size)
            val backup = RoomBackupRepository(db)
            backup.restoreBackup(backup.createBackup())
            for (source in listOf("  CARTAO   PESSOAL  ", "Conta conjunta")) {
                assertTrue(runCatching {
                    repository.confirmImport(statement(source), listOf(transaction()), "same-content")
                }.exceptionOrNull() is DuplicateStatementImportException)
            }
            assertEquals(2, db.transactionDao().getAllTransactions().size)
        }
    }

    @Test fun legacyGlobalClaimsAreCheckedAgainstTheirOriginalSource() = runBlocking {
        for (legacy in listOf(false, true)) withDatabase { db ->
            db.statementImportDao().insertImport(StatementImportEntity(fileName = "old.csv", importedAt = LocalDateTime.of(2026, 11, 1, 9, 0),
                transactionCount = 1, fingerprint = if (legacy) "old-v1" else "canonical-v2", sourceLabel = "Conta A"))
            val repository = RoomStatementImportRepository(db)
            assertTrue(runCatching {
                repository.confirmImport(statement("  conta a "), listOf(transaction()), "canonical-v2", "old-v1")
            }.exceptionOrNull() is DuplicateStatementImportException)
            repository.confirmImport(statement("Conta B"), listOf(transaction()), "canonical-v2", "old-v1")
            assertEquals(1, db.transactionDao().getAllTransactions().size)
            assertTrue(runCatching {
                repository.confirmImport(statement("Conta B"), listOf(transaction()), "canonical-v2", "old-v1")
            }.exceptionOrNull() is DuplicateStatementImportException)
        }
    }

    @Test fun unlabeledLegacyBatchRemainsBlockedUntilADifferentSourceIsIdentified() = runBlocking {
        withDatabase { db ->
            db.statementImportDao().insertImport(StatementImportEntity(fileName = "old.csv", importedAt = LocalDateTime.of(2026, 11, 1, 9, 0),
                transactionCount = 1, fingerprint = "content"))
            val repository = RoomStatementImportRepository(db)
            assertTrue(runCatching {
                repository.confirmImport(statement("  "), listOf(transaction()), "content")
            }.exceptionOrNull() is DuplicateStatementImportException)
            repository.confirmImport(statement("Outra conta"), listOf(transaction()), "content")
            assertEquals(1, db.transactionDao().getAllTransactions().size)
        }
    }

    @Test fun concurrentClaimsForTheSameAccountCommitOnlyOnce() = runBlocking {
        withDatabase { db ->
            val repository = RoomStatementImportRepository(db)
            val jobs = (1..2).map { async(Dispatchers.IO) {
                runCatching { repository.confirmImport(statement("Conta A"), listOf(transaction()), "content") }
            } }
            val results = jobs.map { it.await() }
            assertEquals(1, results.count { it.isSuccess })
            assertTrue(results.single { it.isFailure }.exceptionOrNull() is DuplicateStatementImportException)
            assertEquals(1, db.transactionDao().getAllTransactions().size)
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
                repository.confirmImport(statement.copy(fileName = "new.csv"), listOf(entry), "canonical-v2", "legacy-v1")
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
