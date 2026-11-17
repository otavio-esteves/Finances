package br.com.otavioesteves.finances.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.ui.screens.importstatement.PotentialOverlapComparison
import br.com.otavioesteves.finances.ui.theme.FinancesTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ImportOverlapComparisonTest {
    @get:Rule val rule = createComposeRule()
    private val entry = RawStatementEntry("Compra", Money(100), LocalDate.of(2026, 11, 1), TransactionType.EXPENSE)
    private fun matches(count: Int) = List(count) { index ->
        Transaction(index + 1L, entry.description, entry.amount, 1, entry.date, entry.type, sourceLabel = "Fonte ${index + 1}")
    }

    @Test fun thousandsOfCandidatesArePagedWithoutLosingTheCountOrPreviousCandidates() {
        rule.setContent { FinancesTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PotentialOverlapComparison(CategorySuggestion(entry, null, 0f), matches(2000), emptyList())
            }
        } }
        rule.onNodeWithText("No histórico (2000)").assertExists()
        rule.onAllNodesWithText("Fonte ", substring = true).assertCountEquals(5)
        rule.onNodeWithText("Fonte 6").assertDoesNotExist()
        rule.onNodeWithText("Próximos").performScrollTo().performClick()
        rule.onNodeWithText("6–10 de 2000").assertExists()
        rule.onNodeWithText("Fonte 1").assertDoesNotExist()
        rule.onNodeWithText("Fonte 10").assertExists()
        rule.onNodeWithText("Anteriores").performScrollTo().performClick()
        rule.onNodeWithText("1–5 de 2000").assertExists()
        rule.onNodeWithText("Fonte 1").assertExists()
    }

    @Test fun theLastPageShowsRemainingCandidatesAndNewMatchesResetThePage() {
        val candidates = mutableStateOf(matches(7))
        rule.setContent { FinancesTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PotentialOverlapComparison(CategorySuggestion(entry, null, 0f), candidates.value, emptyList())
            }
        } }
        rule.onNodeWithText("Próximos").performScrollTo().performClick()
        rule.onNodeWithText("6–7 de 7").assertExists()
        rule.onAllNodesWithText("Fonte ", substring = true).assertCountEquals(2)
        rule.onNodeWithText("Próximos").assertIsNotEnabled()
        rule.runOnIdle { candidates.value = matches(2) }
        rule.onNodeWithText("Fonte 1").assertExists()
        rule.onNodeWithText("Próximos").assertDoesNotExist()
    }
}
