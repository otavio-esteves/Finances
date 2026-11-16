package br.com.otavioesteves.finances.data.repository

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.domain.usecase.ConfirmStatementImportUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class ImportReviewRestoreTest {
    private val date = LocalDate.of(2026, 11, 15)
    private val clock = object : DateProvider {
        override fun getCurrentDate() = date
        override fun getCurrentDateTime() = LocalDateTime.of(2026, 11, 15, 12, 0)
        override fun getCurrentMonthPeriod() = MonthPeriod(2026, 11)
    }
    private val food = Category(1, "Alimentação", CategoryType.EXPENSE)
    private fun suggestions() = listOf(CategorySuggestion(
        RawStatementEntry("Mercado", Money(15000), date, TransactionType.EXPENSE),
        food, 1f, SuggestionSource.USER, rememberCorrection = true
    ))
    private fun database() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
    ).build()

    @Test fun restoreRejectsOldReviewsEvenWhenTheCategoryStillHasTheSameIdAndType() = runBlocking {
        val db = database()
        try {
            db.categoryDao().insertCategories(listOf(CategoryEntity(1, food.name, food.type)))
            val imports = RoomStatementImportRepository(db)
            val confirm = ConfirmStatementImportUseCase(imports, clock)
            val reviewedRevision = confirm.getDataRevision()
            val backups = RoomBackupRepository(db)
            backups.restoreBackup(backups.createBackup())
            val restored = backups.createBackup()
            try {
                confirm("extrato.csv", suggestions(), dataRevision = reviewedRevision)
                fail("A review made before restore must be rejected")
            } catch (_: StaleStatementImportException) { }
            assertEquals(restored, backups.createBackup())
            assertEquals(0L, db.transactionDao().storedMagnitudeTotal())
            assertTrue(db.categoryMemoryDao().getAll().isEmpty())

            confirm("extrato.csv", suggestions(), dataRevision = confirm.getDataRevision())
            assertEquals(1, imports.getImports().first().size)
            assertEquals(15000L, db.transactionDao().storedMagnitudeTotal())
            assertEquals(1, db.categoryMemoryDao().getAll().size)
            val valid = backups.createBackup()
            backups.restoreBackup(valid)
            assertEquals(valid, backups.createBackup())
        } finally { db.close() }
    }

    @Test fun currentRevisionStillValidatesActualCategoryTypesBeforeAnyWrite() = runBlocking {
        val db = database()
        try {
            val backups = RoomBackupRepository(db)
            backups.restoreBackup(BackupModel(
                categories = listOf(CategoryExportModel(1, "Salário", CategoryType.INCOME)), transactions = emptyList()
            ))
            val before = backups.createBackup()
            val confirm = ConfirmStatementImportUseCase(RoomStatementImportRepository(db), clock)
            assertTrue(runCatching { confirm("extrato.csv", suggestions(), dataRevision = confirm.getDataRevision()) }
                .exceptionOrNull() is IllegalArgumentException)
            assertEquals(before, backups.createBackup())
            assertEquals(0L, db.transactionDao().storedMagnitudeTotal())
            assertTrue(db.categoryMemoryDao().getAll().isEmpty())
        } finally { db.close() }
    }

    @Test fun queuedConfirmationAndRevisionSnapshotSerializeWithRestore() = runBlocking {
        withTimeout(20_000) {
            val db = database()
            val gate = CompletableDeferred<Unit>()
            try {
                db.categoryDao().insertCategories(listOf(
                    CategoryEntity(1, food.name, food.type), CategoryEntity(2, "Salário", CategoryType.INCOME),
                    CategoryEntity(3, "Transferência", CategoryType.TRANSFER)
                ))
                val imports = RoomStatementImportRepository(db)
                val confirm = ConfirmStatementImportUseCase(imports, clock)
                val oldRevision = confirm.getDataRevision()
                val backups = RoomBackupRepository(db)
                val original = backups.createBackup()
                val replaced = CompletableDeferred<Unit>()
                val restoring = async {
                    db.withTransaction {
                        backups.restoreBackup(original)
                        replaced.complete(Unit)
                        gate.await()
                    }
                }
                replaced.await()
                val saving = async(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        confirm("extrato.csv", suggestions(), dataRevision = oldRevision)
                        false
                    } catch (_: StaleStatementImportException) { true }
                }
                val snapshot = async(start = CoroutineStart.UNDISPATCHED) { confirm.getDataRevision() }
                assertFalse(saving.isCompleted)
                assertFalse(snapshot.isCompleted)
                gate.complete(Unit)
                restoring.await()
                assertTrue(saving.await())
                assertNotEquals(oldRevision, snapshot.await())
                assertEquals(db.dataRevision, snapshot.await())
                assertEquals(original, backups.createBackup())
            } finally { gate.complete(Unit); db.close() }
        }
    }

    @Test fun reviewedImportRequiresACapturedGeneration() = runBlocking {
        val db = database()
        try {
            db.categoryDao().insertCategories(listOf(CategoryEntity(1, food.name, food.type)))
            val imports = RoomStatementImportRepository(db)
            try {
                imports.confirmReviewedImport(
                    ImportedStatement(0, "extrato.csv", clock.getCurrentDateTime(), 1),
                    listOf(Transaction(0, "Mercado", Money(15000), 1, date, TransactionType.EXPENSE)),
                    "unversioned", emptyList(), emptyList()
                )
                fail("An unversioned review must be rejected")
            } catch (_: StaleStatementImportException) { }
            assertTrue(imports.getImports().first().isEmpty())
            assertTrue(db.transactionDao().getAllTransactions().isEmpty())
        } finally { db.close() }
    }
}
