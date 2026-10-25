package br.com.otavioesteves.finances.presentation.charts

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.AnnualTransactionSummary
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import br.com.otavioesteves.finances.domain.usecase.GetCategorySummariesUseCase
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import androidx.lifecycle.ViewModelStore
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChartsViewModelTest {
    @Test
    fun `annual totals include only expenses in selected year and category`() {
        val transactions = listOf(
            transaction(1, 2026, 1, 3, 100, TransactionType.EXPENSE),
            transaction(2, 2026, 1, 8, 250, TransactionType.EXPENSE),
            transaction(1, 2026, 2, 3, 500, TransactionType.EXPENSE),
            transaction(1, 2026, 1, 10, 900, TransactionType.INCOME),
            transaction(1, 2025, 1, 3, 700, TransactionType.EXPENSE)
        )

        assertEquals(350L, annualExpenseTotals(transactions, 2026)[0])
        assertEquals(100L, annualExpenseTotals(transactions, 2026, categoryId = 1)[0])
        assertEquals(500L, annualExpenseTotals(transactions, 2026, categoryId = 1)[1])
        assertEquals(0L, annualExpenseTotals(transactions, 2026, categoryId = 1)[2])
    }

    @Test
    fun `month and category changes reuse annual subscription and select preaggregated data`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val repository = AnnualRepository()
            val categories = ChartCategories()
            val viewModel = ChartsViewModel(GetCategorySummariesUseCase(categories), repository, categories, dates, dispatcher)
            store.put("charts", viewModel)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
            testScheduler.runCurrent()
            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals(listOf(2026), repository.requestedYears)
            assertEquals(300L, viewModel.uiState.value.annualExpenses[0])
            assertEquals(100L, viewModel.uiState.value.categoryAnnualExpenses[0])

            viewModel.onMonthSelected(MonthPeriod(2026, 2))
            testScheduler.runCurrent()
            viewModel.onCategorySelected(2)
            testScheduler.runCurrent()

            assertEquals(listOf(2026), repository.requestedYears)
            assertEquals(2, categories.requestedMonths.size)
            assertEquals(200L, viewModel.uiState.value.categoryAnnualExpenses[0])
            assertEquals(MonthPeriod(2026, 2), viewModel.uiState.value.monthPeriod)

            repository.sources.getValue(2026).value = AnnualTransactionSummary(2026, List(12) { 500 }, mapOf(2L to List(12) { 400 }), 8)
            testScheduler.runCurrent()
            assertEquals(500L, viewModel.uiState.value.annualExpenses[0])
            assertEquals(400L, viewModel.uiState.value.categoryAnnualExpenses[0])
            assertEquals(8, viewModel.uiState.value.exampleCount)

            viewModel.onMonthSelected(MonthPeriod(2027, 1))
            testScheduler.runCurrent()
            assertEquals(listOf(2026, 2027), repository.requestedYears)
            assertEquals(MonthPeriod(2027, 1), viewModel.uiState.value.monthPeriod)
            assertEquals(300L, viewModel.uiState.value.annualExpenses[0])
        } finally {
            store.clear()
            testScheduler.runCurrent()
            Dispatchers.resetMain()
        }
    }

    private val dates = object : DateProvider {
        override fun getCurrentMonthPeriod() = MonthPeriod(2026, 1)
        override fun getCurrentDate() = LocalDate.of(2026, 1, 1)
        override fun getCurrentDateTime() = getCurrentDate().atStartOfDay()
    }

    private class AnnualRepository : TransactionsRepository {
        val requestedYears = mutableListOf<Int>()
        val sources = mutableMapOf<Int, MutableStateFlow<AnnualTransactionSummary>>()
        override fun getAnnualSummary(year: Int, trackedNote: String?): Flow<AnnualTransactionSummary> {
            requestedYears += year
            return sources.getOrPut(year) {
                MutableStateFlow(AnnualTransactionSummary(year, List(12) { 300 }, mapOf(1L to List(12) { 100 }, 2L to List(12) { 200 })))
            }
        }
        override fun getTransactionsForYear(year: Int): Flow<List<Transaction>> = error("Charts must request compact aggregates")
        override fun getTransaction(id: Long): Flow<Transaction?> = flowOf(null)
        override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getMonthlyBalance(period: MonthPeriod): Flow<Money> = flowOf(Money.Zero)
        override suspend fun addTransaction(transaction: Transaction) = Unit
        override suspend fun removeTransaction(transactionId: Long) = Unit
        override suspend fun updateTransaction(transaction: Transaction) = Unit
    }

    private class ChartCategories : CategoriesRepository {
        val requestedMonths = mutableListOf<MonthPeriod>()
        override fun getCategories() = flowOf(listOf(Category(1, "Alimentação", CategoryType.EXPENSE), Category(2, "Transporte", CategoryType.EXPENSE)))
        override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> {
            requestedMonths += period
            return flowOf(emptyList())
        }
    }

    private fun transaction(categoryId: Long, year: Int, month: Int, day: Int, cents: Long, type: TransactionType) =
        Transaction(
            id = 0,
            description = "Teste",
            amount = Money.fromCents(cents),
            categoryId = categoryId,
            date = LocalDate.of(year, month, day),
            type = type
        )
}
