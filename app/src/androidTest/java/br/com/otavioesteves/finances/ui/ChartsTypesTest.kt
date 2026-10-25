package br.com.otavioesteves.finances.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import br.com.otavioesteves.finances.domain.usecase.GetCategorySummariesUseCase
import br.com.otavioesteves.finances.presentation.charts.ChartsViewModel
import br.com.otavioesteves.finances.ui.screens.charts.ChartsScreen
import br.com.otavioesteves.finances.ui.theme.FinancesTheme
import br.com.otavioesteves.finances.utils.MoneyFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Uses a synthetic in-memory repository: opening these charts never modifies the installed database. */
@RunWith(AndroidJUnit4::class)
class ChartsTypesTest {
    @get:Rule val composeRule = createComposeRule()
    private val viewModelStore = ViewModelStore()
    private val food = Category(1, "Alimentação", CategoryType.EXPENSE)
    private val health = Category(2, "Saúde", CategoryType.EXPENSE)
    private val transactions = listOf(
        Transaction(1, "Mercado", Money(25_000), food.id, LocalDate.of(2025, 2, 10), TransactionType.EXPENSE),
        Transaction(2, "Consulta", Money(10_000), health.id, LocalDate.of(2025, 8, 10), TransactionType.EXPENSE)
    )

    @After
    fun clearViewModel() {
        composeRule.runOnIdle { viewModelStore.clear() }
    }

    @Test
    fun chartMenusSwitchBetweenPieDonutBarsLinesAndArea() {
        val viewModel = viewModel(transactions)
        composeRule.setContent { FinancesTheme { ChartsScreen(viewModel = viewModel) } }
        composeRule.waitUntil(timeoutMillis = 5_000) { !viewModel.uiState.value.isLoading }

        selectStyle("Categorias", "Rosca", "Pizza")
        composeRule.onNodeWithContentDescription("Pizza. Alimentação: ${MoneyFormatter.format(Money(25_000))}").assertExists()
        selectStyle("Categorias", "Pizza", "Rosca")
        composeRule.onNodeWithContentDescription("Rosca. Alimentação: ${MoneyFormatter.format(Money(25_000))}").assertExists()

        val allAmounts = List(12) { when (it) { 1 -> 25_000L; 7 -> 10_000L; else -> 0L } }
        selectStyle("Gastos em 2025", "Barras", "Linhas")
        composeRule.onNodeWithContentDescription(annualDescription("Linhas", allAmounts)).assertExists()
        selectStyle("Gastos em 2025", "Linhas", "Área")
        composeRule.onNodeWithContentDescription(annualDescription("Área", allAmounts)).assertExists()
        selectStyle("Gastos em 2025", "Área", "Barras")
        composeRule.onNodeWithContentDescription(annualDescription("Barras", allAmounts)).assertExists()

        val foodAmounts = List(12) { if (it == 1) 25_000L else 0L }
        selectStyle("Por categoria", "Barras", "Linhas")
        composeRule.onNodeWithContentDescription(annualDescription("Linhas", foodAmounts)).assertExists()
        selectStyle("Por categoria", "Linhas", "Área")
        composeRule.onNodeWithContentDescription(annualDescription("Área", foodAmounts)).assertExists()
    }

    @Test
    fun chartChoicesSurviveLeavingThePageAndRestoringSavedState() {
        val viewModel = viewModel(transactions)
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            FinancesTheme {
                var showCharts by rememberSaveable { mutableStateOf(true) }
                val holder = rememberSaveableStateHolder()
                Column {
                    TextButton(onClick = { showCharts = !showCharts }) {
                        Text(if (showCharts) "Sair dos gráficos" else "Voltar aos gráficos")
                    }
                    if (showCharts) holder.SaveableStateProvider("charts") {
                        ChartsScreen(viewModel = viewModel)
                    }
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { !viewModel.uiState.value.isLoading }
        selectStyle("Categorias", "Rosca", "Pizza")
        selectStyle("Gastos em 2025", "Barras", "Área")
        composeRule.onNodeWithText("Sair dos gráficos").performClick()
        composeRule.onNodeWithText("Voltar aos gráficos").performClick()
        scrollToMenu("Gastos em 2025", "Área")
        restoration.emulateSavedInstanceStateRestore()
        scrollToMenu("Gastos em 2025", "Área")
        scrollToMenu("Categorias", "Pizza")
    }

    @Test
    fun zeroYearSupportsEveryTrendStyleAndCategoryEmptyState() {
        val viewModel = viewModel(emptyList())
        composeRule.setContent { FinancesTheme { ChartsScreen(viewModel = viewModel) } }
        composeRule.waitUntil(timeoutMillis = 5_000) { !viewModel.uiState.value.isLoading }
        scrollToMenu("Categorias", "Rosca")
        composeRule.onNodeWithText("Nenhuma despesa categorizada neste período").assertExists()
        selectStyle("Categorias", "Rosca", "Pizza")
        composeRule.onNodeWithText("Nenhuma despesa categorizada neste período").assertExists()
        selectStyle("Gastos em 2025", "Barras", "Linhas")
        composeRule.onNodeWithContentDescription(annualDescription("Linhas", List(12) { 0L })).assertExists()
        selectStyle("Gastos em 2025", "Linhas", "Área")
        composeRule.onNodeWithContentDescription(annualDescription("Área", List(12) { 0L })).assertExists()
        selectStyle("Gastos em 2025", "Área", "Barras")
        composeRule.onAllNodesWithContentDescription(annualDescription("Barras", List(12) { 0L })).onFirst().assertExists()
    }

    private fun selectStyle(chart: String, current: String, next: String) {
        scrollToMenu(chart, current)
        composeRule.onNodeWithContentDescription("Tipo do gráfico $chart: $current").performClick()
        composeRule.onNodeWithText(next).performClick()
        composeRule.onNodeWithContentDescription("Tipo do gráfico $chart: $next").assertExists()
    }

    private fun scrollToMenu(chart: String, style: String) {
        val description = "Tipo do gráfico $chart: $style"
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(description))
        composeRule.onNodeWithContentDescription(description).assertExists()
    }

    private fun annualDescription(style: String, amounts: List<Long>): String {
        val months = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
        return "$style. " + months.mapIndexed { index, month ->
            "$month: ${MoneyFormatter.format(Money(amounts[index]))}"
        }.joinToString("; ")
    }

    private fun viewModel(data: List<Transaction>): ChartsViewModel {
        val categories = object : CategoriesRepository {
            override fun getCategories(): Flow<List<Category>> = flowOf(listOf(food, health))
            override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> = flowOf(
                data.filter { it.date.year == period.year && it.date.monthValue == period.month }
                    .groupBy { it.categoryId }.map { (id, entries) ->
                        CategorySummary(if (id == food.id) food else health, Money(entries.sumOf { it.amount.cents }))
                    }
            )
        }
        val repository = object : TransactionsRepository {
            override fun getTransaction(id: Long): Flow<Transaction?> = flowOf(data.find { it.id == id })
            override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> = flowOf(
                data.filter { it.date.year == period.year && it.date.monthValue == period.month }
            )
            override fun getMonthlyBalance(period: MonthPeriod): Flow<Money> = flowOf(Money.Zero)
            override suspend fun addTransaction(transaction: Transaction) = Unit
            override suspend fun removeTransaction(transactionId: Long) = Unit
            override suspend fun updateTransaction(transaction: Transaction) = Unit
        }
        val dates = object : DateProvider {
            override fun getCurrentDate(): LocalDate = LocalDate.of(2025, 2, 15)
            override fun getCurrentMonthPeriod(): MonthPeriod = MonthPeriod(2025, 2)
            override fun getCurrentDateTime() = getCurrentDate().atStartOfDay()
        }
        return ChartsViewModel(GetCategorySummariesUseCase(categories), repository, categories, dates).also {
            viewModelStore.put("charts", it)
        }
    }
}
