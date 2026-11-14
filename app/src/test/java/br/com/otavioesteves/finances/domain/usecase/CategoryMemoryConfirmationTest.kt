package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.domain.repository.StatementImportRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CategoryMemoryConfirmationTest {
    private val food = Category(1, "Alimentação", CategoryType.EXPENSE)
    private val other = Category(2, "Outros", CategoryType.EXPENSE)
    private val date = object : DateProvider {
        override fun getCurrentDate() = LocalDate.of(2026, 11, 1)
        override fun getCurrentDateTime() = LocalDateTime.of(2026, 11, 1, 9, 0)
        override fun getCurrentMonthPeriod() = MonthPeriod(2026, 11)
    }
    private class Repository : StatementImportRepository {
        var remembered = emptyList<CategoryMemory>()
        var source: String? = null
        override fun getImports() = flowOf(emptyList<ImportedStatement>())
        override suspend fun confirmImport(statementImport: ImportedStatement, transactions: List<Transaction>, fingerprint: String, legacyFingerprint: String?) = error("Reviewed path required")
        override suspend fun confirmReviewedImport(statementImport: ImportedStatement, transactions: List<Transaction>, fingerprint: String,
            legacyFingerprint: String?, memories: List<CategoryMemory>, additionalLegacyFingerprints: List<String>) { remembered = memories; source = statementImport.sourceLabel }
    }
    private fun suggestion() = CategorySuggestion(RawStatementEntry("CAFÉ 123", Money(1500), date.getCurrentDate(), TransactionType.EXPENSE), food, 1f)

    @Test fun onlyExplicitCategoryChoicesTeachMemory() = runTest {
        val repo = Repository()
        val confirm = ConfirmStatementImportUseCase(repo, date)
        confirm("a.csv", listOf(suggestion().copy(source = SuggestionSource.USER)), "  Cartão pessoal  ")
        assertTrue(repo.remembered.isEmpty())
        assertEquals("Cartão pessoal", repo.source)
        confirm("a.csv", listOf(suggestion().copy(source = SuggestionSource.USER, rememberCorrection = true)))
        assertEquals(listOf(CategoryMemory("cafe 123", TransactionType.EXPENSE, 1)), repo.remembered)
    }
    @Test fun conflictingChoicesForSameDescriptionDoNotTeachArbitraryLastChoice() = runTest {
        val repo = Repository()
        ConfirmStatementImportUseCase(repo, date)("a.csv", listOf(
            suggestion().copy(rememberCorrection = true, source = SuggestionSource.USER), suggestion().copy(suggestedCategory = other)
        ))
        assertTrue(repo.remembered.isEmpty())
    }
}
