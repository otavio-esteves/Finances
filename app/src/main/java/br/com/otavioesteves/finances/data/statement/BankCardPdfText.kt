package br.com.otavioesteves.finances.data.statement

/** Normalizes identified card sections before the shared strict transaction parser. */
internal object BankCardPdfText {
    fun prepare(text: String): String {
        val lines = text.replace("\u000c", "\n\u000c\n").lineSequence().toList()
        val normalized = lines.map(::normalizePdfText)
        val all = normalizePdfText(text)
        if (INTER.containsMatchIn(all) && "despesas da fatura" in normalized) return inter(lines, normalized)
        val section = when {
            "santander" in all -> Section(
                Regex("^(?:historico das despesas|detalhamento da fatura)$"),
                Regex("^(?:resumo da fatura|juros e custo efetivo)\\b"),
                Regex("^central de atendimento\\b")
            )
            "bradesco" in all -> Section(
                Regex("^lancamentos$"), Regex("^(?:total da fatura em real|total parcelados|encargos financeiros)\\b")
            )
            "banco do brasil" in all || "ourocard" in all -> Section(
                Regex("^detalhamento (?:da sua |da )?fatura$"),
                Regex("^(?:parcelamentos proxima fatura|total parcelado para proxima)\\b")
            )
            "caixa" in all -> Section(
                Regex("^demonstrativo$"), Regex("^(?:demonstrativo do programa|programa de pontos|legenda|encargos financeiros)\\b")
            )
            else -> return text
        }
        val start = normalized.indexOfFirst { section.start.matches(it) }
        if (start < 0) return text
        // Only explicit period/year metadata supplies dates; neither due dates nor today's year are guessed.
        val metadata = lines.take(start).filter { REFERENCE.containsMatchIn(normalizePdfText(it)) }
        var active = false
        var inFooter = false
        val body = lines.drop(start).filter { line ->
            val value = normalizePdfText(line)
            when {
                line.contains('\u000c') -> { inFooter = false; false }
                section.start.matches(value) -> { active = true; inFooter = false; false }
                !inFooter && section.end.containsMatchIn(value) -> { active = false; false }
                section.footer?.containsMatchIn(value) == true -> { inFooter = true; false }
                else -> active && !inFooter
            }
        }
            .map { line ->
                val value = normalizePdfText(line)
                // Labelled holder subtotals are metadata, not purchases or a pending row's amount.
                if (!line.contains('\t') && SUBTOTAL.containsMatchIn(value)) "Total " + line else line
            }
        return (listOf("Fatura") + metadata + body).joinToString("\n")
    }

    private fun inter(lines: List<String>, normalized: List<String>): String {
        val start = normalized.indexOf("despesas da fatura")
        val output = mutableListOf("Fatura")
        var pending: String? = null
        var count = 0
        val dates = PdfDateResolver(emptySequence())
        for (index in start + 1 until lines.size) {
            val line = lines[index].trim().replace('\u2212', '-').replace('\u2013', '-')
            val norm = normalizePdfText(line)
            if (INTER_END.containsMatchIn(norm)) break
            if (line.isBlank() || norm == "despesas da fatura" || INTER_HEADER.containsMatchIn(norm) || PAGE.matches(norm)) continue
            val date = INTER_DATE.find(line)
            if (date != null) {
                require(pending == null) { INCOMPLETE }
                val resolved = dates.resolve("${date.groupValues[1]} ${date.groupValues[2]} ${date.groupValues[3]}")
                pending = "$resolved " + line.substring(date.range.last + 1).trim()
                count = 1
            } else if (INTER_FX.containsMatchIn(norm)) {
                require(pending != null) { "Informação cambial fora de um lançamento Inter." }
                require(++count <= 8) { INCOMPLETE }
                continue
            } else if (pending != null) {
                require(++count <= 8) { INCOMPLETE }
                pending += " $line"
            } else {
                // Keep unknown rows so the shared parser can reject monetary content instead of losing it.
                output += line
                continue
            }
            val match = INTER_AMOUNT.matchEntire(requireNotNull(pending)) ?: continue
            val description = match.groupValues[1].trim()
            require(description.substringAfter(' ', "").isNotBlank()) { INCOMPLETE }
            // Inter's '-' before R$ separates columns. '+ R$' explicitly identifies a credit.
            val amount = match.groupValues[3]
            output += "$description ${if (match.groupValues[2].isNotEmpty()) "-" else ""}$amount"
            pending = null
        }
        require(pending == null) { INCOMPLETE }
        return output.joinToString("\n")
    }

    private data class Section(val start: Regex, val end: Regex, val footer: Regex? = null)
    private const val INCOMPLETE = "Lançamento incompleto na fatura Inter. Nenhum lançamento foi importado."
    private val INTER = Regex("\\b(?:banco inter|cartao inter|bancointer\\.com\\.br)\\b")
    private val INTER_DATE = Regex("^(\\d{1,2})\\s+de\\s+([\\p{L}]{3})\\.\\s+(\\d{4})\\s+", RegexOption.IGNORE_CASE)
    private val INTER_AMOUNT = Regex("^(.+?)\\s+-\\s*(\\+\\s*)?R\\$\\s*(\\d[\\d.]*,\\d{2})$")
    private val INTER_HEADER = Regex("^(?:cartao\\s+[\\d* .x]+|total cartao\\b.*|data movimentacao\\b.*)$")
    private val INTER_FX = Regex("^(?:valor e simbolo da moeda de origem|valor em dolar americano|cotacao do dolar americano):")
    private val INTER_END = Regex("^(?:limite de credito total|proxima fatura)\\b")
    private val PAGE = Regex("(?:pagina\\s+)?\\d+\\s*(?:/|de)\\s*\\d+")
    private val REFERENCE = Regex("^(?:ano|periodo|referencia|competencia|fatura)\\b")
    private val SUBTOTAL = Regex("^subtotal\\b")
}
