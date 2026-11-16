package br.com.otavioesteves.finances.data.repository

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class ImportUndoRestoreTest {
    private fun database() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
    ).build()

    private suspend fun seed(db: AppDatabase): ImportedStatement {
        db.categoryDao().insertCategories(listOf(
            CategoryEntity(1, "Outros", CategoryType.EXPENSE),
            CategoryEntity(2, "Salário", CategoryType.INCOME),
            CategoryEntity(3, "Transferência", CategoryType.TRANSFER)
        ))
        val repository = RoomStatementImportRepository(db)
        repository.confirmImport(
            ImportedStatement(0, "fatura.csv", LocalDateTime.of(2026, 11, 1, 9, 0), 1),
            listOf(Transaction(0, "Compra antiga", Money(100), 1, LocalDate.of(2026, 11, 1), TransactionType.EXPENSE)),
            "original"
        )
        return repository.getImports().first().single()
    }

    @Test fun staleHistoryCannotUndoARestoredBatchEvenWithIdenticalDisplayMetadata() = runBlocking {
        val db = database()
        try {
            val old = seed(db)
            val backups = RoomBackupRepository(db)
            val original = backups.createBackup()
            backups.restoreBackup(original.copy(
                transactions = original.transactions.map { it.copy(description = "Compra restaurada", amountCents = 9999) },
                statementImports = original.statementImports.map { it.copy(fingerprint = "replacement") }
            ))
            val repository = RoomStatementImportRepository(db)
            val restored = backups.createBackup()
            val fresh = repository.getImports().first().single()
            assertEquals(old.copy(dataRevision = null), fresh.copy(dataRevision = null))
            assertNotEquals(old.dataRevision, fresh.dataRevision)
            for (stale in listOf(old, old.copy(dataRevision = null))) {
                try { repository.undoImport(stale); fail("Stale or unversioned history must be rejected") }
                catch (_: StaleStatementImportException) { }
                assertEquals(restored, backups.createBackup())
                assertEquals(9999L, db.transactionDao().storedMagnitudeTotal())
            }
            repository.undoImport(fresh)
            assertTrue(repository.getImports().first().isEmpty())
            assertTrue(db.transactionDao().getAllTransactions().isEmpty())
            assertEquals(0L, db.transactionDao().storedMagnitudeTotal())
        } finally { db.close() }
    }

    @Test fun queuedUndoAndHistorySnapshotSerializeWithRestore() = runBlocking {
        withTimeout(20_000) {
            val db = database()
            val gate = CompletableDeferred<Unit>()
            try {
                val old = seed(db)
                val repository = RoomStatementImportRepository(db)
                val backups = RoomBackupRepository(db)
                val backup = backups.createBackup()
                val replaced = CompletableDeferred<Unit>()
                val restoring = async {
                    db.withTransaction {
                        backups.restoreBackup(backup)
                        replaced.complete(Unit)
                        gate.await()
                    }
                }
                replaced.await()
                val undoing = async(start = CoroutineStart.UNDISPATCHED) {
                    try { repository.undoImport(old); false } catch (_: StaleStatementImportException) { true }
                }
                val reading = async(start = CoroutineStart.UNDISPATCHED) { repository.getImports().first().single() }
                assertFalse(undoing.isCompleted)
                assertFalse(reading.isCompleted)
                gate.complete(Unit)
                restoring.await()
                assertTrue(undoing.await())
                assertEquals(db.dataRevision, reading.await().dataRevision)
                assertEquals(backup, backups.createBackup())
            } finally { gate.complete(Unit); db.close() }
        }
    }
}
