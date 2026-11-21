package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.MonthPeriod
import java.text.Normalizer

/** Resolves one calendar month; ambiguous ranges and day-level questions need a different query. */
internal fun resolveChatPeriod(message: String, current: MonthPeriod): MonthPeriod? {
    val text = Normalizer.normalize(message.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

    if (UNSUPPORTED_PERIOD.containsMatchIn(text) || FULL_DATE.containsMatchIn(text) || MALFORMED_NUMERIC_PERIOD.containsMatchIn(text) ||
        NAMED_YEAR.findAll(text).any { it.groupValues[1].length != 4 }) return null

    val namedMonths = MONTH_NAMES.mapIndexedNotNull { index, names ->
        (index + 1).takeIf { names.any { Regex("\\b$it\\b").containsMatchIn(text) } }
    }
    val numericMonths = MONTH_YEAR.findAll(text).map { it.groupValues[1].toInt() }.toList() +
        YEAR_MONTH.findAll(text).map { it.groupValues[1].toInt() }.toList()
    val months = (namedMonths + numericMonths).distinct()
    val years = YEAR.findAll(text).map { it.value.toInt() }.distinct().toList()
    if (months.any { it !in 1..12 } || 0 in years) return null
    if (months.size > 1 || years.size > 1) return null
    if (years.isNotEmpty() && months.isEmpty()) return null

    if (months.isEmpty()) return current
    val month = months.single()
    val year = years.singleOrNull() ?: if (month > current.month) current.year - 1 else current.year
    return year.takeIf { it >= 1 }?.let { MonthPeriod(it, month) }
}

internal fun canAnswerWithCurrentMonth(message: String, period: MonthPeriod): Boolean =
    resolveChatPeriod(message, period) == period

internal fun removeChatPeriodReference(text: String): String = PERIOD_REFERENCE.replace(text, " ")

private val MONTH_NAMES = listOf(
    listOf("janeiro", "jan"), listOf("fevereiro", "fev"), listOf("marco", "mar"),
    listOf("abril", "abr"), listOf("maio", "mai"), listOf("junho", "jun"),
    listOf("julho", "jul"), listOf("agosto", "ago"), listOf("setembro", "set"),
    listOf("outubro", "out"), listOf("novembro", "nov"), listOf("dezembro", "dez")
)
private const val YEAR_NUMBER = "[0-9]{4}"
private const val MONTH_NUMBER = "[0-9]{1,2}"
private val MONTH_NAME = MONTH_NAMES.flatten().joinToString("|", "(?:", ")")
private val YEAR = Regex("\\b$YEAR_NUMBER\\b")
private val NAMED_YEAR = Regex("\\b$MONTH_NAME(?:\\s+de\\s+|/)([0-9]+)\\b")
private val MONTH_YEAR = Regex("\\b($MONTH_NUMBER)/$YEAR_NUMBER\\b")
private val YEAR_MONTH = Regex("\\b$YEAR_NUMBER-($MONTH_NUMBER)\\b")
private val FULL_DATE = Regex("\\b(?:$MONTH_NUMBER/$MONTH_NUMBER/$YEAR_NUMBER|$YEAR_NUMBER-$MONTH_NUMBER-$MONTH_NUMBER)\\b")
private val MALFORMED_NUMERIC_PERIOD = Regex(
    "\\b(?:[0-9]{3,}/$YEAR_NUMBER|$YEAR_NUMBER-[0-9]{3,}|$MONTH_NUMBER/[0-9]{5,}|[0-9]{5,}-$MONTH_NUMBER)\\b"
)
private val PERIOD_REFERENCE = Regex(
    "\\b(?:em|no|na|de)\\s+(?:$MONTH_NAME(?:(?:\\s+de\\s+|/)$YEAR_NUMBER)?|" +
        "$YEAR_NUMBER-$MONTH_NUMBER|$MONTH_NUMBER/$YEAR_NUMBER)\\b"
)
private val UNSUPPORTED_PERIOD = Regex(
    "\\b(?:ontem|anteontem|semana|trimestre|semestre|ano|anos|dia|dias|" +
        "ultim[oa]s?|passad[oa]s?|retrasad[oa]s?|anterior(?:es)?|proxim[oa]s?|" +
        "seguinte(?:s)?|futur[oa]s?|amanha|hoje|atras|vem)\\b"
)
