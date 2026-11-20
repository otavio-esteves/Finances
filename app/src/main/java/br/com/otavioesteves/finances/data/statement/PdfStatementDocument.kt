package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.model.isCardPaymentDescription
import br.com.otavioesteves.finances.utils.MoneyFormatter

/** Recognized document structures separate current movements from payment simulations and legal notices. */
internal class PdfStatementDocument(original: String, cardContext: Boolean) {
    private val normalized = normalizePdfText(original)
    private val summaryLines = original.lineSequence().map(::normalizePdfText)
        .filter { it.startsWith("entradas:") || it.startsWith("saidas:") }.toList()
    val unsignedCredits = original.lineSequence().any { normalizePdfText(it) == "detalhe dos movimentos" } && "entradas:" in normalized &&
        "saidas:" in normalized
    val text: String
    private var closingDate: java.time.LocalDate? = null
    private data class SectionTotal(val start: Int, val count: Int, val signedCents: Long, val excludeTransfers: Boolean = false)
    private val billSectionTotals = mutableListOf<SectionTotal>()
    private var billMovementCount: Int? = null

    init {
        val lines = original.lineSequence().toList()
        val transactionStart = lines.indexOfFirst { TRANSACTIONS.containsMatchIn(normalizePdfText(it)) }
        text = if (cardContext && transactionStart >= 0 && EMISSION.containsMatchIn(normalized)) {
            requireNoDatedCoverRows(lines.take(transactionStart))
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
            "Fatura\nPeríodo: $start a $end\n" + billMovements(body, "nubank" in normalized || "nu pagamentos" in normalized)
        } else if (cardContext && "picpay" in normalized && lines.any { PICPAY_SECTION_PREFIX.containsMatchIn(normalizePdfText(it)) }
        ) {
            val closings = CLOSING.findAll(normalized).map { it.groupValues[1] }.toSet()
            require(closings.size == 1) { "A fatura não tem uma data de fechamento inequívoca." }
            val closing = PdfDateResolver(emptySequence()).resolve(closings.single())
            // This layout supplies a closing date rather than a year on each purchase. Older dates need an explicit year.
            closingDate = closing
            val start = closing.minusMonths(1).withDayOfMonth(1)
            "Fatura\nPeríodo: $start a $closing\n" + picpayMovements(original)
        } else if (unsignedCredits && lines.any(::isMovementTableHeader)) {
            // This identified account layout reconciles both movement totals. Cover balances are not movements.
            val heading = lines.indexOfFirst { normalizePdfText(it) == "detalhe dos movimentos" }
            val prefix = lines.take(heading)
            requireNoDatedCoverRows(prefix)
            val reference = prefix.filter { REFERENCE.containsMatchIn(normalizePdfText(it)) }
            (reference + lines.drop(heading)).joinToString("\n")
        } else if ("extrato da conta" in normalized && lines.any(::isBeneficiaryTableHeader)) {
            // Account identifiers in the cover are not transaction dates; this table has complete dates on every row.
            val heading = lines.indexOfFirst(::isBeneficiaryTableHeader)
            requireNoDatedCoverRows(lines.take(heading))
            lines.drop(heading).joinToString("\n")
        } else original
    }

    private fun billMovements(lines: List<String>, hasBankMarker: Boolean): String {
        val body = mutableListOf<String>()
        val first = lines.indexOfFirst { it.isNotBlank() }
        var count = 0
        var sectionStart = 0
        var expected: Long? = null
        fun finishSection() {
            expected?.let {
                require(count > sectionStart) { "Subtotal sem lançamentos na fatura. Nenhum lançamento foi importado." }
                billSectionTotals += SectionTotal(sectionStart, count - sectionStart, it)
            }
        }
        lines.forEachIndexed { index, line ->
            val normalizedLine = normalizePdfText(line.replace('\u2212', '-').replace('\u2013', '-'))
            val named = NAMED_BILL_SECTION_TOTAL.matches(normalizedLine)
            val firstHolder = hasBankMarker && index == first && HOLDER_TOTAL.matches(normalizedLine) &&
                lines.drop(index + 1).firstOrNull { it.isNotBlank() }?.let {
                    PdfDateResolver.DATE_PREFIX.containsMatchIn(normalizePdfText(it))
                } == true
            if (named || firstHolder) {
                finishSection()
                val amount = requireNotNull(SECTION_AMOUNT.find(normalizedLine)) { "Subtotal inválido na fatura." }.value
                    .replace("r$", "").replace(" ", "")
                expected = requireNotNull(MoneyFormatter.parse(amount)) { "Subtotal inválido na fatura." }.cents
                sectionStart = count
            } else {
                if (PdfDateResolver.DATE_PREFIX.containsMatchIn(normalizedLine)) count++
                body += line
            }
        }
        finishSection()
        if (billSectionTotals.isNotEmpty()) billMovementCount = count
        return body.joinToString("\n")
    }

    private fun picpayMovements(original: String): String {
        val body = mutableListOf<String>()
        var active = false
        var futureSection = false
        var count = 0
        var sectionStart = 0
        for (page in original.split('\u000c')) {
            val pageLines = page.lines()
            val normalizedLines = pageLines.map(::normalizePdfText)
            require(normalizedLines.none {
                PICPAY_SECTION_PREFIX.containsMatchIn(it) && !PICPAY_SECTION.matches(it)
            }) { "Seção de lançamentos não reconhecida na fatura PicPay. Nenhum lançamento foi importado." }
            var firstLine = 0
            if (!active && !futureSection) {
                val sectionStart = normalizedLines.indexOfFirst { PICPAY_SECTION.matches(it) }
                if (sectionStart < 0) {
                    val future = normalizedLines.indexOfFirst { PICPAY_FUTURE_SECTION.containsMatchIn(it) }
                    requireNoDatedCoverRows(pageLines.take(if (future < 0) pageLines.size else future))
                    continue
                }
                val prefix = normalizedLines.take(sectionStart)
                val futureSummary = prefix.indexOfFirst { PICPAY_FUTURE_SECTION.containsMatchIn(it) }
                if (futureSummary >= 0) {
                    requireNoDatedCoverRows(pageLines.take(futureSummary))
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
                if (active) {
                    if (PICPAY_SECTION.matches(normalizedLine)) sectionStart = count
                    val summary = PICPAY_TOTAL.matchEntire(normalizedLine)
                    if (summary != null) {
                        val total = requireNotNull(MoneyFormatter.parse(summary.groupValues[2])).cents
                        val start = if (summary.groupValues[1].startsWith("total geral")) 0 else sectionStart
                        require(count > start) { "Subtotal sem lançamentos na fatura." }
                        billSectionTotals += SectionTotal(start, count - start, total, excludeTransfers = true)
                        sectionStart = count
                        body += "Subtotal dos lançamentos ${summary.groupValues[2]}"
                    } else {
                        if (PdfDateResolver.DATE_PREFIX.containsMatchIn(normalizedLine)) count++
                        body += line
                    }
                }
            }
        }
        if (billSectionTotals.isNotEmpty()) billMovementCount = count
        return body.joinToString("\n")
    }

    fun isMovementSummary(line: String): Boolean = unsignedCredits && normalizePdfText(line) in summaryLines

    fun isMovementSectionHeading(line: String): Boolean = normalizePdfText(line).let {
        PICPAY_SECTION.matches(it) || it == "detalhe dos movimentos"
    }

    fun validateTotals(entries: List<RawStatementEntry>) {
        billMovementCount?.let { require(entries.size == it) { "A quantidade de lançamentos não confere com as seções da fatura." } }
        billSectionTotals.forEach { section ->
            val total = try {
                entries.subList(section.start, section.start + section.count).fold(0L) { sum, entry ->
                    Math.addExact(sum, when {
                        section.excludeTransfers && (entry.type == TransactionType.TRANSFER ||
                            (entry.type == TransactionType.INCOME && isCardPaymentDescription(entry.description, includeBankLabels = true, includeDatedPayments = true))) -> 0L
                        entry.type == TransactionType.EXPENSE -> entry.amount.cents
                        else -> -entry.amount.cents
                    })
                }
            } catch (error: ArithmeticException) {
                throw IllegalArgumentException("O subtotal da fatura excede o limite suportado.", error)
            }
            require(total == section.signedCents) { "Os lançamentos não conferem com o subtotal da seção da fatura. Nenhum lançamento foi importado." }
        }
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
        private val PICPAY_TOTAL = Regex("^(subtotal dos lancamentos|total geral dos lancamentos)\\s+(?:r\\$\\s*)?([+-]?[\\d.]+,\\d{2})$")
        private val PICPAY_SECTION_PREFIX = Regex("^transacoes\\s+(?:nacionais|internacionais)\\b")
        private val PICPAY_SECTION = Regex("transacoes\\s+(?:nacionais|internacionais)(?:\\s*[-–—:]?\\s*\\(?continuacao\\)?)?\\s*[:.]?")
        private val PICPAY_FUTURE_SECTION = Regex(
            "^(?:(?:total parcelado|parcelamentos?|compras|lancamentos)\\s*(?:[-–—:]\\s*)?" +
                "(?:(?:para|d[ae]s?|nas?)\\s+)?(?:as?\\s+)?)?(?:proximas? faturas?|faturas? futuras?)\\b"
        )
        private fun isBeneficiaryTableHeader(line: String): Boolean = line.startsWith("data\t") &&
            line.split('\t').containsAll(listOf("descricao", "favorecido", "identificador", "valor", "saldo"))
        private fun isMovementTableHeader(line: String): Boolean = line.split('\t').map(PdfCurrencyPolicy::columnName).let {
            "data" in it && "descricao" in it && "valor" in it
        }
        private val REFERENCE = Regex("^(?:periodo|referencia|competencia|ano)\\b")
        private val HOLDER_TOTAL = Regex("[\\p{L} .'-]+\\s+[+-]?\\s*r\\$\\s*[+-]?\\s*[\\d.]+,\\d{2}")
        private val SECTION_AMOUNT = Regex("[+-]?\\s*(?:r\\$\\s*)?[+-]?\\s*[\\d.]+,\\d{2}$")
        private val TRANSACTIONS = Regex("^transacoes\\s+de\\s+(\\d{1,2}\\s+[a-z]{3})\\s+a\\s+(\\d{1,2}\\s+[a-z]{3})$")
        private val EMISSION = Regex("\\bemissao e envio\\s+(\\d{1,2}\\s+[a-z]{3}\\s+\\d{4})\\b")
        private val CLOSING = Regex("\\bfechamento:\\s*(\\d{1,2}/\\d{1,2}/\\d{4})\\b")
        private val NAMED_BILL_SECTION_TOTAL = Regex("(?:cartao titular|cartao adicional|pagamentos)\\s+[+-]?\\s*(?:r\\$\\s*)?[+-]?[\\d.]+,\\d{2}")
    }
}

// Reject ambiguous pre-section rows even when they are zero: amount reconciliation cannot detect their loss.
// The numeric boundary excludes Brazilian thousands groups (1.234,56) from date candidates.
private val COVER_NUMERIC_DATE = Regex("^\\d{1,4}\\s*[./-]\\s*\\d{1,2}(?!\\d)")
private val COVER_DATE_WITH_DE = Regex("^(\\d{1,2})\\s+de\\s+")
internal fun requireNoDatedCoverRows(lines: List<String>) {
    require(lines.none { line ->
        val normalized = normalizePdfText(line)
        COVER_NUMERIC_DATE.containsMatchIn(normalized) ||
            PdfDateResolver.DATE_PREFIX.containsMatchIn(normalized.replaceFirst(COVER_DATE_WITH_DE, "$1 "))
    }) { "Há linhas com data antes da seção de movimentações. O PDF está ambíguo; nenhum lançamento foi importado. Confira o documento ou exporte CSV/OFX." }
}
