package br.com.otavioesteves.finances.data.repository

import br.com.otavioesteves.finances.data.statement.CsvOfxStatementParser
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
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class RoomStatementImportRepositoryTest {
    private fun transaction() = Transaction(0, "Tarifa", Money(1000), 1, LocalDate.of(2026, 11, 1), TransactionType.EXPENSE)
    private fun statement(source: String?) = ImportedStatement(0, "conta.csv", LocalDateTime.of(2026, 11, 1, 9, 0), 1, sourceLabel = source)

    private suspend fun assertLegacyClaimsRecognized(oldEntry: RawStatementEntry, corrected: RawStatementEntry) {
        val correctedCategory = Category(1, "Outros", CategoryType.valueOf(corrected.type.name))
        // The encoded descriptions and old PDF directions could only be saved in v1 through v3.
        for (version in 1..3) withDatabase { db ->
            val fingerprint = historicalFingerprint(oldEntry, version)
            db.categoryDao().insertCategories(listOf(CategoryEntity(1, correctedCategory.name, correctedCategory.type)))
            db.statementImportDao().insertImport(StatementImportEntity(fileName = "old.file", importedAt = LocalDateTime.of(2026, 11, 1, 9, 0),
                transactionCount = 1, fingerprint = fingerprint, sourceLabel = "Conta A"))
            val result = runCatching {
                ConfirmStatementImportUseCase(RoomStatementImportRepository(db), DefaultDateProvider())(
                    "new.file", listOf(CategorySuggestion(corrected, correctedCategory, 1f)), "Conta A")
            }
            assertTrue("Expected a duplicate for v$version, got $result", result.exceptionOrNull() is DuplicateStatementImportException)
            assertTrue(db.transactionDao().getAllTransactions().isEmpty())
            assertEquals(1, db.statementImportDao().getAllImportsForBackup().size)
        }
    }

    private fun historicalFingerprint(entry: RawStatementEntry, version: Int): String {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            output.write("finances-statement-v$version".toByteArray(Charsets.UTF_8))
            val fields = buildList {
                if (version == 3) add("conta a")
                addAll(listOf(entry.date.toString(), entry.type.name, entry.amount.cents.toString(), entry.description))
            }
            for (field in fields) {
                val bytes = field.toByteArray(Charsets.UTF_8)
                output.writeInt(bytes.size)
                output.write(bytes)
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(buffer.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @Test fun decodedOfxCannotRepeatBatchesSavedWithEncodedTextInEitherLegacyVersion() = runBlocking {
        val oldEntry = RawStatementEntry("A &amp; B", Money(1000), LocalDate.of(2026, 11, 1), TransactionType.EXPENSE)
        val ofx = "<OFX><BANKTRANLIST><STMTTRN><DTPOSTED>20261101<TRNAMT>-10.00<MEMO>A &amp; B</STMTTRN></BANKTRANLIST></OFX>"
        assertLegacyClaimsRecognized(oldEntry, CsvOfxStatementParser().parse("new.ofx", ofx.toByteArray()).single())
    }

    @Test fun multilineOfxStillRecognizesEncodedAndDecodedFirstLineBatches() = runBlocking {
        val ofx = "<BANKTRANLIST><STMTTRN><DTPOSTED>20261101<TRNAMT>-10.00<MEMO>A &amp; B\nPedido 123</STMTTRN></BANKTRANLIST>"
        val corrected = CsvOfxStatementParser().parse("new.ofx", ofx.toByteArray()).single()
        for (text in listOf("A &amp; B", "A & B")) {
            assertLegacyClaimsRecognized(RawStatementEntry(text, corrected.amount, corrected.date, corrected.type), corrected)
        }
    }

    @Test fun differentMultilineOfxDescriptionsAreStoredAsIndependentBatches() = runBlocking {
        withDatabase { db ->
            db.categoryDao().insertCategories(listOf(CategoryEntity(1, "Outros", CategoryType.EXPENSE)))
            val category = Category(1, "Outros", CategoryType.EXPENSE)
            val confirm = ConfirmStatementImportUseCase(RoomStatementImportRepository(db), DefaultDateProvider())
            for (text in listOf("A &amp; B", "A &amp; B\nPedido 1", "A &amp; B\nPedido 2")) {
                val ofx = "<BANKTRANLIST><STMTTRN><DTPOSTED>20261101<TRNAMT>-10.00<MEMO>$text</STMTTRN></BANKTRANLIST>"
                val entry = CsvOfxStatementParser().parse("a.ofx", ofx.toByteArray()).single()
                confirm("a.ofx", listOf(CategorySuggestion(entry, category, 1f)), "Conta A")
            }
            assertEquals(setOf("A & B", "A & B\nPedido 1", "A & B\nPedido 2"), db.transactionDao().getAllTransactions().map { it.description }.toSet())
            assertEquals(3, db.statementImportDao().getAllImportsForBackup().size)
            val repeated = CsvOfxStatementParser().parse("a.ofx", "<BANKTRANLIST><STMTTRN><DTPOSTED>20261101<TRNAMT>-10.00<MEMO>A &amp; B\nPedido 1</STMTTRN></BANKTRANLIST>".toByteArray()).single()
            assertTrue(runCatching { confirm("a.ofx", listOf(CategorySuggestion(repeated, category, 1f)), "Conta A") }.exceptionOrNull() is DuplicateStatementImportException)
        }
    }

    @Test fun correctedPdfDirectionsStillRecognizeBothLegacyFingerprintVersions() = runBlocking {
        for (row in listOf("01/11/2026 Mercado +10,00", "01/11/2026 Pagamento do cartão -10,00",
            "01/11/2026 Pagamento da fatura estornado -10,00", "01/11/2026 Obrigado pelo pagamento +10,00")) {
            val parser = br.com.otavioesteves.finances.data.statement.PdfStatementParser { "Fatura\n$row" }
            val corrected = parser.parse("new.pdf", "%PDF-1.4".toByteArray()).single()
            for (oldType in listOfNotNull(corrected.legacyInputType, corrected.legacyScopedInputType).distinct()) {
                val old = corrected.copy(type = oldType, legacyInputType = null, legacyScopedInputType = null)
                assertLegacyClaimsRecognized(old, corrected)
            }
        }
    }

    @Test fun distinctAccountsAreIndependentButEquivalentLabelsStayProtectedAfterBackupRestore() = runBlocking {
        withDatabase { db ->
            val repository = RoomStatementImportRepository(db)
            val confirm = ConfirmStatementImportUseCase(repository, DefaultDateProvider())
            val raw = RawStatementEntry("Tarifa", Money(1000), LocalDate.of(2026, 11, 1), TransactionType.EXPENSE)
            val suggestions = listOf(CategorySuggestion(raw, Category(1, "Outros", CategoryType.EXPENSE), 1f))
            confirm("first.csv", suggestions, "Cartão pessoal")
            confirm("second.csv", suggestions, "Conta conjunta")
            assertEquals(2, db.transactionDao().getAllTransactions().size)
            val backup = RoomBackupRepository(db)
            backup.restoreBackup(backup.createBackup())
            for (source in listOf("  CARTÃO   PESSOAL  ", "Conta conjunta")) {
                assertTrue(runCatching {
                    confirm("repeat.csv", suggestions, source)
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
                repository.confirmImport(statement("  conta a "), listOf(transaction()), "scoped-a", listOf("canonical-v2", "old-v1"))
            }.exceptionOrNull() is DuplicateStatementImportException)
            repository.confirmImport(statement("Conta B"), listOf(transaction()), "scoped-b", listOf("canonical-v2", "old-v1"))
            assertEquals(1, db.transactionDao().getAllTransactions().size)
            assertTrue(runCatching {
                repository.confirmImport(statement("Conta B"), listOf(transaction()), "scoped-b", listOf("canonical-v2", "old-v1"))
            }.exceptionOrNull() is DuplicateStatementImportException)
        }
    }

    @Test fun unlabeledLegacyBatchRemainsBlockedUntilADifferentSourceIsIdentified() = runBlocking {
        withDatabase { db ->
            db.statementImportDao().insertImport(StatementImportEntity(fileName = "old.csv", importedAt = LocalDateTime.of(2026, 11, 1, 9, 0),
                transactionCount = 1, fingerprint = "content"))
            val repository = RoomStatementImportRepository(db)
            assertTrue(runCatching {
                repository.confirmImport(statement("  "), listOf(transaction()), "scoped-unknown", listOf("content"))
            }.exceptionOrNull() is DuplicateStatementImportException)
            repository.confirmImport(statement("Outra conta"), listOf(transaction()), "scoped-other", listOf("content"))
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
            imports.undoImport(first)
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
