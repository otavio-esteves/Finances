package br.com.otavioesteves.finances.ui.screens.home

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.state.SelectedMonthState
import br.com.otavioesteves.finances.domain.usecase.GetCategorySummariesUseCase
import br.com.otavioesteves.finances.presentation.categories.CategoriesViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageLifecycleOwnerTest {
    @Test
    fun categoriesReleaseFinancialQueriesWhenThePageIsHiddenAndResumeWithTheCurrentMonth() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val month = MonthPeriod(2026, 11)
        val selection = SelectedMonthState(month)
        val activeQueries = AtomicInteger()
        val requestedPeriods = CopyOnWriteArrayList<MonthPeriod>()
        val repository = object : CategoriesRepository {
            override fun getCategories(): Flow<List<Category>> = flowOf(emptyList())
            override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> = flow {
                requestedPeriods += period
                activeQueries.incrementAndGet()
                try { emitAll(MutableStateFlow(emptyList<CategorySummary>())) }
                finally { activeQueries.decrementAndGet() }
            }
        }
        val dates = object : DateProvider {
            override fun getCurrentMonthPeriod() = month
            override fun getCurrentDate() = LocalDate.of(2026, 11, 18)
            override fun getCurrentDateTime() = getCurrentDate().atStartOfDay()
        }
        val parent = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var page: PageLifecycleOwner
        lateinit var vm: CategoriesViewModel
        fun awaitQueries(expected: Int) {
            val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
            while (activeQueries.get() != expected && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(10)
            assertEquals(expected, activeQueries.get())
        }
        try {
            instrumentation.runOnMainSync {
                parent.registry.currentState = Lifecycle.State.RESUMED
                page = PageLifecycleOwner(parent.lifecycle, active = false)
                parent.lifecycle.addObserver(page)
                vm = CategoriesViewModel(GetCategorySummariesUseCase(repository), dates, repository, selection)
                scope.launch { page.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.uiState.collect {} } }
            }
            instrumentation.waitForIdleSync()
            assertEquals(emptyList<MonthPeriod>(), requestedPeriods.toList())
            instrumentation.runOnMainSync { page.setActive(true) }
            awaitQueries(1)
            assertEquals(listOf(month), requestedPeriods.toList())
            instrumentation.runOnMainSync { page.setActive(false) }
            awaitQueries(0)
            instrumentation.runOnMainSync { selection.select(month.previousMonth()) }
            instrumentation.waitForIdleSync()
            assertEquals(listOf(month), requestedPeriods.toList())
            instrumentation.runOnMainSync { page.setActive(true) }
            awaitQueries(1)
            assertEquals(listOf(month, month.previousMonth()), requestedPeriods.toList())
            instrumentation.runOnMainSync { parent.registry.currentState = Lifecycle.State.DESTROYED }
            awaitQueries(0)
        } finally {
            instrumentation.runOnMainSync {
                parent.lifecycle.removeObserver(page)
                page.dispose()
                scope.cancel()
                vm.viewModelScope.cancel()
            }
        }
    }

    @Test
    fun prefetchedPageOnlyCollectsWhenVisibleAndNeverOutlivesActivity() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val parent = object : LifecycleOwner {
                val registry = LifecycleRegistry(this)
                override val lifecycle: Lifecycle get() = registry
            }
            parent.registry.currentState = Lifecycle.State.RESUMED
            val page = PageLifecycleOwner(parent.lifecycle, active = false)
            parent.lifecycle.addObserver(page)
            assertEquals(Lifecycle.State.CREATED, page.lifecycle.currentState)
            page.setActive(true)
            assertEquals(Lifecycle.State.RESUMED, page.lifecycle.currentState)
            page.setActive(false)
            assertEquals(Lifecycle.State.CREATED, page.lifecycle.currentState)
            parent.registry.currentState = Lifecycle.State.CREATED
            page.setActive(true)
            assertEquals(Lifecycle.State.CREATED, page.lifecycle.currentState)
            parent.registry.currentState = Lifecycle.State.STARTED
            assertEquals(Lifecycle.State.STARTED, page.lifecycle.currentState)
            parent.registry.currentState = Lifecycle.State.DESTROYED
            assertEquals(Lifecycle.State.DESTROYED, page.lifecycle.currentState)
            parent.lifecycle.removeObserver(page)
            page.dispose()
        }
    }
}
