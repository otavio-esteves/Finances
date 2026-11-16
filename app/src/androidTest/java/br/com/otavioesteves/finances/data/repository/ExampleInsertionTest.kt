package br.com.otavioesteves.finances.data.repository

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.domain.usecase.EXAMPLE_TRANSACTIONS_NOTE
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ExampleInsertionTest {
    private fun database() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
    ).build()
    private suspend fun seed(db: AppDatabase) {
        db.categoryDao().insertCategories(listOf(CategoryEntity(1, "Outros", CategoryType.EXPENSE), CategoryEntity(2, "Salário", CategoryType.INCOME)))
    }

    @Test fun laterDatesKeepClampedExamplesAndManualRowsNeverSuppressExamples() = runBlocking {
        val db = database()
        try {
            seed(db)
            val repository = RoomTransactionsRepository(db.transactionDao(), db)
            repository.addExamples2026IfCurrent(LocalDate.of(2026, 1, 1), repository.dataRevision)
            val january = db.transactionDao().getAllTransactions()
            assertEquals(6, january.size)
            assertTrue(january.all { it.date.dayOfMonth == 1 })
            val manual = january.first().copy(id = 1000, date = LocalDate.of(2026, 2, 1), notes = null)
            db.transactionDao().insertTransaction(manual)
            for (day in listOf(5, 20, 31)) repository.addExamples2026IfCurrent(LocalDate.of(2026, 1, day), repository.dataRevision)
            for (day in listOf(1, 5, 28)) repository.addExamples2026IfCurrent(LocalDate.of(2026, 2, day), repository.dataRevision)
            val rows = db.transactionDao().getAllTransactions()
            assertEquals(13, rows.size)
            assertEquals(january, rows.filter { it.date.monthValue == 1 })
            assertEquals(manual, rows.single { it.notes == null })
            assertEquals(rows.sumOf { it.amountCents }, db.transactionDao().storedMagnitudeTotal())
        } finally { db.close() }
    }

    @Test fun queuedInsertionCannotUseCategoriesFromBeforeRestoreAndFreshRetryRoundTrips() = runBlocking {
        withTimeout(20_000) {
            val db = database()
            val gate = CompletableDeferred<Unit>()
            try {
                seed(db)
                val repository = RoomTransactionsRepository(db.transactionDao(), db)
                val oldRevision = repository.dataRevision
                val backups = RoomBackupRepository(db)
                val replacement = BackupModel(
                    categories = listOf(CategoryExportModel(1, "Receitas", CategoryType.INCOME), CategoryExportModel(2, "Despesas", CategoryType.EXPENSE)),
                    transactions = listOf(TransactionExportModel(1, "Real restaurada", 1234, 2, "2026-11-01", TransactionType.EXPENSE, null))
                )
                val replaced = CompletableDeferred<Unit>()
                val restoring = async {
                    db.withTransaction { backups.restoreBackup(replacement); replaced.complete(Unit); gate.await() }
                }
                replaced.await()
                val adding = async(start = CoroutineStart.UNDISPATCHED) {
                    try { repository.addExamples2026IfCurrent(LocalDate.of(2026, 11, 15), oldRevision); false }
                    catch (_: StaleTransactionEditException) { true }
                }
                assertFalse(adding.isCompleted)
                gate.complete(Unit)
                restoring.await()
                assertTrue(adding.await())
                assertEquals(1, db.transactionDao().getAllTransactions().size)
                assertEquals(1234L, db.transactionDao().storedMagnitudeTotal())
                repository.addExamples2026IfCurrent(LocalDate.of(2026, 11, 15), repository.dataRevision)
                val backup = backups.createBackup()
                assertEquals(61, backup.transactions.size)
                val types = backup.categories.associate { it.id to it.type.name }
                assertTrue(backup.transactions.all { types[it.categoryId] == it.type.name })
                backups.restoreBackup(backup)
                assertEquals(backup, backups.createBackup())
            } finally { gate.complete(Unit); db.close() }
        }
    }

    @Test fun overflowingBatchCannotLeaveTheFirstExamplesSaved() = runBlocking {
        val db = database()
        try {
            seed(db)
            val original = TransactionEntity(1, "Real", Long.MAX_VALUE - 500_000, 1, LocalDate.of(2026, 1, 1), TransactionType.EXPENSE, null)
            db.transactionDao().insertTransaction(original)
            val repository = RoomTransactionsRepository(db.transactionDao(), db)
            try { repository.addExamples2026IfCurrent(LocalDate.of(2026, 1, 1), repository.dataRevision); fail("The whole batch must be rejected") }
            catch (_: IllegalArgumentException) { }
            assertEquals(listOf(original), db.transactionDao().getAllTransactions())
            assertEquals(original.amountCents, db.transactionDao().storedMagnitudeTotal())
        } finally { db.close() }
    }

    @Test fun concurrentRequestsInsertEachMonthlyExampleOnlyOnce() = runBlocking {
        withTimeout(20_000) {
            val db = database()
            try {
                seed(db)
                val repository = RoomTransactionsRepository(db.transactionDao(), db)
                val today = LocalDate.of(2026, 11, 15)
                listOf(async { repository.addExamples2026IfCurrent(today, 0) }, async { repository.addExamples2026IfCurrent(today, 0) }).awaitAll()
                val rows = db.transactionDao().getAllTransactions()
                assertEquals(60, rows.size)
                assertTrue(rows.all { it.notes == EXAMPLE_TRANSACTIONS_NOTE })
                assertEquals(rows.sumOf { it.amountCents }, db.transactionDao().storedMagnitudeTotal())
            } finally { db.close() }
        }
    }
}
