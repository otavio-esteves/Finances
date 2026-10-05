package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.FinancialContext
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.utils.MoneyFormatter
import br.com.otavioesteves.finances.utils.formatMonthPeriod
import java.text.Normalizer

/** Answers supported numeric questions from queried totals, without asking a language model to calculate. */
internal fun answerFinancialQuestion(message: String, context: FinancialContext): String? {
    val text = normalizeFinancialText(message)
    val period = "${formatMonthPeriod(context.period)} de ${context.period.year}"
    if (BALANCE_QUESTION.containsMatchIn(text)) {
        return "Seu saldo em $period é de ${MoneyFormatter.format(context.monthlyBalance)}."
    }

    val asksExpense = EXPENSE_QUESTION.containsMatchIn(text)
    val asksIncome = INCOME_QUESTION.containsMatchIn(text)
    if (!asksExpense && !asksIncome) return null
    val expectedType = if (asksExpense) CategoryType.EXPENSE else CategoryType.INCOME
    val category = context.categorySummaries.firstOrNull {
        it.category.type == expectedType && normalizeFinancialText(it.category.name) in text
    }
    if (category != null) {
        val verb = if (asksExpense) "gastou" else "recebeu"
        return "Você $verb ${MoneyFormatter.format(category.totalAmount)} em ${category.category.name} em $period."
    }
    if (SPECIFIC_TARGET.containsMatchIn(text.replace(PERIOD_REFERENCE, " "))) {
        return "Ainda não consigo calcular esse gasto ou recebimento por estabelecimento. " +
            "Consigo consultar apenas totais por categoria em $period."
    }
    val total = context.categorySummaries.asSequence()
        .filter { it.category.type == expectedType }
        .fold(Money.Zero) { sum, summary -> sum + summary.totalAmount }
    val label = if (asksExpense) "despesas" else "receitas"
    return "O total de $label em $period foi ${MoneyFormatter.format(total)}."
}

private fun normalizeFinancialText(value: String): String =
    Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

private val BALANCE_QUESTION = Regex("\\bsaldo\\b|\\bbalanco\\b")
private val EXPENSE_QUESTION = Regex("\\bquanto\\b.*\\b(?:gastei|gasto|gastos|despesa|despesas)\\b")
private val INCOME_QUESTION = Regex("\\bquanto\\b.*\\b(?:recebi|receita|receitas|ganhei)\\b")
private val PERIOD_REFERENCE = Regex(
    "\\b(?:em|no|na|de)\\s+(?:janeiro|jan|fevereiro|fev|marco|mar|abril|abr|maio|mai|junho|jun|" +
        "julho|jul|agosto|ago|setembro|set|outubro|out|novembro|nov|dezembro|dez)" +
        "(?:\\s+de\\s+\\d{4})?\\b"
)
private val SPECIFIC_TARGET = Regex("\\b(?:com|em|no|na)\\s+(?!total\\b|mes\\b|periodo\\b)[a-z][a-z0-9 ]+")
