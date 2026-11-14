package br.com.otavioesteves.finances.data.statement

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/** Short dates need document metadata, never the device's current date or a neighbouring purchase. */
internal class PdfDateResolver(headerLines: Sequence<String>, fallbackYear: Int? = null) {
    private val ranges = mutableSetOf<Pair<LocalDate, LocalDate>>()
    private val years = mutableSetOf<Int>()
    private val months = mutableSetOf<Pair<Int, Int>>()

    init {
        require(fallbackYear == null || fallbackYear in 1..9999) { "Informe um ano entre 1 e 9999." }
        headerLines.forEach { original ->
            val line = normalizePdfText(original)
            if (PERIOD_LABEL.containsMatchIn(line)) {
                PERIOD.findAll(line).forEach { match ->
                    val start = fullDate(match.groupValues[1])
                    val end = fullDate(match.groupValues[2])
                    require(!end.isBefore(start)) { "Período inválido no cabeçalho do PDF." }
                    ranges += start to end
                }
            }
            YEAR_LABEL.findAll(line).forEach { years += it.groupValues[1].toInt() }
            if (MONTH_LABEL.containsMatchIn(line)) {
                MONTH_YEAR.findAll(line).forEach { months += it.groupValues[2].toInt() to it.groupValues[1].toInt() }
                NAMED_MONTH_YEAR.findAll(line).forEach { match ->
                    months += match.groupValues[2].toInt() to MONTH_NAMES.getValue(match.groupValues[1])
                }
            }
        }
        require(ranges.size <= 1 && years.size <= 1) { "O PDF contém períodos de referência ambíguos." }
        require(years.isEmpty() || months.all { it.first in years }) {
            "Ano e mês de referência incompatíveis no PDF."
        }
        if (ranges.isEmpty() && years.isEmpty() && months.isEmpty() && fallbackYear != null) years += fallbackYear
    }

    fun resolve(value: String): LocalDate {
        val normalized = normalizePdfText(value)
        if (FULL_DATE.matches(normalized)) return fullDate(normalized)
        val numeric = SHORT_DATE.matchEntire(normalized)
        val named = NAMED_DATE.matchEntire(normalized)
        val day = (numeric?.groupValues?.get(1) ?: named?.groupValues?.get(1))?.toInt()
            ?: throw IllegalArgumentException("Data inválida no PDF: $value")
        val month = numeric?.groupValues?.get(2)?.toInt() ?: MONTH_NAMES.getValue(named!!.groupValues[2])
        val explicitYear = named?.groupValues?.get(3)?.takeIf { it.isNotBlank() }?.toInt()
        if (explicitYear != null) return date(explicitYear, month, day)
        val suffixYear = numeric?.groupValues?.getOrNull(3)?.takeIf { it.isNotBlank() }?.toInt()
        val range = ranges.singleOrNull()
        val candidateYears = when {
            range != null -> (range.first.year..range.second.year).toList()
            years.isNotEmpty() -> years.toList()
            else -> months.filter { it.second == month }.map { it.first }.distinct()
        }
        val candidates = candidateYears.filter { suffixYear == null || it % 100 == suffixYear }.mapNotNull { year -> runCatching { date(year, month, day) }.getOrNull() }
            .filter { range == null || (!it.isBefore(range.first) && !it.isAfter(range.second)) }
        require(candidates.size == 1) {
            "A data $value não tem um ano inequívoco no PDF. Informe o ano das compras na importação ou use um arquivo com datas completas."
        }
        val resolved = candidates.single()
        require(years.isEmpty() || resolved.year in years) { "Ano e período de referência incompatíveis no PDF." }
        return resolved
    }

    private fun fullDate(value: String): LocalDate {
        val parts = value.split('/', '-', '.')
        return if (parts[0].length == 4) date(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            else date(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
    }

    private fun date(year: Int, month: Int, day: Int): LocalDate = try {
        require(year in 1..9999)
        LocalDate.of(year, month, day)
    } catch (error: RuntimeException) {
        throw IllegalArgumentException("Data inválida no PDF: $day/$month/$year", error)
    }

    companion object {
        private val MONTH_NAMES = mapOf(
            "jan" to 1, "janeiro" to 1, "fev" to 2, "fevereiro" to 2, "mar" to 3, "marco" to 3,
            "abr" to 4, "abril" to 4, "mai" to 5, "maio" to 5, "jun" to 6, "junho" to 6,
            "jul" to 7, "julho" to 7, "ago" to 8, "agosto" to 8, "set" to 9, "setembro" to 9,
            "out" to 10, "outubro" to 10, "nov" to 11, "novembro" to 11, "dez" to 12, "dezembro" to 12
        )
        private val MONTH_PATTERN = MONTH_NAMES.keys.sortedByDescending { it.length }.joinToString("|")
        private const val FULL_PATTERN = "(?:\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[./-]\\d{1,2}[./-]\\d{4})"
        private val FULL_DATE = Regex(FULL_PATTERN)
        private val SHORT_DATE = Regex("(\\d{1,2})[./-](\\d{1,2})(?:[./-](\\d{2}))?")
        private val NAMED_DATE = Regex("(\\d{1,2})\\s+($MONTH_PATTERN)\\.?(?:\\s+(\\d{4}))?")
        private val PERIOD_LABEL = Regex("\\b(?:periodo|extrato|movimentacao)\\b")
        private val PERIOD = Regex("($FULL_PATTERN)\\s*(?:a|ate|-)\\s*($FULL_PATTERN)")
        private val YEAR_LABEL = Regex("\\bano\\s*(?:de|:)?\\s*((?:19|20)\\d{2})\\b")
        private val MONTH_LABEL = Regex("\\b(?:fatura|competencia|referencia|mes)\\b")
        private val MONTH_YEAR = Regex("(?<![\\d/])(0?[1-9]|1[0-2])/(\\d{4})\\b")
        private val NAMED_MONTH_YEAR = Regex("\\b($MONTH_PATTERN)\\s*(?:/|de\\s+|\\s)(\\d{4})\\b")
        val DATE_PREFIX = Regex("^($FULL_PATTERN|\\d{1,2}[./-]\\d{1,2}[./-]\\d{2}|\\d{1,2}[./-]\\d{1,2}(?![\\d./-])|\\d{1,2}\\s+(?:$MONTH_PATTERN)\\.?(?:\\s+\\d{4})?)(?=\\s|$)", RegexOption.IGNORE_CASE)
        // Recognizable but unsupported date layouts must reject the batch, not disappear from it.
        val DATE_LIKE_PREFIX = Regex("^\\d{1,4}\\s*[./-]\\s*\\d{1,2}|^\\d{1,2}\\s+[\\p{L}]{3,}\\.?")
    }
}

private val PDF_DIACRITICS = Regex("\\p{M}+")
private val PDF_WHITESPACE = Regex("\\s+")

internal fun normalizePdfText(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(PDF_DIACRITICS, "").replace('\u00a0', ' ').replace(PDF_WHITESPACE, " ").trim()
