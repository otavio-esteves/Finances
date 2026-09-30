package br.com.otavioesteves.finances.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RoomTransactionsRepositoryTest {
    @Test
    fun getTransaction_findsEntryOutsideCurrentMonth() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            database.categoryDao().insertCategories(
                listOf(CategoryEntity(id = 1, name = "Alimentação", type = CategoryType.EXPENSE))
            )
            database.transactionDao().insertTransaction(
                TransactionEntity(
                    id = 42, description = "Compra antiga", amountCents = 2500, categoryId = 1,
                    date = LocalDate.of(2025, 8, 3), type = TransactionType.EXPENSE, notes = null
                )
            )

            assertEquals("Compra antiga", RoomTransactionsRepository(database.transactionDao())
                .getTransaction(42).first()?.description)
        } finally {
            database.close()
        }
    }

    @Test
    fun recentTransactions_returnsOnlyLatestSixInStableOrder() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            database.categoryDao().insertCategories(
                listOf(CategoryEntity(id = 1, name = "Alimentação", type = CategoryType.EXPENSE))
            )
            database.transactionDao().insertTransactions(
                (1L..8L).map { id ->
                    TransactionEntity(
                        id = id,
                        description = "Compra $id",
                        amountCents = 100,
                        categoryId = 1,
                        date = LocalDate.of(2026, 1, if (id == 8L) 11 else 10),
                        type = TransactionType.EXPENSE,
                        notes = null
                    )
                } + TransactionEntity(
                    id = 9,
                    description = "Outro mês",
                    amountCents = 100,
                    categoryId = 1,
                    date = LocalDate.of(2026, 2, 1),
                    type = TransactionType.EXPENSE,
                    notes = null
                )
            )

            val result = RoomTransactionsRepository(database.transactionDao())
                .getRecentTransactions(MonthPeriod(2026, 1), 6).first()

            assertEquals(listOf(8L, 7L, 6L, 5L, 4L, 3L), result.map { it.id })
        } finally {
            database.close()
        }
    }
}
