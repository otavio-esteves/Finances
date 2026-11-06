package br.com.otavioesteves.finances.presentation.importstatement

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.TransactionType
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

class ImportStatementUiStateTest {
    @Test
    fun mismatchingOrInvalidExpectedTotalCannotBeSilentlyConfirmed() {
        val category = Category(1, "Outros", CategoryType.EXPENSE)
        val suggestion = CategorySuggestion(RawStatementEntry("Mercado", Money(100), LocalDate.of(2026, 11, 1), TransactionType.EXPENSE), category, 1f)
        val state = ImportStatementUiState.ReviewingSuggestions("fatura.csv", listOf(suggestion), listOf(category), expectedExpenseTotal = "2,00")
        assertFalse(state.canConfirm)
        org.junit.Assert.assertTrue(state.copy(differenceAcknowledged = true).canConfirm)
        org.junit.Assert.assertTrue(state.copy(expectedExpenseTotal = "1,00").canConfirm)
        assertFalse(state.copy(expectedExpenseTotal = "inválido", differenceAcknowledged = true).canConfirm)
    }
    @Test
    fun cannotConfirmWhenSuggestedCategoryHasWrongType() {
        val entry = RawStatementEntry("Mercado", Money.fromCents(100), LocalDate.of(2026, 1, 1), TransactionType.EXPENSE)
        val category = Category(1, "Salário", CategoryType.INCOME)
        val state = ImportStatementUiState.ReviewingSuggestions(
            "extrato.csv", listOf(CategorySuggestion(entry, category, 1f)), listOf(category)
        )

        assertFalse(state.canConfirm)
    }
}
