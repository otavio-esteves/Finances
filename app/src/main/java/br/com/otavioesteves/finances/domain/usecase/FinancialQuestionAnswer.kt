package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.FinancialContext
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.utils.MoneyFormatter
import br.com.otavioesteves.finances.utils.formatMonthPeriod
import java.text.Normalizer
import java.util.Locale

/** Answers supported numeric questions from queried totals, without asking a language model to calculate. */
internal fun answerFinancialQuestion(message: String, context: FinancialContext): String? {
    val text = normalizeFinancialText(message)
    val period = "${formatMonthPeriod(context.period)} de ${context.period.year}"
    val asksExpense = EXPENSE_QUESTION.containsMatchIn(text)
    val asksIncome = INCOME_QUESTION.containsMatchIn(text)
    val asksBalance = BALANCE_QUESTION.containsMatchIn(text)
    val limitation = "Ainda não consigo calcular esse recorte com segurança. " +
        "Consigo consultar totais por categoria em $period; informe os nomes das categorias, " +
        "por exemplo, 'quanto gastei com Alimentação e Transporte?' ou 'quanto gastei sem Alimentação?'."
    if (!asksExpense && !asksIncome) {
        if (!asksBalance) return null
        val request = text.replace(PERIOD_REFERENCE, " ").trim().trimEnd('?', '.', '!').trim()
        return if (BALANCE_REQUEST.matches(request)) {
            "Seu saldo em $period é de ${MoneyFormatter.format(context.monthlyBalance)}."
        } else limitation
    }
    if ((asksExpense && asksIncome) || asksBalance) return limitation

    val expectedType = if (asksExpense) CategoryType.EXPENSE else CategoryType.INCOME
    // Require the whole numeric request to match: no partial category names or ignored qualifiers.
    val request = NUMERIC_REQUEST.matchEntire(text.replace(PERIOD_REFERENCE, " ").trim()) ?: return limitation
    val target = TOTAL_REFERENCE.replace(request.groupValues[1].trim().trimEnd('?', '.', '!'), " ").trim()
    val knownCategories = context.categories.filter { it.type == expectedType }
    val byName = knownCategories.groupBy { normalizeFinancialText(it.name) }
    val names = byName.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
    val included: List<Category>
    val excluded: List<Category>
    if (target.isEmpty()) {
        included = knownCategories
        excluded = emptyList()
    } else {
        if (names.isEmpty()) return limitation
        val categoryPattern = "(?:$names)"
        val listPattern = "$categoryPattern(?:\\s*(?:,|\\be\\b)\\s*(?:(?:com|em|no|na)\\s+)?$categoryPattern)*"
        val filter = Regex(
            "^(?:(?:(?:com|em|no|na)\\s+)?($listPattern))?" +
                "(?:\\s*(?:sem|exceto|excluindo|menos)\\s+($listPattern))?$"
        ).matchEntire(target) ?: return limitation
        fun categoriesIn(value: String): List<Category>? {
            if (value.isEmpty()) return emptyList()
            val categories = mutableListOf<Category>()
            for (match in Regex(categoryPattern).findAll(value)) {
                categories += byName[match.value]?.singleOrNull() ?: return null
            }
            return categories.distinctBy { it.id }
        }
        included = if (filter.groupValues[1].isEmpty()) knownCategories
            else categoriesIn(filter.groupValues[1]) ?: return limitation
        excluded = categoriesIn(filter.groupValues[2]) ?: return limitation
    }
    val selectedIds = included.map { it.id }.toSet() - excluded.map { it.id }.toSet()
    val total = context.categorySummaries.asSequence()
        .filter { it.category.type == expectedType && it.category.id in selectedIds }
        .fold(Money.Zero) { sum, summary -> sum + summary.totalAmount }
    val label = if (asksExpense) "despesas" else "receitas"
    val selection = if (target.isEmpty() || (included == knownCategories && excluded.isNotEmpty())) ""
        else " em ${included.joinToString(" e ") { it.name }}"
    val exclusion = if (excluded.isEmpty()) "" else ", excluindo ${excluded.joinToString(" e ") { it.name }}"
    return "O total de $label$selection$exclusion em $period foi ${MoneyFormatter.format(total)}."
}

private fun normalizeFinancialText(value: String): String =
    Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ").trim()

private val BALANCE_QUESTION = Regex("\\bsaldo\\b|\\bbalanco\\b")
private val BALANCE_REQUEST = Regex(
    "(?:qual(?: e)?|quanto(?: e| tenho de)|mostre|mostrar)?\\s*(?:(?:o )?(?:meu|seu) |o )?(?:saldo|balanco)(?: do mes)?"
)
private val EXPENSE_QUESTION = Regex("\\bquanto\\b.*\\b(?:gastei|gasto|gastos|despesa|despesas)\\b")
private val INCOME_QUESTION = Regex("\\bquanto\\b.*\\b(?:recebi|receita|receitas|ganhei)\\b")
private val NUMERIC_REQUEST = Regex(
    "quanto\\s+(?:eu\\s+)?(?:gastei|gasto|gastos|despesa|despesas|recebi|receita|receitas|ganhei)\\b(.*)"
)
private val PERIOD_REFERENCE = Regex(
    "\\b(?:em|no|na|de)\\s+(?:(?:janeiro|jan|fevereiro|fev|marco|mar|abril|abr|maio|mai|junho|jun|" +
        "julho|jul|agosto|ago|setembro|set|outubro|out|novembro|nov|dezembro|dez)" +
        "(?:\\s+de\\s+\\d{4}|/\\d{4})?|(?:19|20)\\d{2}-(?:0?[1-9]|1[0-2])|(?:0?[1-9]|1[0-2])/(?:19|20)\\d{2})\\b"
)
private val TOTAL_REFERENCE = Regex("\\b(?:no total|ao todo|neste mes|nesse mes|este mes|esse mes|no mes)\\b")
