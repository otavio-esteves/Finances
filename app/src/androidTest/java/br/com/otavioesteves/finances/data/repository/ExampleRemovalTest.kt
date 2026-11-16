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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ExampleRemovalTest {
    private fun database() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
    ).build()

    @Test
    fun removalKeepsRealTransactionsAndOtherYearsAndUpdatesTheMagnitudeTotal() = runBlocking {
        val db = database()
        try {
            db.categoryDao().insertCategories(listOf(CategoryEntity(1, "Outros", CategoryType.EXPENSE)))
            val sample = TransactionEntity(1, "[EXEMPLO] Mercado", 1000, 1, LocalDate.of(2026, 1, 1),
                TransactionType.EXPENSE, EXAMPLE_TRANSACTIONS_NOTE)
            val kept = listOf(sample.copy(id = 2, notes = null), sample.copy(id = 3, date = LocalDate.of(2025, 1, 1)),
                sample.copy(id = 4, notes = "${EXAMPLE_TRANSACTIONS_NOTE}_OUTRO"))
            db.transactionDao().insertTransactions(kept + sample + sample.copy(id = 5, amountCents = 2000))
            val repository = RoomTransactionsRepository(db.transactionDao(), db)
            repository.removeTransactionsByNoteIfCurrent(2026, EXAMPLE_TRANSACTIONS_NOTE, repository.dataRevision)
            assertEquals(kept.sortedBy { it.id }, db.transactionDao().getAllTransactions().sortedBy { it.id })
            assertEquals(3000L, db.transactionDao().storedMagnitudeTotal())
            repository.removeTransactionsByNoteIfCurrent(2026, EXAMPLE_TRANSACTIONS_NOTE, repository.dataRevision)
            assertEquals(3000L, db.transactionDao().storedMagnitudeTotal())
        } finally { db.close() }
    }

    @Test
    fun aDeleteQueuedBehindRestoreCannotRemoveReusedIdsOrRestoredExamples() = runBlocking {
        withTimeout(15_000) {
            val db = database()
            val gate = CompletableDeferred<Unit>()
            try {
                db.categoryDao().insertCategories(listOf(CategoryEntity(1, "Outros", CategoryType.EXPENSE)))
                db.transactionDao().insertTransaction(TransactionEntity(1, "Exemplo antigo", 1000, 1,
                    LocalDate.of(2026, 1, 1), TransactionType.EXPENSE, EXAMPLE_TRANSACTIONS_NOTE))
                val repository = RoomTransactionsRepository(db.transactionDao(), db)
                val revision = repository.dataRevision
                val backup = BackupModel(
                    categories = listOf(CategoryExportModel(1, "Outros", CategoryType.EXPENSE)),
                    transactions = listOf(
                        TransactionExportModel(1, "Despesa real", 5000, 1, "2026-01-01", TransactionType.EXPENSE, null),
                        TransactionExportModel(2, "Exemplo restaurado", 1000, 1, "2026-01-01", TransactionType.EXPENSE, EXAMPLE_TRANSACTIONS_NOTE)
                    )
                )
                val replaced = CompletableDeferred<Unit>()
                val restoring = async {
                    db.withTransaction {
                        RoomBackupRepository(db).restoreBackup(backup)
                        replaced.complete(Unit)
                        gate.await()
                    }
                }
                replaced.await()
                val deleting = async(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        repository.removeTransactionsByNoteIfCurrent(2026, EXAMPLE_TRANSACTIONS_NOTE, revision)
                        false
                    } catch (_: StaleTransactionEditException) { true }
                }
                assertFalse(deleting.isCompleted)
                gate.complete(Unit)
                restoring.await()
                assertTrue(deleting.await())
                assertEquals(listOf(1L, 2L), db.transactionDao().getAllTransactions().map { it.id }.sorted())
                assertEquals(6000L, db.transactionDao().storedMagnitudeTotal())

                repository.removeTransactionsByNoteIfCurrent(2026, EXAMPLE_TRANSACTIONS_NOTE, repository.dataRevision)
                assertEquals(listOf(1L), db.transactionDao().getAllTransactions().map { it.id })
                assertEquals(5000L, db.transactionDao().storedMagnitudeTotal())
            } finally { gate.complete(Unit); db.close() }
        }
    }
}
