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
import kotlinx.coroutines.flow.MutableStateFlow
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
        val hasAny = MutableStateFlow(false)
        override fun hasTransactions(): Flow<Boolean> = hasAny
        override fun getTransaction(id: Long): Flow<Transaction?> = flowOf(null)
        var requestedRecentLimit: Int? = null
        override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getRecentTransactions(period: MonthPeriod, limit: Int): Flow<List<Transaction>> {
            requestedRecentLimit = limit
            return flowOf(emptyList())
        }
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
    fun `first use depends on the entire database and reacts to adding or removing transactions`() = runTest {
        val repository = FakeTransactionsRepository()
        val viewModel = DashboardViewModel(
            GetMonthlyBalanceUseCase(repository), GetTransactionsByMonthUseCase(repository),
            GetCategorySummariesUseCase(FakeCategoriesRepository()), FakeCategoriesRepository(), fakeDateProvider
        )
        assertEquals(null, viewModel.uiState.value.hasAnyTransactions)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.hasAnyTransactions)
        repository.hasAny.value = true
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.hasAnyTransactions)
        viewModel.onMonthSelected(MonthPeriod(2020, 1))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.hasAnyTransactions)
        repository.hasAny.value = false
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.hasAnyTransactions)
    }

    @Test
    fun `monthly components include income and expenses and leave transfers out`() {
        val state = DashboardUiState(fakeMonthPeriod, Money(2000), categorySummaries = listOf(
            CategorySummary(Category(1, "Salário", br.com.otavioesteves.finances.domain.model.CategoryType.INCOME), Money(3000)),
            CategorySummary(Category(2, "Mercado", br.com.otavioesteves.finances.domain.model.CategoryType.EXPENSE), Money(700)),
            CategorySummary(Category(3, "Transporte", br.com.otavioesteves.finances.domain.model.CategoryType.EXPENSE), Money(300)),
            CategorySummary(Category(4, "Transferência", br.com.otavioesteves.finances.domain.model.CategoryType.TRANSFER), Money(5000))
        ))
        assertEquals(Money(3000), state.monthlyIncome)
        assertEquals(Money(1000), state.monthlyExpenses)
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

    @Test
    fun `dashboard requests only six recent transactions`() = runTest {
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
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(6, repository.requestedRecentLimit)
    }
}
