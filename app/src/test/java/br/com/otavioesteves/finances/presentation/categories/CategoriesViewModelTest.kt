package br.com.otavioesteves.finances.presentation.categories

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.usecase.GetCategorySummariesUseCase
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
class CategoriesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val fakeMonthPeriod = MonthPeriod(year = 2025, month = 12)
    private val fakeDateProvider = object : DateProvider {
        override fun getCurrentMonthPeriod(): MonthPeriod = fakeMonthPeriod
    }

    private class FakeCategoriesRepository : CategoriesRepository {
        override fun getCategories(): Flow<List<Category>> = flowOf(emptyList())
        override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> = flowOf(emptyList())
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
        val repository = FakeCategoriesRepository()
        val getCategorySummaries = GetCategorySummariesUseCase(repository)
        
        val viewModel = CategoriesViewModel(
            getCategorySummaries = getCategorySummaries,
            dateProvider = fakeDateProvider
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val finalState = viewModel.uiState.value
        val finalPeriod = when (finalState) {
            is CategoriesUiState.Success -> finalState.monthPeriod
            is CategoriesUiState.Empty -> finalState.monthPeriod
            is CategoriesUiState.Error -> finalState.monthPeriod
            CategoriesUiState.Loading -> null
        }

        assertEquals(fakeMonthPeriod, finalPeriod)
    }
}
