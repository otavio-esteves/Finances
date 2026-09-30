package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.MonthPeriod
import java.text.Normalizer

/** Prevents a current-month summary from being presented as an answer for another period. */
internal fun canAnswerWithCurrentMonth(message: String, period: MonthPeriod): Boolean {
    val text = Normalizer.normalize(message.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

    if (OTHER_PERIOD.containsMatchIn(text)) return false
    if (FULL_DATE.containsMatchIn(text)) return false

    val mentionedMonths = MONTH_NAMES.mapIndexedNotNull { index, names ->
        if (names.any { Regex("\\b$it\\b").containsMatchIn(text) }) index + 1 else null
    } + MONTH_YEAR.findAll(text).map { it.groupValues[1].toInt() }.toList() +
        YEAR_MONTH.findAll(text).map { it.groupValues[1].toInt() }.toList()
    if (mentionedMonths.any { it != period.month }) return false

    val mentionedYears = YEAR.findAll(text).map { it.value.toInt() }.toList()
    if (mentionedYears.any { it != period.year }) return false
    if (mentionedYears.isNotEmpty() && mentionedMonths.isEmpty()) return false

    return true
}

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
private val OTHER_PERIOD = Regex(
    "\\b(?:ontem|anteontem|semana|trimestre|semestre|ano|anos|dia|dias|" +
        "ultim[oa]s?|passad[oa]s?|retrasad[oa]s?|anteriores?|proxim[oa]s?)\\b"
)
