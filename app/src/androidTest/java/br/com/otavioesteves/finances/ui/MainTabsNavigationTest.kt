package br.com.otavioesteves.finances.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.utils.formatMonthPeriod
import br.com.otavioesteves.finances.MainActivity
import br.com.otavioesteves.finances.MainApplication
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainTabsNavigationTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetSelectedMonth() {
        val app = composeRule.activity.application as br.com.otavioesteves.finances.MainApplication
        composeRule.runOnIdle { app.container.selectedMonth.select(MonthPeriod.now()) }
    }

    @Test
    fun allFinancialPagesShareTheMonthAndKeepItAfterRecreation() {
        val selected = MonthPeriod.now().previousMonth()
        fun assertMonth(period: MonthPeriod) {
            composeRule.waitUntil(10_000) {
                val matches = composeRule.onAllNodesWithText("${formatMonthPeriod(period)} ${period.year}")
                matches.fetchSemanticsNodes().indices.any { matches[it].isDisplayed() }
            }
        }
        fun changeMonth(description: String) {
            val matches = composeRule.onAllNodesWithContentDescription(description)
            val index = matches.fetchSemanticsNodes().indices.first { matches[it].isDisplayed() }
            matches[index].performClick()
        }
        changeMonth("Mês anterior")
        composeRule.onNodeWithContentDescription("Gráficos").performClick()
        assertMonth(selected)
        composeRule.onNodeWithContentDescription("Transações").performClick()
        assertMonth(selected)
        changeMonth("Mês anterior")
        val earlier = selected.previousMonth()
        composeRule.onNodeWithContentDescription("Categorias").performClick()
        assertMonth(earlier)
        composeRule.onNodeWithContentDescription("Transações").performClick()
        assertMonth(earlier)
        changeMonth("Próximo mês")
        composeRule.onNodeWithContentDescription("Categorias").performClick()
        assertMonth(selected)
        composeRule.onNodeWithContentDescription("Início").performClick()
        assertMonth(selected)
        composeRule.activityRule.scenario.recreate()
        assertMonth(selected)
        composeRule.onNodeWithContentDescription("Chat").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Abrir menu").performClick()
        composeRule.onNodeWithText("Chat").assertDoesNotExist()
        composeRule.onNodeWithText("Configurações").performScrollTo().performClick()
        composeRule.onNodeWithText("Modelo de IA").performScrollTo().performClick()
        composeRule.onNodeWithText("Modelo de IA").assertIsDisplayed()
    }

    @Test
    fun categoryDetailsKeepTheSelectedMonth() {
        composeRule.onNodeWithContentDescription("Categorias").performClick()
        val previousButtons = composeRule.onAllNodesWithContentDescription("Mês anterior")
        previousButtons[previousButtons.fetchSemanticsNodes().indices.first { previousButtons[it].isDisplayed() }].performClick()
        val previous = MonthPeriod.now().previousMonth()
        composeRule.onNodeWithText("Alimentação").performScrollTo().performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Despesas do mês").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("${formatMonthPeriod(previous)} ${previous.year}").assertIsDisplayed()
        composeRule.onNodeWithTag("category_details_total").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Voltar").performClick()
        composeRule.waitUntil(10_000) {
            val labels = composeRule.onAllNodesWithText("${formatMonthPeriod(previous)} ${previous.year}")
            labels.fetchSemanticsNodes().indices.any { labels[it].isDisplayed() }
        }
    }

    @Test
    fun transactionDateCanBeSelectedFromTheForm() {
        val app = composeRule.activity.application as br.com.otavioesteves.finances.MainApplication
        val selected = app.container.dateProvider.getCurrentDate().withDayOfMonth(15)
        composeRule.onNodeWithContentDescription("Importar fatura ou extrato").performClick()
        composeRule.onNodeWithText("Adicionar lançamento").performScrollTo().performClick()
        composeRule.onNodeWithText("Data", substring = false).performScrollTo().performClick()
        val dayDescription = selected.format(java.time.format.DateTimeFormatter
            .ofLocalizedDate(java.time.format.FormatStyle.FULL).withLocale(java.util.Locale.getDefault()))
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(dayDescription, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(dayDescription, substring = true).performClick()
        composeRule.onNodeWithText("Aplicar data").performClick()
        composeRule.onNodeWithText(br.com.otavioesteves.finances.utils.DateFormatter.format(selected)).assertIsDisplayed()
    }

    @Test
    fun bottomBarOpensEachPrimaryPage() {
        composeRule.onNodeWithContentDescription("Gráficos").performClick()
        composeRule.onNodeWithTag("charts_title").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Transações").performClick()
        composeRule.onNodeWithTag("transactions_title").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Categorias").performClick()
        composeRule.onNodeWithText("Criar categoria").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Categorias").assertIsSelected()
        composeRule.onNodeWithContentDescription("Chat").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Início").performClick()
        composeRule.onNodeWithContentDescription("Abrir menu").assertIsDisplayed()
    }

    @Test
    fun viewTransactionsKeepsTheMonthSelectedOnHome() {
        val previous = MonthPeriod.now().previousMonth()
        val container = (composeRule.activity.application as MainApplication).container
        val description = "Navegação de teste ${java.util.UUID.randomUUID()}"
        val transactionId = runBlocking {
            val category = container.categoriesRepository.getCategories().first().first { it.type == CategoryType.EXPENSE }
            container.transactionsRepository.addTransaction(Transaction(
                0, description, Money(100), category.id, previous.toYearMonth().atDay(1), TransactionType.EXPENSE
            ))
            container.transactionsRepository.getTransactions(previous).first().single { it.description == description }.id
        }
        try {
            composeRule.onNodeWithContentDescription("Início").performClick()
            composeRule.onAllNodesWithContentDescription("Mês anterior").onFirst().performClick()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithText(description).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("dashboard_view_transactions").performScrollTo().performClick()

            composeRule.onNodeWithTag("transactions_title").assertIsDisplayed()
            composeRule.onAllNodesWithText("${formatMonthPeriod(previous)} ${previous.year}")
                .onFirst().assertIsDisplayed()
            composeRule.onNodeWithText(description).assertIsDisplayed()

            composeRule.onNodeWithContentDescription("Voltar").performClick()
            composeRule.onNodeWithTag("dashboard_view_transactions").performScrollTo().performClick()
            composeRule.onNodeWithTag("transactions_title").assertIsDisplayed()
            composeRule.onAllNodesWithText("${formatMonthPeriod(previous)} ${previous.year}")
                .onFirst().assertIsDisplayed()
        } finally {
            runBlocking { container.transactionsRepository.removeTransaction(transactionId) }
        }
    }

    @Test
    fun horizontalSwipesActivateEachPrefetchedPage() {
        composeRule.onRoot().performTouchInput { swipeLeft(durationMillis = 250) }
        composeRule.onNodeWithTag("charts_title").assertIsDisplayed()
        composeRule.onRoot().performTouchInput { swipeLeft(durationMillis = 250) }
        composeRule.onNodeWithTag("transactions_title").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Transações").assertIsSelected()
    }

    @Test
    fun rapidTabRequestsKeepTheLastDestinationSelected() {
        val actions = listOf("Gráficos", "Transações", "Início", "Gráficos", "Transações").map { label ->
            requireNotNull(composeRule.onNodeWithContentDescription(label).fetchSemanticsNode()
                .config[SemanticsActions.OnClick].action)
        }
        composeRule.runOnUiThread { actions.forEach { it() } }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Transações").assertIsSelected()
        composeRule.onNodeWithTag("transactions_title").assertIsDisplayed()
    }

    @Test
    fun chartStyleSurvivesLeavingAndRecreatingThePage() {
        composeRule.onNodeWithContentDescription("Gráficos").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithContentDescription("Tipo do gráfico Categorias: Rosca").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("charts_list")
            .performScrollToNode(hasContentDescription("Tipo do gráfico Categorias: Rosca"))
        composeRule.onNodeWithContentDescription("Tipo do gráfico Categorias: Rosca")
            .performClick()
        composeRule.onNodeWithText("Pizza").performClick()
        composeRule.onNodeWithContentDescription("Início").performClick()
        composeRule.onNodeWithContentDescription("Gráficos").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithContentDescription("Tipo do gráfico Categorias: Pizza").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("charts_list")
            .performScrollToNode(hasContentDescription("Tipo do gráfico Categorias: Pizza"))
        composeRule.onNodeWithContentDescription("Tipo do gráfico Categorias: Pizza")
            .assertIsDisplayed()
    }

    @Test
    fun tappingOutsideDrawerClosesIt() {
        composeRule.onNodeWithContentDescription("Abrir menu").performClick()
        composeRule.onNodeWithText("Finances").assertIsDisplayed()
        composeRule.onNodeWithText("Configurações").assertIsDisplayed()
        composeRule.onNodeWithText("PRIVADO · NO SEU APARELHO").assertIsDisplayed()
        composeRule.onNodeWithText("Histórico").assertDoesNotExist()
        composeRule.onNodeWithText("Novo lançamento").assertDoesNotExist()
        composeRule.onNodeWithText("Seu espaço financeiro").assertDoesNotExist()

        composeRule.onRoot().performTouchInput {
            click(Offset(center.x * 1.9f, center.y))
        }

        composeRule.onNodeWithText("Finances").assertIsNotDisplayed()
    }
}
