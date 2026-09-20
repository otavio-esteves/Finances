package br.com.otavioesteves.finances.data.ai

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.ChatRole
import br.com.otavioesteves.finances.domain.model.FinancialContext
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.SuggestionSource
import br.com.otavioesteves.finances.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RuleBasedLocalAiRepositoryTest {

    private val dateProvider = object : DateProvider {
        override fun getCurrentMonthPeriod(): MonthPeriod = MonthPeriod(2026, 1)
        override fun getCurrentDate(): LocalDate = LocalDate.of(2026, 1, 15)
        override fun getCurrentDateTime(): LocalDateTime = LocalDateTime.of(2026, 1, 15, 10, 0)
    }
    private val repository = RuleBasedLocalAiRepository(dateProvider)

    @Test
    fun suggestCategories_matchesByKeywordWithinSameType() = runBlocking {
        val mercado = Category(id = 1L, name = "Mercado", type = CategoryType.EXPENSE)
        val salario = Category(id = 2L, name = "Salário", type = CategoryType.INCOME)
        val entry = RawStatementEntry(
            description = "Compra no Mercado Central",
            amount = Money.fromCents(5_000),
            date = LocalDate.of(2026, 1, 10),
            type = TransactionType.EXPENSE
        )

        val suggestions = repository.categorize(listOf(entry), listOf(mercado, salario)).getOrThrow()

        assertEquals(mercado, suggestions.single().suggestedCategory)
        assertTrue(suggestions.single().confidence > 0f)
        assertEquals(SuggestionSource.RULE, suggestions.single().source)
    }

    @Test
    fun suggestCategories_fallsBackToOutrosWhenNoKeywordMatches() = runBlocking {
        val outros = Category(id = 3L, name = "Outros", type = CategoryType.EXPENSE)
        val entry = RawStatementEntry(
            description = "Pagamento diverso",
            amount = Money.fromCents(1_000),
            date = LocalDate.of(2026, 1, 10),
            type = TransactionType.EXPENSE
        )

        val suggestion = repository.categorize(listOf(entry), listOf(outros)).getOrThrow().single()

        assertEquals(outros, suggestion.suggestedCategory)
    }

    @Test
    fun suggestCategories_returnsNullSuggestionWhenNoCategoryMatchesType() = runBlocking {
        val salario = Category(id = 2L, name = "Salário", type = CategoryType.INCOME)
        val entry = RawStatementEntry(
            description = "Compra qualquer",
            amount = Money.fromCents(1_000),
            date = LocalDate.of(2026, 1, 10),
            type = TransactionType.EXPENSE
        )

        val suggestion = repository.categorize(listOf(entry), listOf(salario)).getOrThrow().single()

        assertNull(suggestion.suggestedCategory)
        assertEquals(0f, suggestion.confidence)
    }

    @Test
    fun sendMessage_answersWithCategoryTotalWhenMessageMentionsIt() = runBlocking {
        val context = FinancialContext(
            period = MonthPeriod(2026, 1),
            monthlyBalance = Money.fromCents(100_000),
            categorySummaries = listOf(
                CategorySummary(
                    category = Category(id = 1L, name = "Mercado", type = CategoryType.EXPENSE),
                    totalAmount = Money.fromCents(25_000)
                )
            )
        )

        val reply = repository.sendMessage("Quanto gastei com mercado?", context)

        assertEquals(ChatRole.ASSISTANT, reply.role)
        assertTrue(reply.content.contains("R$"))
        assertTrue(reply.content.contains("Mercado"))
    }

    @Test
    fun sendMessage_answersWithBalanceWhenMessageMentionsSaldo() = runBlocking {
        val context = FinancialContext(
            period = MonthPeriod(2026, 1),
            monthlyBalance = Money.fromCents(150_000),
            categorySummaries = emptyList()
        )

        val reply = repository.sendMessage("Qual meu saldo?", context)

        assertTrue(reply.content.contains("saldo"))
    }
}
