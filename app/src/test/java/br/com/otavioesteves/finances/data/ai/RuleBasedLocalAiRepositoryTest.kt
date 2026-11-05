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
    fun suggestCategories_recognizesEverydayTermsAndMerchantsWithAccentsAndPunctuation() = runBlocking {
        val categories = listOf(
            Category(1, "Alimentação", CategoryType.EXPENSE),
            Category(2, "Transporte", CategoryType.EXPENSE),
            Category(3, "Saúde", CategoryType.EXPENSE),
            Category(4, "Moradia", CategoryType.EXPENSE),
            Category(5, "Lazer", CategoryType.EXPENSE),
            Category(6, "Salário", CategoryType.INCOME),
            Category(7, "Outros", CategoryType.EXPENSE)
        )
        val cases = listOf(
            Triple("COMPRA IFOOD*RESTAURANTE", TransactionType.EXPENSE, 1L),
            Triple("Supermercado Central", TransactionType.EXPENSE, 1L),
            Triple("UBER*TRIP", TransactionType.EXPENSE, 2L),
            Triple("Compra FARMACIA São José", TransactionType.EXPENSE, 3L),
            Triple("Pagamento CONDOMÍNIO", TransactionType.EXPENSE, 4L),
            Triple("NETFLIX.COM", TransactionType.EXPENSE, 5L),
            Triple("FOLHA DE PAGAMENTO", TransactionType.INCOME, 6L)
        )
        val entries = cases.map { (description, type, _) -> entry(description, type) }
        val suggestions = repository.categorize(entries, categories).getOrThrow()
        assertEquals(cases.map { it.third }, suggestions.map { it.suggestedCategory?.id })
        assertTrue(suggestions.all { it.source == SuggestionSource.RULE && it.confidence in 0.5f..0.8f })
    }

    @Test
    fun suggestCategories_usesWordBoundariesAndDoesNotTreatMarketplacesAsGroceries() = runBlocking {
        val others = Category(4, "Outros", CategoryType.EXPENSE)
        val categories = listOf(
            Category(1, "Alimentação", CategoryType.EXPENSE),
            Category(2, "Mercado", CategoryType.EXPENSE),
            Category(3, "Transporte", CategoryType.EXPENSE), others
        )
        val descriptions = listOf("MERCADO LIVRE", "MERCADO PAGO", "Pagamento supermercadoz", "Ubertino Comércio", "Pagamento número 99")
        val suggestions = repository.categorize(descriptions.map { entry(it) }, categories).getOrThrow()
        assertTrue(suggestions.all { it.suggestedCategory == others && it.confidence == 0.3f })
    }

    @Test
    fun suggestCategories_preservesExplicitMarketplaceCategoriesWhileIgnoringGenericMercado() = runBlocking {
        val marketplace = Category(1, "Mercado Livre", CategoryType.EXPENSE)
        val payments = Category(2, "Mercado Pago", CategoryType.EXPENSE)
        val categories = listOf(
            Category(3, "Mercado", CategoryType.EXPENSE),
            Category(4, "Alimentação", CategoryType.EXPENSE), marketplace, payments,
            Category(5, "Outros", CategoryType.EXPENSE)
        )
        val suggestions = repository.categorize(listOf(entry("MERCADO LIVRE COMPRA"), entry("MERCADO PAGO")), categories).getOrThrow()
        assertEquals(listOf(marketplace, payments), suggestions.map { it.suggestedCategory })
    }

    @Test
    fun suggestCategories_prefersSpecificMerchantPhraseAndFallsBackOnUnrelatedConflictingTerms() = runBlocking {
        val food = Category(1, "Alimentação", CategoryType.EXPENSE)
        val transport = Category(2, "Transporte", CategoryType.EXPENSE)
        val health = Category(3, "Saúde", CategoryType.EXPENSE)
        val others = Category(4, "Outros", CategoryType.EXPENSE)
        val categories = listOf(transport, food, health, others)
        val suggestions = repository.categorize(listOf(entry("UBER EATS"), entry("IFOOD FARMACIA")), categories).getOrThrow()
        assertEquals(food, suggestions[0].suggestedCategory)
        assertEquals(others, suggestions[1].suggestedCategory)
    }

    @Test
    fun suggestCategories_neverUsesIncomeCategoryForAnExpenseAndDoesNotChooseBetweenDuplicateCategories() = runBlocking {
        val others = Category(3, "Outros", CategoryType.EXPENSE)
        val salary = Category(1, "Salário", CategoryType.INCOME)
        val categories = listOf(salary, Category(2, "Saúde", CategoryType.EXPENSE), others,
            Category(4, "Saúde", CategoryType.EXPENSE))
        val suggestions = repository.categorize(listOf(entry("SALARIO"), entry("FARMÁCIA")), categories).getOrThrow()
        assertTrue(suggestions.all { it.suggestedCategory == others })
    }

    private fun entry(description: String, type: TransactionType = TransactionType.EXPENSE) = RawStatementEntry(
        description, Money(1000), LocalDate.of(2026, 1, 10), type
    )

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
