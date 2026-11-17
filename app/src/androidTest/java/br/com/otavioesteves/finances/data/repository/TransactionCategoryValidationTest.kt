package br.com.otavioesteves.finances.data.repository

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.mapper.toEntity
import br.com.otavioesteves.finances.domain.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TransactionCategoryValidationTest {
    private fun database() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
    ).build()

    private suspend fun seed(db: AppDatabase): Transaction {
        db.categoryDao().insertCategories(listOf(
            CategoryEntity(1, "Mercado", CategoryType.EXPENSE),
            CategoryEntity(2, "Salário", CategoryType.INCOME),
            CategoryEntity(3, "Transferência", CategoryType.TRANSFER)
        ))
        val repository = RoomTransactionsRepository(db.transactionDao(), db)
        repository.addTransaction(Transaction(0, "Compra", Money(100), 1,
            LocalDate.of(2026, 11, 1), TransactionType.EXPENSE))
        return repository.getTransactions(MonthPeriod(2026, 11)).first().single()
    }

    @Test fun invalidInsertsAndEditsPreserveRowsTotalsMemoriesAndRestorableBackup() = runBlocking {
        val db = database()
        try {
            val original = seed(db)
            val repository = RoomTransactionsRepository(db.transactionDao(), db)
            val backups = RoomBackupRepository(db)
            val before = backups.createBackup()
            val invalid = listOf(
                original.copy(categoryId = 2),
                original.copy(type = TransactionType.INCOME),
                original.copy(categoryId = 99)
            )
            for (candidate in invalid) {
                for (write in listOf<suspend () -> Unit>(
                    { repository.addTransaction(candidate.copy(id = 0)) },
                    { repository.updateTransaction(candidate) },
                    { repository.updateTransactionIfCurrent(candidate, db.dataRevision) }
                )) {
                    assertTrue(runCatching { write() }.exceptionOrNull() is IllegalArgumentException)
                    assertEquals(before, backups.createBackup())
                    assertEquals(100L, db.transactionDao().storedMagnitudeTotal())
                    assertEquals(100L, db.transactionDao().initialMagnitudeTotal())
                    assertTrue(db.categoryMemoryDao().getAll().isEmpty())
                }
            }
            backups.restoreBackup(before)
            assertEquals(before, backups.createBackup())
        } finally { db.close() }
    }

    @Test fun mixedBatchIsRejectedAtomicallyWhileCompatibleTypesAndZeroRemainSupported() = runBlocking {
        val db = database()
        try {
            val original = seed(db)
            val backups = RoomBackupRepository(db)
            val before = backups.createBackup()
            val valid = original.copy(id = 0, amount = Money(25))
            val invalid = valid.copy(categoryId = 2)
            assertTrue(runCatching {
                db.transactionDao().insertTransactions(listOf(valid.toEntity(), invalid.toEntity()))
            }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(before, backups.createBackup())
            assertEquals(100L, db.transactionDao().storedMagnitudeTotal())

            db.transactionDao().insertTransactions(listOf(
                valid.toEntity(),
                valid.copy(categoryId = 2, type = TransactionType.INCOME, amount = Money.Zero).toEntity(),
                valid.copy(categoryId = 3, type = TransactionType.TRANSFER, amount = Money.Zero).toEntity()
            ))
            assertEquals(4, db.transactionDao().getAllTransactions().size)
            assertEquals(125L, db.transactionDao().storedMagnitudeTotal())
            val backup = backups.createBackup()
            backups.restoreBackup(backup)
            assertEquals(backup, backups.createBackup())
        } finally { db.close() }
    }

    @Test fun queuedManualInsertValidatesTheCategoryTypeAfterRestoreCommits() = runBlocking {
        withTimeout(15_000) {
            val db = database()
            val gate = CompletableDeferred<Unit>()
            try {
                val selected = seed(db).copy(id = 0)
                val repository = RoomTransactionsRepository(db.transactionDao(), db)
                val backups = RoomBackupRepository(db)
                val replaced = CompletableDeferred<Unit>()
                val restoring = async {
                    db.withTransaction {
                        backups.restoreBackup(BackupModel(
                            categories = listOf(CategoryExportModel(1, "Salário restaurado", CategoryType.INCOME)),
                            transactions = emptyList()
                        ))
                        replaced.complete(Unit)
                        gate.await()
                    }
                }
                replaced.await()
                val saving = async(start = CoroutineStart.UNDISPATCHED) {
                    runCatching { repository.addTransaction(selected) }
                }
                assertFalse(saving.isCompleted)
                gate.complete(Unit)
                restoring.await()
                assertTrue(saving.await().exceptionOrNull() is IllegalArgumentException)
                assertTrue(db.transactionDao().getAllTransactions().isEmpty())
                assertEquals(0L, db.transactionDao().storedMagnitudeTotal())
                assertTrue(db.categoryMemoryDao().getAll().isEmpty())
                val restored = backups.createBackup()
                backups.restoreBackup(restored)
                assertEquals(restored, backups.createBackup())
            } finally { gate.complete(Unit); db.close() }
        }
    }
}
