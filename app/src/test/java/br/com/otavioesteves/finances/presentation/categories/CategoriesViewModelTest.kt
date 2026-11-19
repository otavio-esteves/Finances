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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CompletableDeferred
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.state.SelectedMonthState
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
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
    private val viewModels = mutableListOf<CategoriesViewModel>()

    private fun viewModel(repository: CategoriesRepository, selection: SelectedMonthState = SelectedMonthState(fakeMonthPeriod)) =
        CategoriesViewModel(GetCategorySummariesUseCase(repository), fakeDateProvider, repository, selection)
            .also { viewModels += it }

    private fun TestScope.observe(viewModel: CategoriesViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) { viewModel.uiState.collect {} }
    
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
        viewModels.forEach { it.viewModelScope.cancel() }
        testDispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun `viewModel starts with current month from dateProvider`() = runTest {
        val repository = FakeCategoriesRepository()
        val viewModel = viewModel(repository)
        observe(viewModel)

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
        val viewModel = viewModel(repository)
        observe(viewModel)
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
        val viewModel = viewModel(repository)
        observe(viewModel)
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

    private class TrackingRepository : CategoriesRepository {
        val category = Category(1, "Alimentação", CategoryType.EXPENSE)
        val catalog = MutableStateFlow(listOf(category))
        val totals = MutableStateFlow(listOf(CategorySummary(category, Money(100))))
        val requestedPeriods = mutableListOf<MonthPeriod>()
        var activeSummaries = 0
        var activeCatalogs = 0
        var fail = false
        var gate: CompletableDeferred<Unit>? = null

        override fun getCategories(): Flow<List<Category>> = flow {
            activeCatalogs++
            try { emitAll(catalog) } finally { activeCatalogs-- }
        }

        override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> = flow {
            requestedPeriods += period
            activeSummaries++
            try {
                gate?.await()
                if (fail) error("Falha de leitura")
                emitAll(totals)
            } finally { activeSummaries-- }
        }
    }

    @Test fun hiddenPageDoesNotStartQueriesOnCreationMonthChangesOrRetry() = runTest {
        val repository = TrackingRepository()
        val selection = SelectedMonthState(fakeMonthPeriod)
        val vm = viewModel(repository, selection)
        runCurrent()
        advanceTimeBy(10_000)
        selection.select(fakeMonthPeriod.previousMonth())
        vm.onEvent(CategoriesEvent.OnRetryClicked)
        runCurrent()
        assertEquals(emptyList<MonthPeriod>(), repository.requestedPeriods)
        assertEquals(0, repository.activeSummaries)
        assertEquals(0, repository.activeCatalogs)
    }

    @Test fun lastUiSubscriberStopsBothQueriesAndReturnReadsTheLatestMonthAndData() = runTest {
        val repository = TrackingRepository()
        val selection = SelectedMonthState(fakeMonthPeriod)
        val vm = viewModel(repository, selection)
        val first = observe(vm)
        val second = observe(vm)
        runCurrent()
        assertEquals(listOf(fakeMonthPeriod), repository.requestedPeriods)
        assertEquals(1, repository.activeSummaries)
        assertEquals(1, repository.activeCatalogs)
        first.cancel()
        runCurrent()
        assertEquals(1, repository.activeSummaries)
        second.cancel()
        runCurrent()
        assertEquals(0, repository.activeSummaries)
        assertEquals(0, repository.activeCatalogs)

        val next = fakeMonthPeriod.previousMonth()
        selection.select(next)
        repository.totals.value = listOf(CategorySummary(repository.category, Money(250)))
        repository.catalog.value = listOf(repository.category.copy(name = "Mercado"))
        vm.onEvent(CategoriesEvent.OnRetryClicked)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(listOf(fakeMonthPeriod), repository.requestedPeriods)

        val resumed = observe(vm)
        runCurrent()
        val state = vm.uiState.value as CategoriesUiState.Success
        assertEquals(next, state.monthPeriod)
        assertEquals(Money(250), state.categories.single().totalAmount)
        assertEquals("Mercado", state.categories.single().category.name)
        assertEquals(listOf(fakeMonthPeriod, next), repository.requestedPeriods)
        resumed.cancel()
        runCurrent()
        assertEquals(0, repository.activeSummaries)
        assertEquals(0, repository.activeCatalogs)
    }

    @Test fun changingTheMonthCancelsThePreviousQueryWhileThePageIsVisible() = runTest {
        val repository = TrackingRepository()
        val vm = viewModel(repository)
        observe(vm)
        runCurrent()
        val previous = fakeMonthPeriod.previousMonth()
        vm.onEvent(CategoriesEvent.OnMonthChanged(previous))
        runCurrent()
        assertEquals(listOf(fakeMonthPeriod, previous), repository.requestedPeriods)
        assertEquals(1, repository.activeSummaries)
        assertEquals(1, repository.activeCatalogs)
        assertEquals(previous, (vm.uiState.value as CategoriesUiState.Success).monthPeriod)
    }

    @Test fun retryRecoversFromAReadFailureWithoutRecreatingTheViewModel() = runTest {
        val repository = TrackingRepository().apply { fail = true }
        val vm = viewModel(repository)
        observe(vm)
        runCurrent()
        assertEquals("Falha de leitura", (vm.uiState.value as CategoriesUiState.Error).message)
        repository.fail = false
        vm.onEvent(CategoriesEvent.OnRetryClicked)
        runCurrent()
        assertEquals(Money(100), (vm.uiState.value as CategoriesUiState.Success).categories.single().totalAmount)
        assertEquals(listOf(fakeMonthPeriod, fakeMonthPeriod), repository.requestedPeriods)
    }

    @Test fun returningToTheSameMonthKeepsCachedRowsUntilTheRefreshCompletes() = runTest {
        val repository = TrackingRepository()
        val vm = viewModel(repository)
        val initial = observe(vm)
        runCurrent()
        val cached = vm.uiState.value as CategoriesUiState.Success
        initial.cancel()
        runCurrent()
        repository.gate = CompletableDeferred()
        repository.totals.value = listOf(CategorySummary(repository.category, Money(300)))
        observe(vm)
        runCurrent()
        assertEquals(cached, vm.uiState.value)
        repository.gate!!.complete(Unit)
        runCurrent()
        assertEquals(Money(300), (vm.uiState.value as CategoriesUiState.Success).categories.single().totalAmount)
    }

}
