package br.com.otavioesteves.finances.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.DuplicateStatementImportException
import br.com.otavioesteves.finances.domain.model.ImportedStatement
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class RoomStatementImportRepositoryTest {
    @Test
    fun confirmImport_isAtomicAndRejectsDuplicateBatch() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            database.categoryDao().insertCategories(
                listOf(CategoryEntity(id = 1, name = "Alimentação", type = CategoryType.EXPENSE))
            )
            val repository = RoomStatementImportRepository(database)
            val statement = ImportedStatement(0, "extrato.csv", LocalDateTime.of(2026, 1, 15, 10, 0), 2)
            val valid = Transaction(0, "Mercado", Money.fromCents(1000), 1, LocalDate.of(2026, 1, 10), TransactionType.EXPENSE)
            val invalid = valid.copy(description = "Sem categoria", categoryId = 999)

            assertTrue(runCatching {
                repository.confirmImport(statement, listOf(valid, invalid), "fingerprint-1")
            }.isFailure)
            assertTrue(database.transactionDao().getAllTransactions().isEmpty())
            assertTrue(repository.getImports().first().isEmpty())

            repository.confirmImport(statement.copy(transactionCount = 1), listOf(valid), "fingerprint-1")
            assertTrue(runCatching {
                repository.confirmImport(statement.copy(transactionCount = 1), listOf(valid), "fingerprint-1")
            }.exceptionOrNull() is DuplicateStatementImportException)
            assertEquals(1, database.transactionDao().getAllTransactions().size)
            assertEquals(1, repository.getImports().first().size)
        } finally {
            database.close()
        }
    }
}
