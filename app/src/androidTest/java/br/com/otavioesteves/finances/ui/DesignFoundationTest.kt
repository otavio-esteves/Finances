package br.com.otavioesteves.finances.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.ui.components.CategoryUsageChart
import br.com.otavioesteves.finances.utils.MoneyFormatter
import br.com.otavioesteves.finances.ui.components.MonthPeriodSelector
import br.com.otavioesteves.finances.ui.theme.FinancesTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesignFoundationTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun monthSelectorKeepsBothActionsReachableWithLargeTextOnNarrowScreen() {
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.8f)) {
                FinancesTheme {
                    var period by remember { mutableStateOf(MonthPeriod(2026, 9)) }
                    Box(Modifier.width(320.dp)) {
                        MonthPeriodSelector(period, { period = period.previousMonth() }, { period = period.nextMonth() })
                    }
                }
            }
        }
        rule.onNodeWithText("Setembro 2026").assertIsDisplayed()
        val previous = rule.onNodeWithContentDescription("Mês anterior").fetchSemanticsNode().boundsInRoot
        val label = rule.onNodeWithText("Setembro 2026").fetchSemanticsNode().boundsInRoot
        val next = rule.onNodeWithContentDescription("Próximo mês").fetchSemanticsNode().boundsInRoot
        assertTrue(previous.right <= label.left)
        assertTrue(label.right <= next.left)
        rule.onNodeWithContentDescription("Próximo mês").performClick()
        rule.onNodeWithText("Outubro 2026").assertIsDisplayed()
    }

    @Test
    fun minimumMonthDisablesNavigationOutsideSupportedDates() {
        rule.setContent { FinancesTheme { MonthPeriodSelector(MonthPeriod(1, 1), {}, {}) } }
        rule.onNodeWithContentDescription("Mês anterior").assertIsNotEnabled()
    }

    @Test
    fun chartKeepsTheFullTotalAndPercentageReadableWithLargeTextAndLongCategory() {
        val name = "Categoria com nome muito extenso para testar a legenda"
        val money = Money(1_234_567)
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.8f)) {
                FinancesTheme {
                    CategoryUsageChart(listOf(CategorySummary(Category(1, name, CategoryType.EXPENSE), money)),
                        modifier = Modifier.width(320.dp))
                }
            }
        }
        rule.onNodeWithText("Total: ${MoneyFormatter.format(money)}").assertIsDisplayed()
        rule.onNodeWithText("100%").assertIsDisplayed()
        val label = rule.onNodeWithText(name).fetchSemanticsNode().boundsInRoot
        val percent = rule.onNodeWithText("100%").fetchSemanticsNode().boundsInRoot
        assertTrue(label.right <= percent.left)
    }
}
