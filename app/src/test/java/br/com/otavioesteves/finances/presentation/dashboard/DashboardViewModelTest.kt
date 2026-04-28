package br.com.otavioesteves.finances.presentation.dashboard

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import br.com.otavioesteves.finances.domain.usecase.GetMonthlyBalanceUseCase
import br.com.otavioesteves.finances.domain.usecase.GetTransactionsByMonthUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val fakeMonthPeriod = MonthPeriod(year = 2025, month = 12)
    private val fakeDateProvider = object : DateProvider {
        override fun getCurrentMonthPeriod(): MonthPeriod = fakeMonthPeriod
    }

    private class FakeTransactionsRepository : TransactionsRepository {
        override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> = flowOf(emptyList())
        override suspend fun addTransaction(transaction: Transaction) {}
        override suspend fun removeTransaction(transactionId: Long) {}
        override suspend fun updateTransaction(transaction: Transaction) {}
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
        
        val viewModel = DashboardViewModel(
            getMonthlyBalance = getMonthlyBalance,
            getTransactionsByMonth = getTransactionsByMonth,
            dateProvider = fakeDateProvider
        )

        assertEquals(fakeMonthPeriod, viewModel.uiState.value.monthPeriod)
    }
}
