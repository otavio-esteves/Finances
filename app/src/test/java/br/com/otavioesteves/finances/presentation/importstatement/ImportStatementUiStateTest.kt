package br.com.otavioesteves.finances.presentation.importstatement

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.TransactionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ImportStatementUiStateTest {
    @Test
    fun overflowingReviewShowsValidationErrorAndCanRecoverByExcludingAnEntry() {
        val category = Category(1, "Outros", CategoryType.EXPENSE)
        val entry = RawStatementEntry("Compra", Money(Long.MAX_VALUE), LocalDate.of(2026, 1, 1), TransactionType.EXPENSE)
        val suggestion = CategorySuggestion(entry, category, 1f)
        val state = ImportStatementUiState.ReviewingSuggestions("a.csv", listOf(suggestion, suggestion), listOf(category),
            expectedExpenseTotal = "1,00", differenceAcknowledged = true)
        assertFalse(state.canConfirm)
        assertEquals(null, state.expenseTotal)
        assertEquals(null, state.expenseDifference)
        assertTrue(state.amountError != null)
        val repaired = state.copy(excludedIndices = setOf(1), expectedExpenseTotal = "")
        assertTrue(repaired.canConfirm)
        assertEquals(null, repaired.amountError)
        assertEquals(Money(Long.MAX_VALUE), repaired.expenseTotal)
    }

    @Test
    fun combinedIncomeAndExpensesCannotOverflowThePersistenceBudget() {
        val expense = Category(1, "Outros", CategoryType.EXPENSE)
        val income = Category(2, "Salário", CategoryType.INCOME)
        val entry = RawStatementEntry("Compra", Money(Long.MAX_VALUE), LocalDate.of(2026, 1, 1), TransactionType.EXPENSE)
        val state = ImportStatementUiState.ReviewingSuggestions("a.csv", listOf(
            CategorySuggestion(entry, expense, 1f),
            CategorySuggestion(entry.copy(type = TransactionType.INCOME, amount = Money(1)), income, 1f)
        ), listOf(expense, income))
        assertEquals(Money(Long.MAX_VALUE), state.expenseTotal)
        assertEquals(Money(1), state.incomeTotal)
        assertFalse(state.canConfirm)
    }

    @Test
    fun filtersKeepOriginalIndicesAndExcludedRowsDoNotBlockConfirmation() {
        val category = Category(1, "Mercado", CategoryType.EXPENSE)
        val entry = RawStatementEntry("Compra", Money(100), LocalDate.of(2026, 1, 1), TransactionType.EXPENSE)
        val state = ImportStatementUiState.ReviewingSuggestions(
            "a.csv", listOf(
                CategorySuggestion(entry, category, 0.9f),
                CategorySuggestion(entry.copy(extractedByOcr = true), null, 0.2f),
                CategorySuggestion(entry, category, 0.5f)
            ), listOf(category), excludedIndices = setOf(1)
        )
        assertEquals(listOf(0, 1, 2), state.visibleIndices)
        assertEquals(emptyList<Int>(), state.indicesFor(ImportReviewFilter.UNCATEGORIZED))
        assertEquals(listOf(2), state.indicesFor(ImportReviewFilter.UNCERTAIN))
        assertEquals(listOf(1), state.indicesFor(ImportReviewFilter.EXCLUDED))
        assertEquals(Money(200), state.expenseTotal)
        assertTrue(state.canConfirm)
        assertFalse(state.requiresOcrReview)
        assertFalse(state.copy(excludedIndices = setOf(0, 1, 2)).canConfirm)
        assertFalse(state.copy(excludedIndices = emptySet()).canConfirm)
    }

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
