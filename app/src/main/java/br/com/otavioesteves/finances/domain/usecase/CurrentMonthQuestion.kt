package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.MonthPeriod
import java.text.Normalizer

/** Resolves one calendar month; ambiguous ranges and day-level questions need a different query. */
internal fun resolveChatPeriod(message: String, current: MonthPeriod): MonthPeriod? {
    val text = Normalizer.normalize(message.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

    if (UNSUPPORTED_PERIOD.containsMatchIn(text) || FULL_DATE.containsMatchIn(text)) return null

    val namedMonths = MONTH_NAMES.mapIndexedNotNull { index, names ->
        (index + 1).takeIf { names.any { Regex("\\b$it\\b").containsMatchIn(text) } }
    }
    val numericMonths = MONTH_YEAR.findAll(text).map { it.groupValues[1].toInt() }.toList() +
        YEAR_MONTH.findAll(text).map { it.groupValues[1].toInt() }.toList()
    val months = (namedMonths + numericMonths).distinct()
    val years = YEAR.findAll(text).map { it.value.toInt() }.distinct().toList()
    if (months.size > 1 || years.size > 1) return null
    if (years.isNotEmpty() && months.isEmpty()) return null

    if (months.isEmpty()) return current
    val month = months.single()
    val year = years.singleOrNull() ?: if (month > current.month) current.year - 1 else current.year
    return year.takeIf { it >= 1 }?.let { MonthPeriod(it, month) }
}

internal fun canAnswerWithCurrentMonth(message: String, period: MonthPeriod): Boolean =
    resolveChatPeriod(message, period) == period

private val MONTH_NAMES = listOf(
    listOf("janeiro", "jan"), listOf("fevereiro", "fev"), listOf("marco", "mar"),
    listOf("abril", "abr"), listOf("maio", "mai"), listOf("junho", "jun"),
    listOf("julho", "jul"), listOf("agosto", "ago"), listOf("setembro", "set"),
    listOf("outubro", "out"), listOf("novembro", "nov"), listOf("dezembro", "dez")
)
private val YEAR = Regex("\\b(?:19|20)\\d{2}\\b")
private val MONTH_YEAR = Regex("\\b(0?[1-9]|1[0-2])/(?:19|20)\\d{2}\\b")
private val YEAR_MONTH = Regex("\\b(?:19|20)\\d{2}-(0?[1-9]|1[0-2])\\b")
private val FULL_DATE = Regex("\\b(?:\\d{1,2}/\\d{1,2}/(?:19|20)\\d{2}|(?:19|20)\\d{2}-\\d{1,2}-\\d{1,2})\\b")
private val UNSUPPORTED_PERIOD = Regex(
    "\\b(?:ontem|anteontem|semana|trimestre|semestre|ano|anos|dia|dias|" +
        "ultim[oa]s?|passad[oa]s?|retrasad[oa]s?|anterior(?:es)?|proxim[oa]s?|" +
        "seguinte(?:s)?|futur[oa]s?|amanha|hoje|atras|vem)\\b"
)
