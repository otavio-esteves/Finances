package br.com.otavioesteves.finances.presentation.categories

import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import br.com.otavioesteves.finances.domain.usecase.GetTransactionsByMonthUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryDetailsViewModelTest {
    @Test
    fun selectedMonthAndCategoryDriveDetailsAndLiveUpdates() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val period = MonthPeriod(2026, 2)
            val category = Category(1, "Alimentação", CategoryType.EXPENSE)
            val other = Category(2, "Saúde", CategoryType.EXPENSE)
            val categories = object : CategoriesRepository {
                override fun getCategories() = flowOf(listOf(category, other))
                override fun getCategorySummaries(period: MonthPeriod) = flowOf(emptyList<CategorySummary>())
            }
            val rows = MutableStateFlow(listOf(
                Transaction(1, "Mercado", Money(5000), 1, LocalDate.of(2026, 2, 1), TransactionType.EXPENSE),
                Transaction(2, "Farmácia", Money(9000), 2, LocalDate.of(2026, 2, 1), TransactionType.EXPENSE)
            ))
            var requested: MonthPeriod? = null
            val repository = object : TransactionsRepository {
        override fun hasTransactions(): kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)
                override fun getTransaction(id: Long) = flowOf(null)
                override fun getTransactions(period: MonthPeriod) = rows.also { requested = period }
                override fun getMonthlyBalance(period: MonthPeriod) = flowOf(Money.Zero)
                override suspend fun addTransaction(transaction: Transaction) = Unit
                override suspend fun updateTransaction(transaction: Transaction) = Unit
                override suspend fun removeTransaction(transactionId: Long) = Unit
            }
            val viewModel = CategoryDetailsViewModel(1, period, categories, GetTransactionsByMonthUseCase(repository), dispatcher)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(period, requested)
            assertEquals(period, viewModel.uiState.value.period)
            assertEquals(category, viewModel.uiState.value.category)
            assertEquals(listOf(1L), viewModel.uiState.value.transactions.map { it.id })
            assertEquals(Money(5000), viewModel.uiState.value.total)
            rows.value = rows.value.filterNot { it.categoryId == 1L }
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals(emptyList<Transaction>(), viewModel.uiState.value.transactions)
            assertEquals(Money.Zero, viewModel.uiState.value.total)
        } finally { Dispatchers.resetMain() }
    }
}
