package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.utils.MoneyFormatter

/** Recognized document structures separate current movements from payment simulations and legal notices. */
internal class PdfStatementDocument(original: String) {
    private val normalized = normalizePdfText(original)
    private val summaryLines = original.lineSequence().map(::normalizePdfText)
        .filter { it.startsWith("entradas:") || it.startsWith("saidas:") }.toList()
    val unsignedCredits = "detalhe dos movimentos" in normalized && "entradas:" in normalized &&
        "saidas:" in normalized
    val text: String
    private var closingDate: java.time.LocalDate? = null

    init {
        val lines = original.lineSequence().toList()
        val transactionStart = lines.indexOfFirst { TRANSACTIONS.containsMatchIn(normalizePdfText(it)) }
        text = if (transactionStart >= 0 && EMISSION.containsMatchIn(normalized)) {
            val emissions = EMISSION.findAll(normalized).map { it.groupValues[1] }.toSet()
            require(emissions.size == 1) { "O PDF contém datas de emissão ambíguas." }
            val dates = PdfDateResolver(emptySequence())
            val emission = dates.resolve(emissions.single())
            val range = requireNotNull(TRANSACTIONS.find(normalizePdfText(lines[transactionStart])))
            var end = dates.resolve("${range.groupValues[2]} ${emission.year}")
            if (end.isAfter(emission)) end = end.minusYears(1)
            var start = dates.resolve("${range.groupValues[1]} ${end.year}")
            if (start.isAfter(end)) start = start.minusYears(1)
            require(!end.isBefore(emission.minusMonths(1)) && !start.isBefore(end.minusMonths(2))) {
                "Período da fatura incompatível com a emissão."
            }
            val body = lines.drop(transactionStart + 1)
                .takeWhile { !normalizePdfText(it).startsWith("em cumprimento a regulacao") }
                .filterNot { EMISSION.containsMatchIn(normalizePdfText(it)) }
                // These exact section totals belong to this identified bill layout, not purchases.
                .filterNot { NAMED_BILL_SECTION_TOTAL.matches(normalizePdfText(it)) }
            "Fatura\nPeríodo: $start a $end\n" + body.joinToString("\n")
        } else if ("picpay" in normalized && lines.any { PICPAY_SECTION_PREFIX.containsMatchIn(normalizePdfText(it)) }
        ) {
            val closings = CLOSING.findAll(normalized).map { it.groupValues[1] }.toSet()
            require(closings.size == 1) { "A fatura não tem uma data de fechamento inequívoca." }
            val closing = PdfDateResolver(emptySequence()).resolve(closings.single())
            // This layout supplies a closing date rather than a year on each purchase. Older dates need an explicit year.
            closingDate = closing
            val start = closing.minusMonths(1).withDayOfMonth(1)
            "Fatura\nPeríodo: $start a $closing\n" + picpayMovements(original)
        } else if ("extrato da conta" in normalized && lines.any(::isBeneficiaryTableHeader)) {
            // Account identifiers in the cover are not transaction dates; this table has complete dates on every row.
            lines.dropWhile { !isBeneficiaryTableHeader(it) }.joinToString("\n")
        } else original
    }

    private fun picpayMovements(original: String): String {
        val body = mutableListOf<String>()
        var active = false
        var futureSection = false
        for (page in original.split('\u000c')) {
            val pageLines = page.lines()
            val normalizedLines = pageLines.map(::normalizePdfText)
            require(normalizedLines.none {
                PICPAY_SECTION_PREFIX.containsMatchIn(it) && !PICPAY_SECTION.matches(it)
            }) { "Seção de lançamentos não reconhecida na fatura PicPay. Nenhum lançamento foi importado." }
            var firstLine = 0
            if (!active && !futureSection) {
                val sectionStart = normalizedLines.indexOfFirst { PICPAY_SECTION.matches(it) }
                if (sectionStart < 0) continue
                val prefix = normalizedLines.take(sectionStart)
                val futureSummary = prefix.indexOfFirst { PICPAY_FUTURE_SECTION.containsMatchIn(it) }
                if (futureSummary >= 0) {
                    require(prefix.take(futureSummary).none { PdfDateResolver.DATE_LIKE_PREFIX.containsMatchIn(it) }) {
                        "As seções de lançamentos atuais e futuros da fatura estão ambíguas. Nenhum lançamento foi importado."
                    }
                    // A cover summary can share the page with the first current section.
                    firstLine = sectionStart
                }
                // Without a cover summary, preserve purchases above the first repeated header.
                active = true
            }
            for (line in pageLines.drop(firstLine)) {
                val normalizedLine = normalizePdfText(line)
                if (PICPAY_FUTURE_SECTION.containsMatchIn(normalizedLine)) {
                    active = false
                    futureSection = true
                }
                require(!futureSection || !PICPAY_SECTION.matches(normalizedLine)) {
                    "As seções de lançamentos atuais e futuros da fatura estão ambíguas. Nenhum lançamento foi importado."
                }
                if (active) body += line
            }
        }
        return body.joinToString("\n")
    }

    fun isMovementSummary(line: String): Boolean = unsignedCredits && normalizePdfText(line) in summaryLines

    fun validateTotals(entries: List<RawStatementEntry>) {
        closingDate?.let { closing ->
            require(entries.none { it.date.isAfter(closing) }) { "Há lançamentos posteriores ao fechamento da fatura. Nenhum lançamento foi importado." }
        }
        if (!unsignedCredits) return
        for ((label, type) in listOf("entradas" to TransactionType.INCOME, "saidas" to TransactionType.EXPENSE)) {
            val pattern = Regex("^$label:\\s*r\\$\\s*-?\\s*([\\d.]+,\\d{2})")
            val totals = summaryLines.asSequence().flatMap { pattern.findAll(it) }
                .map { requireNotNull(MoneyFormatter.parse(it.groupValues[1])).cents }.toSet()
            require(totals.size == 1) { "Totais de entradas/saídas ambíguos no extrato PDF." }
            val sum = entries.filter { it.type == type }.fold(0L) { total, entry -> Math.addExact(total, entry.amount.cents) }
            require(sum == totals.single()) { "Os lançamentos reconhecidos não conferem com o total de $label do PDF. Nenhum lançamento foi importado." }
        }
    }

    companion object {
        private val PICPAY_SECTION_PREFIX = Regex("^transacoes\\s+(?:nacionais|internacionais)\\b")
        private val PICPAY_SECTION = Regex("transacoes\\s+(?:nacionais|internacionais)(?:\\s*[-–—:]?\\s*\\(?continuacao\\)?)?\\s*[:.]?")
        private val PICPAY_FUTURE_SECTION = Regex(
            "^(?:(?:total parcelado|parcelamentos?|compras|lancamentos)\\s*(?:[-–—:]\\s*)?" +
                "(?:(?:para|d[ae]s?|nas?)\\s+)?(?:as?\\s+)?)?(?:proximas? faturas?|faturas? futuras?)\\b"
        )
        private fun isBeneficiaryTableHeader(line: String): Boolean = line.startsWith("data\t") &&
            line.split('\t').containsAll(listOf("descricao", "favorecido", "identificador", "valor", "saldo"))
        private val TRANSACTIONS = Regex("^transacoes\\s+de\\s+(\\d{1,2}\\s+[a-z]{3})\\s+a\\s+(\\d{1,2}\\s+[a-z]{3})$")
        private val EMISSION = Regex("\\bemissao e envio\\s+(\\d{1,2}\\s+[a-z]{3}\\s+\\d{4})\\b")
        private val CLOSING = Regex("\\bfechamento:\\s*(\\d{1,2}/\\d{1,2}/\\d{4})\\b")
        private val NAMED_BILL_SECTION_TOTAL = Regex("(?:cartao titular|cartao adicional|pagamentos)\\s+[+-]?\\s*(?:r\\$\\s*)?[+-]?[\\d.]+,\\d{2}")
    }
}
