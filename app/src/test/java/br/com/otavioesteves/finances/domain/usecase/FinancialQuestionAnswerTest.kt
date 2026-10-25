package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.FinancialContext
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialQuestionAnswerTest {
    private val food = Category(1, "Alimentação", CategoryType.EXPENSE)
    private val transport = Category(2, "Transporte", CategoryType.EXPENSE)
    private val health = Category(3, "Saúde", CategoryType.EXPENSE)
    private val salary = Category(4, "Salário", CategoryType.INCOME)
    private val context = FinancialContext(
        MonthPeriod(2026, 2), Money(165_000),
        listOf(CategorySummary(food, Money(25_000)), CategorySummary(transport, Money(10_000)),
            CategorySummary(salary, Money(200_000))),
        listOf(food, transport, health, salary)
    )

    @Test
    fun sumsAllRequestedCategoriesRegardlessOfOrderAndRepeatedNames() {
        listOf("alimentação e transporte", "transporte, alimentação", "alimentação e alimentação e transporte")
            .forEach { categories ->
                val reply = answer("Quanto gastei com $categories em fevereiro de 2026?")
                assertTrue(reply, reply.contains("350,00"))
                assertTrue(reply, reply.contains("Alimentação"))
                assertTrue(reply, reply.contains("Transporte"))
            }
    }

    @Test
    fun excludesRequestedCategoriesFromExpenseTotals() {
        listOf("sem", "exceto", "excluindo", "menos").forEach { operator ->
            val reply = answer("Quanto gastei $operator alimentação em fevereiro de 2026?")
            assertTrue(reply, reply.contains("100,00"))
            assertTrue(reply, reply.contains("excluindo Alimentação"))
        }
        assertTrue(answer("Quanto gastei sem alimentação e transporte?").contains("0,00"))
    }

    @Test
    fun excludesOnlyFromExplicitlySelectedCategories() {
        val reply = answer("Quanto gastei com alimentação e transporte sem transporte?")
        assertTrue(reply, reply.contains("250,00"))
        assertTrue(reply, reply.contains("excluindo Transporte"))
    }

    @Test
    fun registeredCategoryWithoutTransactionsReturnsZero() {
        val reply = answer("Quanto gastei com saúde em fevereiro de 2026?")
        assertTrue(reply, reply.contains("0,00"))
        assertTrue(reply, reply.contains("Saúde"))
        assertFalse(reply, reply.contains("não consigo"))
    }

    @Test
    fun emptyMonthStillRecognizesRegisteredCategory() {
        val reply = answerFinancialQuestion("Quanto gastei com saúde?", context.copy(categorySummaries = emptyList()))!!
        assertTrue(reply, reply.contains("0,00"))
        assertTrue(reply, reply.contains("Saúde"))
    }

    @Test
    fun refusesUnknownMerchantsMixedTargetsAndUnsupportedQualifiers() {
        listOf(
            "Quanto gastei com Mercado?",
            "Quanto gastei com alimentação e Mercado?",
            "Quanto gastei com alimentação no Mercado?",
            "Quanto gastei sem Mercado?",
            "Quanto gastei com alimentação ou transporte?",
            "Quanto gastei com alimentação em dinheiro?",
            "Quanto gastei com alimentação acima de 100 reais?",
            "Quanto gastei com alimentação e quanto recebi?"
        ).forEach { message ->
            val reply = answer(message)
            assertTrue("$message: $reply", reply.contains("não consigo calcular"))
            assertFalse(reply, reply.contains("R$"))
        }
    }

    @Test
    fun doesNotMatchCategoryInsideAnotherWordOrLongerName() {
        listOf("Quanto gastei com transportes?", "Quanto gastei com alimentação especial?")
            .forEach { assertTrue(answer(it).contains("não consigo calcular")) }
        val publicTransport = Category(5, "Transporte público", CategoryType.EXPENSE)
        val longerContext = context.copy(
            categories = context.categories + publicTransport,
            categorySummaries = context.categorySummaries + CategorySummary(publicTransport, Money(2_000))
        )
        val reply = answerFinancialQuestion("Quanto gastei com transporte público?", longerContext)!!
        assertTrue(reply, reply.contains("20,00"))
        assertFalse(reply, reply.contains("100,00"))
    }

    @Test
    fun refusesAmbiguousCategoryNames() {
        val duplicate = food.copy(id = 9, name = "ALIMENTACAO")
        val reply = answerFinancialQuestion("Quanto gastei com alimentação?", context.copy(categories = context.categories + duplicate))!!
        assertTrue(reply, reply.contains("não consigo calcular"))
    }

    @Test
    fun handlesCategoryNamesContainingConjunctionsAndRegexCharacters() {
        val special = Category(8, "Casa e jardim (fixos)", CategoryType.EXPENSE)
        val reply = answerFinancialQuestion("Quanto gastei com casa e jardim (fixos)?", context.copy(
            categories = context.categories + special,
            categorySummaries = context.categorySummaries + CategorySummary(special, Money(1234))
        ))!!
        assertTrue(reply, reply.contains("12,34"))
    }

    @Test
    fun keepsMonthFormatsAndUnfilteredTotals() {
        listOf("em fevereiro de 2026", "em fev/2026", "em 02/2026", "em 2026-02", "neste mês", "no total", "")
            .forEach { period ->
                val reply = answer("Quanto gastei $period?")
                assertTrue("$period: $reply", reply.contains("350,00"))
                assertTrue(reply, reply.contains("Fevereiro de 2026"))
            }
    }

    @Test
    fun incomeQueriesDoNotIncludeExpenses() {
        assertTrue(answer("Quanto recebi com salário?").contains("2.000,00"))
        assertTrue(answer("Quanto recebi sem salário?").contains("0,00"))
        assertTrue(answer("Quanto gastei com salário?").contains("não consigo calcular"))
    }

    @Test
    fun balanceQueriesDoNotIgnoreCategoryFiltersOrOtherQuestions() {
        assertTrue(answer("Qual meu saldo?").contains("1.650,00"))
        assertTrue(answer("Qual é o meu saldo em fevereiro de 2026?").contains("1.650,00"))
        listOf("Qual meu saldo sem alimentação?", "Quanto gastei e qual meu saldo?")
            .forEach { assertTrue(answer(it).contains("não consigo calcular")) }
    }

    private fun answer(message: String): String = requireNotNull(answerFinancialQuestion(message, context))
}
