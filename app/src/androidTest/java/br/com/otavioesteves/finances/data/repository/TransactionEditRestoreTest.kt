package br.com.otavioesteves.finances.data.repository

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
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
class TransactionEditRestoreTest {
    @Test
    fun restoreRejectsOldEditsAndDeletesWithoutChangingRowsMemoriesOrTotals() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            val repository = RoomTransactionsRepository(database.transactionDao(), database)
            seed(database)
            val old = requireNotNull(repository.getTransactionForEditing(1))
            RoomBackupRepository(database).restoreBackup(backup())
            val restored = requireNotNull(repository.getTransaction(1).first())
            for (write in listOf<suspend () -> Unit>(
                { repository.updateTransactionIfCurrent(old.transaction.copy(description = "Antiga", categoryId = 2), old.dataRevision) },
                { repository.removeTransactionIfCurrent(1, old.dataRevision) }
            )) {
                try { write(); fail("A stale edit must be rejected") }
                catch (_: StaleTransactionEditException) { }
                assertEquals(restored, repository.getTransaction(1).first())
                assertEquals(99999L, database.transactionDao().storedMagnitudeTotal())
                assertTrue(database.categoryMemoryDao().getAll().isEmpty())
            }

            val fresh = requireNotNull(repository.getTransactionForEditing(1))
            assertNotEquals(old.dataRevision, fresh.dataRevision)
            repository.updateTransactionIfCurrent(fresh.transaction.copy(description = "Atual", amount = Money(1234)), fresh.dataRevision)
            assertEquals("Atual", repository.getTransaction(1).first()?.description)
            assertEquals(1234L, database.transactionDao().storedMagnitudeTotal())
            repository.removeTransactionIfCurrent(1, fresh.dataRevision)
            assertNull(repository.getTransaction(1).first())
            assertEquals(0L, database.transactionDao().storedMagnitudeTotal())
        } finally { database.close() }
    }

    @Test
    fun deleteConfirmationSnapshotRejectsAReplacementWithTheSameId() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            seed(database)
            val repository = RoomTransactionsRepository(database.transactionDao(), database)
            val deleting = br.com.otavioesteves.finances.domain.usecase.DeleteTransactionUseCase(repository)
            val original = requireNotNull(repository.getTransaction(1).first())
            val confirmation = deleting.forConfirmation(original)
            RoomBackupRepository(database).restoreBackup(backup())
            val restored = requireNotNull(repository.getTransaction(1).first())

            try {
                deleting(confirmation.transaction.id, confirmation.dataRevision)
                fail("A confirmation from before restore must not delete the replacement")
            } catch (_: StaleTransactionEditException) { }
            try {
                deleting.forConfirmation(original)
                fail("A stale list row must not open a confirmation for the replacement")
            } catch (_: StaleTransactionEditException) { }
            assertEquals(restored, repository.getTransaction(1).first())
            assertEquals(99999L, database.transactionDao().storedMagnitudeTotal())

            val fresh = deleting.forConfirmation(restored)
            deleting(fresh.transaction.id, fresh.dataRevision)
            assertNull(repository.getTransaction(1).first())
            assertEquals(0L, database.transactionDao().storedMagnitudeTotal())
        } finally { database.close() }
    }

    @Test
    fun queuedEditAndNewSnapshotSerializeWithTheRestoringTransaction() = runBlocking {
        withTimeout(15_000) {
            val database = Room.inMemoryDatabaseBuilder(
                InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
            ).build()
            val gate = CompletableDeferred<Unit>()
            try {
                seed(database)
                val repository = RoomTransactionsRepository(database.transactionDao(), database)
                val old = requireNotNull(repository.getTransactionForEditing(1))
                val replaced = CompletableDeferred<Unit>()
                val restoring = async {
                    database.withTransaction {
                        RoomBackupRepository(database).restoreBackup(backup())
                        replaced.complete(Unit)
                        gate.await()
                    }
                }
                replaced.await()
                val saving = async(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        repository.updateTransactionIfCurrent(old.transaction.copy(notes = "Antiga"), old.dataRevision)
                        false
                    } catch (_: StaleTransactionEditException) { true }
                }
                val reading = async(start = CoroutineStart.UNDISPATCHED) { repository.getTransactionForEditing(1) }
                assertFalse(saving.isCompleted)
                assertFalse(reading.isCompleted)
                gate.complete(Unit)
                restoring.await()
                assertTrue(saving.await())
                val fresh = requireNotNull(reading.await())
                assertEquals("Restaurada", fresh.transaction.description)
                assertEquals(database.dataRevision, fresh.dataRevision)
                assertEquals(99999L, fresh.transaction.amount.cents)
            } finally { gate.complete(Unit); database.close() }
        }
    }

    private suspend fun seed(database: AppDatabase) {
        database.categoryDao().insertCategories(listOf(
            CategoryEntity(1, "Outros", CategoryType.EXPENSE), CategoryEntity(2, "Lazer", CategoryType.EXPENSE)
        ))
        database.transactionDao().insertTransaction(TransactionEntity(1, "Original", 1000, 1,
            LocalDate.of(2026, 11, 1), TransactionType.EXPENSE, null))
    }

    private fun backup() = BackupModel(
        categories = listOf(CategoryExportModel(1, "Outros", CategoryType.EXPENSE), CategoryExportModel(2, "Lazer", CategoryType.EXPENSE)),
        transactions = listOf(TransactionExportModel(1, "Restaurada", 99999, 1, "2026-11-01", TransactionType.EXPENSE, null))
    )
}
