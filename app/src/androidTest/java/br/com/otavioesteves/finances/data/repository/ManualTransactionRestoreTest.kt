package br.com.otavioesteves.finances.data.repository

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
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
class ManualTransactionRestoreTest {
    private fun database() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
    ).build()

    private fun transaction() = Transaction(0, "Compra", Money(100), 1,
        LocalDate.of(2026, 11, 1), TransactionType.EXPENSE)

    @Test fun queuedInsertRejectsRestoredCategoriesWithReusedIdsAndTypesEvenWhenTheirNamesMatch() = runBlocking {
        withTimeout(30_000) {
            for (restoredName in listOf("Saúde", "Alimentação")) {
                val db = database()
                val gate = CompletableDeferred<Unit>()
                try {
                    db.categoryDao().insertCategories(listOf(CategoryEntity(1, "Alimentação", CategoryType.EXPENSE)))
                    val repository = RoomTransactionsRepository(db.transactionDao(), db)
                    val backups = RoomBackupRepository(db)
                    val revision = repository.dataRevision
                    val replaced = CompletableDeferred<BackupModel>()
                    val restoring = async {
                        db.withTransaction {
                            backups.restoreBackup(BackupModel(
                                categories = listOf(CategoryExportModel(1, restoredName, CategoryType.EXPENSE)),
                                transactions = emptyList()
                            ))
                            replaced.complete(backups.createBackup())
                            gate.await()
                        }
                    }
                    val restored = replaced.await()
                    val saving = async(start = CoroutineStart.UNDISPATCHED) {
                        runCatching { repository.addTransactionIfCurrent(transaction(), revision) }
                    }
                    assertFalse(saving.isCompleted)
                    gate.complete(Unit)
                    restoring.await()
                    assertTrue(saving.await().exceptionOrNull() is StaleTransactionEditException)
                    assertEquals(restored, backups.createBackup())
                    assertEquals(0L, db.transactionDao().storedMagnitudeTotal())
                    assertTrue(db.categoryMemoryDao().getAll().isEmpty())

                    // A new form belongs to the replacement and can save normally.
                    repository.addTransactionIfCurrent(transaction(), repository.dataRevision)
                    assertEquals(1, db.transactionDao().getAllTransactions().size)
                    assertEquals(100L, db.transactionDao().storedMagnitudeTotal())
                    val fresh = backups.createBackup()
                    backups.restoreBackup(fresh)
                    assertEquals(fresh, backups.createBackup())
                } finally { gate.complete(Unit); db.close() }
            }
        }
    }

    @Test fun completedRestoreRejectsAnOldFormWithoutChangingRowsTotalsOrCategoryMemory() = runBlocking {
        val db = database()
        try {
            db.categoryDao().insertCategories(listOf(CategoryEntity(1, "Alimentação", CategoryType.EXPENSE)))
            val repository = RoomTransactionsRepository(db.transactionDao(), db)
            val revision = repository.dataRevision
            val backups = RoomBackupRepository(db)
            backups.restoreBackup(BackupModel(
                categories = listOf(CategoryExportModel(1, "Saúde", CategoryType.EXPENSE)),
                transactions = listOf(TransactionExportModel(7, "Restaurada", 999, 1,
                    "2026-11-02", TransactionType.EXPENSE, null)),
                categoryMemories = listOf(CategoryMemoryExportModel("restaurada", TransactionType.EXPENSE, 1))
            ))
            val restored = backups.createBackup()
            assertTrue(runCatching {
                repository.addTransactionIfCurrent(transaction(), revision)
            }.exceptionOrNull() is StaleTransactionEditException)
            assertEquals(restored, backups.createBackup())
            assertEquals(999L, db.transactionDao().storedMagnitudeTotal())
        } finally { db.close() }
    }

    @Test fun initialRevisionReadWaitsForAnUncommittedRestoreBeforeLoadingCategories() = runBlocking {
        withTimeout(15_000) {
            val db = database()
            val gate = CompletableDeferred<Unit>()
            try {
                db.categoryDao().insertCategories(listOf(CategoryEntity(1, "Alimentação", CategoryType.EXPENSE)))
                val repository = RoomTransactionsRepository(db.transactionDao(), db)
                val replaced = CompletableDeferred<Unit>()
                val restoring = async {
                    db.withTransaction {
                        RoomBackupRepository(db).restoreBackup(BackupModel(
                            categories = listOf(CategoryExportModel(1, "Saúde", CategoryType.EXPENSE)),
                            transactions = emptyList()
                        ))
                        replaced.complete(Unit)
                        gate.await()
                    }
                }
                replaced.await()
                val revision = repository.dataRevision
                val reading = async(start = CoroutineStart.UNDISPATCHED) {
                    repository.getDataRevision() to RoomCategoriesRepository(db.categoryDao(), db.transactionDao())
                        .getCategories().first()
                }
                assertFalse(reading.isCompleted)
                gate.complete(Unit)
                restoring.await()
                val (readRevision, categories) = reading.await()
                assertEquals(revision, readRevision)
                assertEquals("Saúde", categories.single { it.id == 1L }.name)
            } finally { gate.complete(Unit); db.close() }
        }
    }
}
