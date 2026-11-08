package br.com.otavioesteves.finances.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
import br.com.otavioesteves.finances.domain.model.AnnualTransactionSummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.summarizeAnnualTransactions
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
    fun hasTransactionsIncludesOtherYearsAndReturnsFalseAfterDeletingTheLastEntry() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            val repository = RoomTransactionsRepository(database.transactionDao())
            assertEquals(false, repository.hasTransactions().first())
            database.categoryDao().insertCategories(listOf(CategoryEntity(id = 1, name = "Mercado", type = CategoryType.EXPENSE)))
            database.transactionDao().insertTransaction(TransactionEntity(
                id = 1, description = "Compra antiga", amountCents = 100, categoryId = 1,
                date = LocalDate.of(2000, 1, 1), type = TransactionType.EXPENSE, notes = null
            ))
            assertEquals(true, repository.hasTransactions().first())
            database.transactionDao().deleteAll()
            assertEquals(false, repository.hasTransactions().first())
        } finally { database.close() }
    }

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
    @Test
    fun annualSummary_matchesTransactionAggregationWithoutLoadingAnnualRows() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            val repository = RoomTransactionsRepository(database.transactionDao())
            assertEquals(AnnualTransactionSummary(2026), repository.getAnnualSummary(2026, "tracked").first())
            database.categoryDao().insertCategories(listOf(
                CategoryEntity(1, "Alimentação", CategoryType.EXPENSE),
                CategoryEntity(2, "Transporte", CategoryType.EXPENSE),
                CategoryEntity(3, "Salário", CategoryType.INCOME),
                CategoryEntity(4, "Transferência", CategoryType.TRANSFER)
            ))
            fun row(id: Long, category: Long, year: Int, month: Int, cents: Long, type: TransactionType, note: String? = null) =
                TransactionEntity(id, "Compra $id", cents, category, LocalDate.of(year, month, 1), type, note)
            database.transactionDao().insertTransactions(listOf(
                row(1, 1, 2026, 1, 100, TransactionType.EXPENSE, "tracked"),
                row(2, 1, 2026, 1, 250, TransactionType.EXPENSE),
                row(3, 2, 2026, 1, 700, TransactionType.EXPENSE),
                row(4, 1, 2026, 12, 0, TransactionType.EXPENSE),
                row(5, 3, 2026, 2, 900, TransactionType.INCOME, "tracked"),
                row(6, 4, 2026, 3, 1000, TransactionType.TRANSFER, "tracked"),
                row(7, 1, 2025, 1, 800, TransactionType.EXPENSE, "tracked"),
                row(8, 1, 2026, 2, 200, TransactionType.EXPENSE, "tracked suffix")
            ))
            val transactions = repository.getTransactionsForYear(2026).first()
            assertEquals(
                summarizeAnnualTransactions(transactions, 2026, "tracked"),
                repository.getAnnualSummary(2026, "tracked").first()
            )
            assertEquals(0, repository.getAnnualSummary(2026).first().trackedNoteCount)
            database.transactionDao().deleteTransactionById(3)
            assertEquals(350L, repository.getAnnualSummary(2026, "tracked").first().monthlyExpenses[0])
        } finally {
            database.close()
        }
    }

}
