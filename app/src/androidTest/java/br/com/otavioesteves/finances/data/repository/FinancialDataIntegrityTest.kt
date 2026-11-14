package br.com.otavioesteves.finances.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.DefaultDateProvider
import br.com.otavioesteves.finances.data.ai.RememberingTransactionCategorizer
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.mapper.toEntity
import br.com.otavioesteves.finances.data.local.mapper.toDomain
import br.com.otavioesteves.finances.domain.ai.TransactionCategorizer
import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.domain.usecase.ConfirmStatementImportUseCase
import br.com.otavioesteves.finances.domain.usecase.CreateBackupUseCase
import br.com.otavioesteves.finances.domain.usecase.RestoreBackupUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class FinancialDataIntegrityTest {
    private val expense = Category(1, "Outros", CategoryType.EXPENSE)
    private val transfer = Category(2, "Transferência", CategoryType.TRANSFER)
    private val secondTransfer = Category(3, "Entre contas", CategoryType.TRANSFER)
    private val income = Category(4, "Receita", CategoryType.INCOME)
    private val categories = listOf(expense, transfer, secondTransfer, income)
    private val date = LocalDate.of(2026, 11, 1)
    private fun entry(amount: Long = 100) = Transaction(0, "Compra", Money(amount), expense.id, date, TransactionType.EXPENSE)
    private suspend fun <T> withDatabase(block: suspend (AppDatabase) -> T): T {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        return try {
            db.categoryDao().insertCategories(categories.map { CategoryEntity(it.id, it.name, it.type) })
            block(db)
        } finally { db.close() }
    }

    @Test fun magnitudeBudgetTracksReplacementDeletionUndoAndRestore() = runBlocking {
        withDatabase { db ->
            val dao = db.transactionDao()
            val repository = RoomTransactionsRepository(dao, db)
            suspend fun assertBudget(expected: Long) {
                assertEquals(expected, dao.storedMagnitudeTotal())
                assertEquals(expected, dao.initialMagnitudeTotal())
            }
            repository.addTransaction(entry(100))
            val first = dao.getAllTransactions().single().toDomain()
            assertBudget(100)
            repository.updateTransaction(first.copy(amount = Money(200)))
            assertBudget(200)
            dao.insertTransactions(listOf(first.copy(amount = Money(300)).toEntity()))
            assertBudget(300)
            val raw = RawStatementEntry("Importada", Money(400), date, TransactionType.EXPENSE)
            ConfirmStatementImportUseCase(RoomStatementImportRepository(db), DefaultDateProvider())(
                "a.csv", listOf(CategorySuggestion(raw, expense, 1f, SuggestionSource.USER)))
            assertBudget(700)
            try {
                repository.addTransaction(entry(Long.MAX_VALUE))
                fail("Overflow must be rejected")
            } catch (_: IllegalArgumentException) { }
            assertBudget(700)
            val backups = RoomBackupRepository(db)
            val backup = backups.createBackup()
            RoomStatementImportRepository(db).undoImport(db.statementImportDao().getAllImportsForBackup().single().id)
            assertBudget(300)
            backups.restoreBackup(backup)
            assertBudget(700)
            repository.removeTransaction(first.id)
            assertBudget(400)
            dao.deleteAll()
            assertBudget(0)
        }
    }

    @Test fun originalMemoryKeySurvivesReviewEditsLaterCorrectionsAndJsonBackup() = runBlocking {
        withDatabase { db ->
            val raw = RawStatementEntry("CAFÉ 123", Money(1500), date, TransactionType.EXPENSE)
            val reviewed = CategorySuggestion(raw.copy(description = "Pagamento corrigido", type = TransactionType.TRANSFER),
                transfer, 1f, SuggestionSource.USER, originalType = raw.type, originalEntry = raw, rememberCorrection = true)
            ConfirmStatementImportUseCase(RoomStatementImportRepository(db), DefaultDateProvider())("a.csv", listOf(reviewed))
            val repository = RoomTransactionsRepository(db.transactionDao(), db)
            val imported = db.transactionDao().getAllTransactions().single().toDomain()
            repository.updateTransaction(imported.copy(categoryId = secondTransfer.id, description = "Descrição editada"))
            val fallback = object : TransactionCategorizer {
                override suspend fun categorize(entries: List<RawStatementEntry>, categories: List<Category>,
                    onProgress: (Int, Int) -> Unit): Result<List<CategorySuggestion>> = error("The original memory must resolve this entry")
            }
            val remembering = RememberingTransactionCategorizer(fallback, RoomCategoryMemoryRepository(db.categoryMemoryDao()))
            assertEquals(secondTransfer.id, remembering.categorize(listOf(raw), categories).getOrThrow().single().suggestedCategory?.id)
            val backups = RoomBackupRepository(db)
            val json = CreateBackupUseCase(backups)()
            RestoreBackupUseCase(backups)(json)
            val restored = db.transactionDao().getAllTransactions().single().toDomain()
            assertEquals("cafe 123", restored.categoryMemoryDescriptionKey)
            assertEquals(TransactionType.EXPENSE, restored.categoryMemoryInputType)
            assertEquals(imported.importId, restored.importId)
            repository.updateTransaction(restored.copy(categoryId = income.id, type = TransactionType.INCOME))
            val next = remembering.categorize(listOf(raw.copy(date = date.plusMonths(1))), categories).getOrThrow().single()
            assertEquals(income.id, next.suggestedCategory?.id)
            assertEquals(TransactionType.INCOME, next.entry.type)
            val memory = db.categoryMemoryDao().getAll().single()
            assertEquals("cafe 123", memory.descriptionKey)
            assertEquals(TransactionType.EXPENSE, memory.inputType)
        }
    }

    @Test fun manualCorrectionsKeepTheirFirstMemoryKeyAcrossTypeAndDescriptionEdits() = runBlocking {
        withDatabase { db ->
            val repository = RoomTransactionsRepository(db.transactionDao(), db)
            repository.addTransaction(entry())
            val original = db.transactionDao().getAllTransactions().single().toDomain()
            repository.updateTransaction(original.copy(type = TransactionType.TRANSFER, categoryId = transfer.id))
            val changed = db.transactionDao().getAllTransactions().single().toDomain()
            repository.updateTransaction(changed.copy(description = "Outra descrição", categoryId = secondTransfer.id))
            val memory = db.categoryMemoryDao().getAll().single()
            assertEquals("compra", memory.descriptionKey)
            assertEquals(TransactionType.EXPENSE, memory.inputType)
            assertEquals(secondTransfer.id, memory.categoryId)
        }
    }

    @Test fun overflowingBackupAndOutOfRangeDatesLeaveAllCurrentDataUntouched() = runBlocking {
        withDatabase { db ->
            RoomTransactionsRepository(db.transactionDao(), db).addTransaction(entry())
            val repository = RoomBackupRepository(db)
            val backup = repository.createBackup()
            val first = backup.transactions.single()
            val invalid = listOf(
                backup.copy(transactions = listOf(first.copy(amountCents = Long.MAX_VALUE), first.copy(id = 2, amountCents = 1))),
                backup.copy(transactions = listOf(first.copy(date = "0000-01-01"))),
                backup.copy(transactions = listOf(first.copy(date = "+10000-01-01"))),
                backup.copy(transactions = listOf(first.copy(date = "2026-02-31"))),
                backup.copy(transactions = listOf(first.copy(categoryMemoryDescriptionKey = "compra")))
            )
            for (candidate in invalid) {
                assertTrue(runCatching { repository.restoreBackup(candidate) }.exceptionOrNull() is IllegalArgumentException)
                assertEquals(backup, repository.createBackup())
            }
        }
    }

    @Test fun legacyBackupAndCalendarBoundaryDatesRemainUsable() = runBlocking {
        withDatabase { db ->
            val repository = RoomBackupRepository(db)
            val legacy = BackupModel(version = 4, categories = categories.map { CategoryExportModel(it.id, it.name, it.type) },
                transactions = listOf(
                    TransactionExportModel(1, "Primeiro ano", 0, expense.id, "0001-01-01", TransactionType.EXPENSE, null),
                    TransactionExportModel(2, "Último ano", 100, expense.id, "9999-12-31", TransactionType.EXPENSE, null)))
            repository.restoreBackup(legacy)
            val transactions = RoomTransactionsRepository(db.transactionDao(), db)
            assertEquals("Primeiro ano", transactions.getTransactions(MonthPeriod(1, 1)).first().single().description)
            assertEquals("Último ano", transactions.getTransactions(MonthPeriod(9999, 12)).first().single().description)
            assertTrue(db.transactionDao().getAllTransactions().all { it.categoryMemoryDescriptionKey == null && it.categoryMemoryInputType == null })
        }
    }

    @Test fun concurrentInsertsCannotExceedTheAggregateBudget() = runBlocking {
        withDatabase { db ->
            val repository = RoomTransactionsRepository(db.transactionDao(), db)
            val results = coroutineScope {
                listOf(Long.MAX_VALUE, 1L).map { amount -> async(Dispatchers.IO) { runCatching { repository.addTransaction(entry(amount)) } } }
                    .map { it.await() }
            }
            assertEquals(1, results.count { it.isSuccess })
            assertTrue(results.single { it.isFailure }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(1, db.transactionDao().getAllTransactions().size)
        }
    }

    @Test fun failedImportRollsBackBatchFingerprintTransactionsAndMemories() = runBlocking {
        withDatabase { db ->
            RoomTransactionsRepository(db.transactionDao(), db).addTransaction(entry(Long.MAX_VALUE - 10))
            val imports = RoomStatementImportRepository(db)
            val batch = ImportedStatement(0, "a.csv", java.time.LocalDateTime.of(2026, 11, 1, 9, 0), 2)
            val result = runCatching { imports.confirmReviewedImport(batch, listOf(entry(6), entry(5)), "overflow", emptyList(),
                listOf(CategoryMemory("compra", TransactionType.EXPENSE, expense.id))) }
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertTrue(imports.getImports().first().isEmpty())
            assertFalse(db.statementImportDao().hasFingerprint("overflow"))
            assertTrue(db.categoryMemoryDao().getAll().isEmpty())
            assertEquals(Long.MAX_VALUE - 10, db.transactionDao().getAllTransactions().single().amountCents)
        }
    }

    @Test fun failedEditDoesNotChangeAmountsOrTeachACategory() = runBlocking {
        withDatabase { db ->
            val repository = RoomTransactionsRepository(db.transactionDao(), db)
            repository.addTransaction(entry(100))
            val original = db.transactionDao().getAllTransactions().single().toDomain()
            repository.addTransaction(entry(Long.MAX_VALUE - 100))
            val result = runCatching { repository.updateTransaction(original.copy(amount = Money(101), categoryId = transfer.id, type = TransactionType.TRANSFER)) }
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(original, repository.getTransaction(original.id).first())
            assertTrue(db.categoryMemoryDao().getAll().isEmpty())
            repository.updateTransaction(original.copy(amount = Money(99)))
            repository.addTransaction(entry(1))
            assertEquals(3, db.transactionDao().getAllTransactions().size)
            assertEquals(Money(-Long.MAX_VALUE), repository.getMonthlyBalance(MonthPeriod(2026, 11)).first())
        }
    }
}
