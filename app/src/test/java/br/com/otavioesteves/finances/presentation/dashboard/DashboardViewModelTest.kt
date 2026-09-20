package br.com.otavioesteves.finances.presentation.dashboard

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import br.com.otavioesteves.finances.domain.usecase.GetCategorySummariesUseCase
import br.com.otavioesteves.finances.domain.usecase.GetMonthlyBalanceUseCase
import br.com.otavioesteves.finances.domain.usecase.GetTransactionsByMonthUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val fakeMonthPeriod = MonthPeriod(year = 2025, month = 12)
    private val fakeDateProvider = object : DateProvider {
        override fun getCurrentMonthPeriod(): MonthPeriod = fakeMonthPeriod
        override fun getCurrentDate(): LocalDate = LocalDate.of(2025, 12, 1)
        override fun getCurrentDateTime(): java.time.LocalDateTime = LocalDate.of(2025, 12, 1).atStartOfDay()
    }

    private class FakeTransactionsRepository : TransactionsRepository {
        override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getMonthlyBalance(period: MonthPeriod): Flow<Money> = flowOf(Money.Zero)
        override suspend fun addTransaction(transaction: Transaction) {}
        override suspend fun removeTransaction(transactionId: Long) {}
        override suspend fun updateTransaction(transaction: Transaction) {}
    }

    private class FakeCategoriesRepository : CategoriesRepository {
        override fun getCategories(): Flow<List<Category>> = flowOf(emptyList())
        override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> =
            flowOf(emptyList())
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `viewModel starts with current month from dateProvider`() = runTest {
        val repository = FakeTransactionsRepository()
        val getMonthlyBalance = GetMonthlyBalanceUseCase(repository)
        val getTransactionsByMonth = GetTransactionsByMonthUseCase(repository)
        val getCategorySummaries = GetCategorySummariesUseCase(FakeCategoriesRepository())

        val viewModel = DashboardViewModel(
            getMonthlyBalance = getMonthlyBalance,
            getTransactionsByMonth = getTransactionsByMonth,
            getCategorySummaries = getCategorySummaries,
            categoriesRepository = FakeCategoriesRepository(),
            dateProvider = fakeDateProvider
        )

        assertEquals(fakeMonthPeriod, viewModel.uiState.value.monthPeriod)
    }

    @Test
    fun `onMonthSelected switches the displayed month`() = runTest {
        val repository = FakeTransactionsRepository()
        val viewModel = DashboardViewModel(
            getMonthlyBalance = GetMonthlyBalanceUseCase(repository),
            getTransactionsByMonth = GetTransactionsByMonthUseCase(repository),
            getCategorySummaries = GetCategorySummariesUseCase(FakeCategoriesRepository()),
            categoriesRepository = FakeCategoriesRepository(),
            dateProvider = fakeDateProvider
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        val previousMonth = fakeMonthPeriod.previousMonth()
        viewModel.onMonthSelected(previousMonth)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(previousMonth, viewModel.uiState.value.monthPeriod)
    }
}
