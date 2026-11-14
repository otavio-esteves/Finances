package br.com.otavioesteves.finances.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.StatementImportEntity
import br.com.otavioesteves.finances.data.local.mapper.toDomain
import br.com.otavioesteves.finances.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class ImportLifecycleTest {
    private fun database() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
    ).build()
    private val batch = ImportedStatement(0, "fatura.csv", LocalDateTime.of(2026, 11, 1, 9, 0), 1, "Nubank · cartão")
    private val entry = Transaction(0, "Café", Money(1500), 1, LocalDate.of(2026, 11, 1), TransactionType.EXPENSE,
        origin = TransactionOrigin.IMPORTED)

    @Test fun undoAfterEditAndBackupRemovesOnlyOwnedTransactionsAndAllowsReimport() = runBlocking {
        val db = database()
        try {
            db.categoryDao().insertCategories(listOf(CategoryEntity(1, "Outros", CategoryType.EXPENSE), CategoryEntity(2, "Alimentação", CategoryType.EXPENSE)))
            val imports = RoomStatementImportRepository(db)
            val transactions = RoomTransactionsRepository(db.transactionDao(), db)
            imports.confirmReviewedImport(batch, listOf(entry), "first", emptyList(), listOf(CategoryMemory("cafe", TransactionType.EXPENSE, 1)))
            val first = imports.getImports().first().single()
            val imported = db.transactionDao().getAllTransactions().single().toDomain()
            assertEquals(first.id, imported.importId)
            assertEquals(batch.sourceLabel, imported.sourceLabel)
            transactions.updateTransaction(imported.copy(categoryId = 2, description = "Café corrigido"))
            assertEquals(2L, db.categoryMemoryDao().getAll().single().categoryId)
            transactions.addTransaction(entry.copy(origin = TransactionOrigin.MANUAL, description = "Manual"))
            imports.confirmImport(batch.copy(fileName = "outro.csv"), listOf(entry.copy(description = "Outro lote")), "second")
            val backupRepository = RoomBackupRepository(db)
            val backup = backupRepository.createBackup()
            backupRepository.restoreBackup(backup)
            assertEquals(1, db.categoryMemoryDao().getAll().size)
            assertTrue(imports.getImports().first().first { it.id == first.id }.canUndo)
            imports.undoImport(first.id)
            assertEquals(setOf("Manual", "Outro lote"), db.transactionDao().getAllTransactions().map { it.description }.toSet())
            assertEquals(1, db.categoryMemoryDao().getAll().size)
            imports.confirmImport(batch, listOf(entry), "first")
            assertEquals(3, db.transactionDao().getAllTransactions().size)
        } finally { db.close() }
    }

    @Test fun failedImportDoesNotLearnAndLegacyUndoIsRejected() = runBlocking {
        val db = database()
        try {
            db.categoryDao().insertCategories(listOf(CategoryEntity(1, "Outros", CategoryType.EXPENSE)))
            val imports = RoomStatementImportRepository(db)
            assertTrue(runCatching {
                imports.confirmReviewedImport(batch, listOf(entry.copy(categoryId = 999)), "bad", emptyList(),
                    listOf(CategoryMemory("cafe", TransactionType.EXPENSE, 1)))
            }.isFailure)
            assertTrue(db.categoryMemoryDao().getAll().isEmpty())
            assertTrue(imports.getImports().first().isEmpty())
            val legacyId = db.statementImportDao().insertImport(StatementImportEntity(fileName = "antigo.csv",
                importedAt = batch.importedAt, transactionCount = 1))
            assertTrue(runCatching { imports.undoImport(legacyId) }.isFailure)
            assertEquals(1, imports.getImports().first().size)
        } finally { db.close() }
    }

    @Test fun invalidBackupImportLinkLeavesCurrentDataUntouched() = runBlocking {
        val db = database()
        try {
            db.categoryDao().insertCategories(listOf(CategoryEntity(1, "Outros", CategoryType.EXPENSE)))
            RoomStatementImportRepository(db).confirmImport(batch, listOf(entry), "good")
            val repository = RoomBackupRepository(db)
            val backup = repository.createBackup()
            assertTrue(runCatching { repository.restoreBackup(backup.copy(transactions = backup.transactions.map { it.copy(importId = 999) })) }.isFailure)
            assertEquals(1, db.transactionDao().getAllTransactions().size)
        } finally { db.close() }
    }
}
