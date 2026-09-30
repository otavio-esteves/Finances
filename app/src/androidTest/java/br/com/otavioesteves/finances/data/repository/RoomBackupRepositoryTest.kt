package br.com.otavioesteves.finances.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.ChatMessageEntity
import br.com.otavioesteves.finances.data.local.entity.StatementImportEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.ChatRole
import br.com.otavioesteves.finances.domain.model.TransactionOrigin
import br.com.otavioesteves.finances.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class RoomBackupRepositoryTest {
    @Test
    fun backupAndRestore_preservesImportedTransactionsAndRelatedHistory() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            val importedAt = LocalDateTime.of(2026, 1, 15, 10, 0)
            database.categoryDao().insertCategories(listOf(CategoryEntity(1, "Alimentação", CategoryType.EXPENSE)))
            database.transactionDao().insertTransaction(
                TransactionEntity(1, "Mercado", 5_000, 1, LocalDate.of(2026, 1, 10), TransactionType.EXPENSE, null, TransactionOrigin.IMPORTED)
            )
            database.statementImportDao().insertImport(StatementImportEntity(1, "extrato.csv", importedAt, 1, "fingerprint"))
            database.chatMessageDao().insertMessage(ChatMessageEntity(1, ChatRole.USER, "Quanto gastei?", importedAt))

            val repository = RoomBackupRepository(database)
            val backup = repository.createBackup()
            database.transactionDao().insertTransaction(
                TransactionEntity(2, "Temporária", 100, 1, LocalDate.of(2026, 1, 11), TransactionType.EXPENSE, null)
            )
            repository.restoreBackup(backup)

            assertEquals(2, backup.version)
            assertEquals(TransactionOrigin.IMPORTED, database.transactionDao().getAllTransactions().single().origin)
            assertEquals("fingerprint", database.statementImportDao().getAllImportsForBackup().single().fingerprint)
            assertEquals("Quanto gastei?", database.chatMessageDao().getAllMessagesForBackup().single().content)
        } finally {
            database.close()
        }
    }
}
