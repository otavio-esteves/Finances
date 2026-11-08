package br.com.otavioesteves.finances.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.presentation.dashboard.DashboardUiState
import br.com.otavioesteves.finances.ui.screens.dashboard.DashboardContent
import br.com.otavioesteves.finances.ui.theme.FinancesTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardImprovementsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun firstUseExplainsImportAndOpensItDirectly() {
        var opened = false
        rule.setContent {
            FinancesTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DashboardContent(
                        DashboardUiState(MonthPeriod(2026, 11), Money.Zero, hasAnyTransactions = false),
                        onHistoryClick = {}, onPreviousMonthClick = {}, onNextMonthClick = {},
                        onImportStatementClick = { opened = true }
                    )
                }
            }
        }
        rule.onNodeWithText("Comece pelo seu primeiro arquivo").assertIsDisplayed()
        captureReviewScreenshot(rule, "first-import")
        rule.onNodeWithTag("dashboard_view_transactions").assertDoesNotExist()
        rule.onNodeWithTag("dashboard_import_statement").performClick()
        rule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun emptyMonthForExistingUserOffersImportWithoutFirstUseMessage() {
        rule.setContent {
            FinancesTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DashboardContent(
                        DashboardUiState(MonthPeriod(2026, 11), Money.Zero, hasAnyTransactions = true),
                        onHistoryClick = {}, onPreviousMonthClick = {}, onNextMonthClick = {}
                    )
                }
            }
        }
        rule.onNodeWithText("Comece pelo seu primeiro arquivo").assertDoesNotExist()
        captureReviewScreenshot(rule, "monthly-result")
        rule.onNodeWithTag("dashboard_import_statement").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Importar fatura ou extrato").assertIsDisplayed()
        rule.onNodeWithTag("dashboard_view_transactions").assertDoesNotExist()
    }

}
