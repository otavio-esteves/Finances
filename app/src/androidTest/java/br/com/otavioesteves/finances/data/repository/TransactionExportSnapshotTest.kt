package br.com.otavioesteves.finances.data.repository

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.dao.TransactionDao
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.utils.ExportFormat
import br.com.otavioesteves.finances.utils.TransactionExporter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TransactionExportSnapshotTest {
    private val period = MonthPeriod(2026, 11)
    private fun database() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
    ).build()

    private suspend fun seed(db: AppDatabase) {
        db.categoryDao().insertCategories(listOf(CategoryEntity(1, "Categoria original", CategoryType.EXPENSE)))
        db.transactionDao().insertTransactions(listOf(
            TransactionEntity(1, "Primeira", 100, 1, LocalDate.of(2026, 11, 1), TransactionType.EXPENSE, null),
            TransactionEntity(2, "Segunda", 200, 1, LocalDate.of(2026, 11, 30), TransactionType.EXPENSE, null),
            TransactionEntity(3, "Terceira", 300, 1, LocalDate.of(2026, 11, 30), TransactionType.EXPENSE, null),
            TransactionEntity(4, "Outro mês", 400, 1, LocalDate.of(2026, 12, 1), TransactionType.EXPENSE, null)
        ))
    }

    private fun replacement() = BackupModel(
        categories = listOf(CategoryExportModel(1, "Categoria restaurada", CategoryType.EXPENSE)),
        transactions = listOf(TransactionExportModel(1, "Restaurada", 999, 1, "2026-11-10", TransactionType.EXPENSE, null))
    )

    @Test fun snapshotIncludesTheRequestedMonthInStableOrderAndSupportsEmptyMonths() = runBlocking {
        val db = database()
        try {
            seed(db)
            val repository = RoomTransactionsRepository(db.transactionDao(), db)
            val snapshot = repository.getExportSnapshot(period)
            assertEquals(listOf(3L, 2L, 1L), snapshot.transactions.map { it.id })
            assertEquals("Categoria original", snapshot.categories.single().name)
            assertTrue(repository.getExportSnapshot(MonthPeriod(2026, 10)).transactions.isEmpty())
        } finally { db.close() }
    }

    @Test fun restoreCannotCommitBetweenReadingExportTransactionsAndCategories() = runBlocking {
        withTimeout(15_000) {
            val db = database()
            val rowsRead = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            try {
                seed(db)
                val dao = db.transactionDao()
                val pausedDao = object : TransactionDao by dao {
                    override suspend fun getTransactionsSnapshot(startDate: String, endDate: String): List<TransactionEntity> {
                        val rows = dao.getTransactionsSnapshot(startDate, endDate)
                        rowsRead.complete(Unit)
                        gate.await()
                        return rows
                    }
                }
                val repository = RoomTransactionsRepository(pausedDao, db)
                val exporting = async { repository.getExportSnapshot(period) }
                rowsRead.await()
                val restoring = async(start = CoroutineStart.UNDISPATCHED) {
                    RoomBackupRepository(db).restoreBackup(replacement())
                }
                // Keep the first read suspended long enough to detect a restore that
                // would otherwise commit before the export reads its category catalog.
                assertNull(withTimeoutOrNull(1_000) { restoring.await(); true })
                gate.complete(Unit)
                val snapshot = exporting.await()
                restoring.await()
                assertEquals(listOf("Terceira", "Segunda", "Primeira"), snapshot.transactions.map { it.description })
                assertEquals("Categoria original", snapshot.categories.single().name)
                for (format in ExportFormat.entries) {
                    val exported = TransactionExporter().export(snapshot.transactions, snapshot.categories.associateBy { it.id }, format)
                    assertTrue(exported, exported.contains("Categoria original"))
                    assertFalse(exported, exported.contains("Categoria restaurada"))
                }
                val fresh = repository.getExportSnapshot(period)
                assertEquals("Restaurada", fresh.transactions.single().description)
                assertEquals("Categoria restaurada", fresh.categories.first { it.id == 1L }.name)
            } finally { gate.complete(Unit); db.close() }
        }
    }

    @Test fun exportQueuedDuringRestoreReadsTheCompleteReplacement() = runBlocking {
        withTimeout(15_000) {
            val db = database()
            val gate = CompletableDeferred<Unit>()
            try {
                seed(db)
                val replaced = CompletableDeferred<Unit>()
                val restoring = async {
                    db.withTransaction {
                        RoomBackupRepository(db).restoreBackup(replacement())
                        replaced.complete(Unit)
                        gate.await()
                    }
                }
                replaced.await()
                val repository = RoomTransactionsRepository(db.transactionDao(), db)
                val exporting = async(start = CoroutineStart.UNDISPATCHED) { repository.getExportSnapshot(period) }
                assertFalse(exporting.isCompleted)
                gate.complete(Unit)
                restoring.await()
                val snapshot = exporting.await()
                assertEquals("Restaurada", snapshot.transactions.single().description)
                assertEquals("Categoria restaurada", snapshot.categories.first { it.id == 1L }.name)
                assertEquals(999L, snapshot.transactions.single().amount.cents)
            } finally { gate.complete(Unit); db.close() }
        }
    }
}
