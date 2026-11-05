package br.com.otavioesteves.finances.presentation.categories

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.usecase.GetCategorySummariesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Money
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val fakeMonthPeriod = MonthPeriod(year = 2025, month = 12)
    private val fakeDateProvider = object : DateProvider {
        override fun getCurrentMonthPeriod(): MonthPeriod = fakeMonthPeriod
        override fun getCurrentDate(): LocalDate = LocalDate.of(2025, 12, 1)
        override fun getCurrentDateTime(): java.time.LocalDateTime = LocalDate.of(2025, 12, 1).atStartOfDay()
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
            dateProvider = fakeDateProvider,
            categoriesRepository = repository
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

    @Test
    fun `OnMonthChanged switches the displayed month`() = runTest {
        val repository = FakeCategoriesRepository()
        val viewModel = CategoriesViewModel(
            getCategorySummaries = GetCategorySummariesUseCase(repository),
            dateProvider = fakeDateProvider,
            categoriesRepository = repository
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val previousMonth = fakeMonthPeriod.previousMonth()
        viewModel.onEvent(CategoriesEvent.OnMonthChanged(previousMonth))
        testDispatcher.scheduler.advanceUntilIdle()

        val finalState = viewModel.uiState.value
        val finalPeriod = when (finalState) {
            is CategoriesUiState.Success -> finalState.monthPeriod
            is CategoriesUiState.Empty -> finalState.monthPeriod
            is CategoriesUiState.Error -> finalState.monthPeriod
            CategoriesUiState.Loading -> null
        }

        assertEquals(previousMonth, finalPeriod)
    }
    @Test
    fun `creating a category exposes it even in a month without transactions`() = runTest {
        val categories = MutableStateFlow<List<Category>>(emptyList())
        var createCalls = 0
        val repository = object : CategoriesRepository {
            override fun getCategories(): Flow<List<Category>> = categories
            override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> = flowOf(emptyList())
            override suspend fun createCategory(name: String, type: CategoryType): Category {
                createCalls++
                return Category(createCalls.toLong(), name, type).also { categories.value += it }
            }
        }
        val viewModel = CategoriesViewModel(GetCategorySummariesUseCase(repository), fakeDateProvider, repository)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(CategoriesUiState.Empty(fakeMonthPeriod), viewModel.uiState.value)
        viewModel.showCreateCategory()
        viewModel.createCategory("   ", CategoryType.EXPENSE)
        assertEquals(0, createCalls)
        viewModel.createCategory("  Educação  ", CategoryType.EXPENSE)
        viewModel.createCategory("Duplicada", CategoryType.EXPENSE)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, createCalls)
        assertEquals(false, viewModel.creationState.value.isOpen)
        val rows = (viewModel.uiState.value as CategoriesUiState.Success).categories
        assertEquals("Educação", rows.single().category.name)
        assertEquals(Money.Zero, rows.single().totalAmount)
    }

}
