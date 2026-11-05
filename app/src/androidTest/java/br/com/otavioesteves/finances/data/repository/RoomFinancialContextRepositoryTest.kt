package br.com.otavioesteves.finances.data.repository

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RoomFinancialContextRepositoryTest {
    @Test
    fun contextKeepsBalanceTotalsAndCategoriesConsistentDuringReplacement() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            suspend fun replaceDataset(id: Long) = database.withTransaction {
                database.transactionDao().deleteAll()
                database.categoryDao().deleteAll()
                database.categoryDao().insertCategories(listOf(
                    CategoryEntity(id, "Categoria $id", CategoryType.EXPENSE),
                    CategoryEntity(1000, "Sem movimento", CategoryType.INCOME)
                ))
                database.transactionDao().insertTransaction(TransactionEntity(
                    id = 1, description = "Compra $id", amountCents = id * 100,
                    categoryId = id, date = LocalDate.of(2026, 2, 1),
                    type = TransactionType.EXPENSE, notes = null
                ))
            }
            replaceDataset(1)
            val repository = RoomFinancialContextRepository(database)
            coroutineScope {
                val writer = async(Dispatchers.IO) { repeat(30) { replaceDataset(it.toLong() + 2) } }
                val reader = async(Dispatchers.IO) {
                    repeat(30) {
                        val context = repository.getContext(MonthPeriod(2026, 2))
                        val summary = context.categorySummaries.single()
                        assertEquals(-summary.totalAmount.cents, context.monthlyBalance.cents)
                        assertEquals(summary.category, context.categories.single { it.id == summary.category.id })
                        assertEquals(2, context.categories.size)
                    }
                }
                writer.await()
                reader.await()
            }
            val empty = repository.getContext(MonthPeriod(2026, 3))
            assertEquals(0L, empty.monthlyBalance.cents)
            assertEquals(emptyList<Any>(), empty.categorySummaries)
            assertEquals(2, empty.categories.size)
        } finally {
            database.close()
        }
    }
}
