package br.com.otavioesteves.finances.presentation.period

import androidx.lifecycle.SavedStateHandle
import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import br.com.otavioesteves.finances.domain.state.SelectedMonthState
import br.com.otavioesteves.finances.domain.usecase.*
import br.com.otavioesteves.finances.presentation.categories.CategoriesEvent
import br.com.otavioesteves.finances.presentation.categories.CategoriesUiState
import br.com.otavioesteves.finances.presentation.categories.CategoriesViewModel
import br.com.otavioesteves.finances.presentation.categories.CategoryDetailsViewModel
import br.com.otavioesteves.finances.presentation.charts.ChartsViewModel
import br.com.otavioesteves.finances.presentation.dashboard.DashboardViewModel
import br.com.otavioesteves.finances.presentation.transactions.TransactionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SharedMonthTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = MonthPeriod(2026, 11)
    private val dates = object : DateProvider {
        override fun getCurrentMonthPeriod() = now
        override fun getCurrentDate() = LocalDate.of(2026, 11, 4)
        override fun getCurrentDateTime() = getCurrentDate().atStartOfDay()
    }
    private val category = Category(1, "Mercado", CategoryType.EXPENSE)
    private val categories = object : CategoriesRepository {
        override fun getCategories() = flowOf(listOf(category))
        override fun getCategorySummaries(period: MonthPeriod) =
            flowOf(listOf(CategorySummary(category, Money.fromCents(period.month.toLong()))))
    }
    private val transactions = object : TransactionsRepository {
        override fun hasTransactions(): kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)
        override fun getTransaction(id: Long) = flowOf<Transaction?>(null)
        override fun getTransactions(period: MonthPeriod) = flowOf(listOf(Transaction(
            id = period.month.toLong(), description = period.toYearMonth().toString(),
            amount = Money.fromCents(period.month.toLong()), categoryId = 1,
            date = period.toYearMonth().atDay(1), type = TransactionType.EXPENSE
        )))
        override fun getMonthlyBalance(period: MonthPeriod) = flowOf(Money.fromCents(period.month.toLong()))
        override suspend fun addTransaction(transaction: Transaction) = Unit
        override suspend fun removeTransaction(transactionId: Long) = Unit
        override suspend fun updateTransaction(transaction: Transaction) = Unit
    }
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `changing month from any financial view updates all queries including a new history instance`() = runTest(dispatcher) {
        val persisted = mutableListOf<MonthPeriod>()
        val selected = SelectedMonthState(now, persisted::add)
        val monthlyTransactions = GetTransactionsByMonthUseCase(transactions)
        val summaries = GetCategorySummariesUseCase(categories)
        val dashboard = DashboardViewModel(GetMonthlyBalanceUseCase(transactions), monthlyTransactions,
            summaries, categories, dates, selected)
        val charts = ChartsViewModel(summaries, transactions, categories, dates, dispatcher, selected)
        val categoryList = CategoriesViewModel(summaries, dates, categories, selected)
        val history = TransactionsViewModel(monthlyTransactions, DeleteTransactionUseCase(transactions),
            categories, dates, dispatcher, monthSelection = selected)
        val details = CategoryDetailsViewModel(1, now, categories, monthlyTransactions, dispatcher, selected)
        listOf(dashboard.uiState, charts.uiState, categoryList.uiState, history.uiState, details.uiState).forEach { state ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect() }
        }
        fun verify(period: MonthPeriod) {
            testScheduler.runCurrent()
            assertEquals(period, dashboard.uiState.value.monthPeriod)
            assertEquals(Money.fromCents(period.month.toLong()), dashboard.uiState.value.monthlyBalance)
            assertEquals(period, charts.uiState.value.monthPeriod)
            assertEquals(period, (categoryList.uiState.value as CategoriesUiState.Success).monthPeriod)
            assertEquals(period, history.uiState.value.monthPeriod)
            assertEquals(period.toYearMonth().toString(), history.uiState.value.transactions.single().transaction.description)
            assertEquals(period, details.uiState.value.period)
            assertEquals(Money.fromCents(period.month.toLong()), details.uiState.value.total)
        }
        verify(now)
        dashboard.onMonthSelected(now.previousMonth())
        verify(now.previousMonth())
        charts.onMonthSelected(MonthPeriod(2025, 12))
        verify(MonthPeriod(2025, 12))
        categoryList.onEvent(CategoriesEvent.OnMonthChanged(MonthPeriod(2026, 1)))
        verify(MonthPeriod(2026, 1))
        history.onMonthSelected(now)
        verify(now)
        val restoredHistory = TransactionsViewModel(monthlyTransactions, DeleteTransactionUseCase(transactions),
            categories, dates, dispatcher, SavedStateHandle(mapOf("selected_month" to "2020-01")), selected)
        assertEquals(now, restoredHistory.selectedMonthPeriod.value)
        assertEquals(listOf(now.previousMonth(), MonthPeriod(2025, 12), MonthPeriod(2026, 1), now), persisted)
    }

    @Test fun `inactive page resumes querying shared month rather than cached month`() = runTest(dispatcher) {
        val selected = SelectedMonthState(now)
        val dashboard = DashboardViewModel(GetMonthlyBalanceUseCase(transactions), GetTransactionsByMonthUseCase(transactions),
            GetCategorySummariesUseCase(categories), categories, dates, selected)
        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { dashboard.uiState.collect() }
        runCurrent()
        first.cancel()
        advanceTimeBy(5_001)
        selected.select(now.previousMonth())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { dashboard.uiState.collect() }
        runCurrent()
        assertEquals(now.previousMonth(), dashboard.uiState.value.monthPeriod)
        assertEquals(Money.fromCents(10), dashboard.uiState.value.monthlyBalance)
    }
}
