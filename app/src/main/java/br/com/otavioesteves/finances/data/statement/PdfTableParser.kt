package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.TransactionType

/** Parses positioned cells; an empty debit cell is different from a missing/guessed column. */
internal class PdfTableParser(
    private val dates: PdfDateResolver,
    private val parseAmount: (String, Boolean, Boolean) -> ParsedPdfAmount,
    private val cardBill: Boolean,
    private val currencyPolicy: PdfCurrencyPolicy = PdfCurrencyPolicy("")
) {
    data class Result(
        val handled: Boolean,
        val entry: RawStatementEntry? = null,
        val continuation: String? = null,
        val boundary: Boolean = false,
        val rowLines: Int = 1
    )
    private var header = emptyList<String>()
    private var brlColumns = emptySet<Int>()
    private var pending: MutableList<String>? = null
    private var pendingLines = 0
    val hasPending get() = pending != null

    fun process(line: String): Result {
        if (!line.contains('\t')) {
            if (pending == null) return Result(false)
            require(!PdfDateResolver.DATE_LIKE_PREFIX.containsMatchIn(normalizePdfText(line))) { "Data não reconhecida na tabela PDF." }
            val description = descriptionIndex()
            pending!![description] = (pending!![description] + " " + line).trim()
            require(++pendingLines <= 6) { "Lançamento incompleto na tabela PDF." }
            return Result(true)
        }
        val cells = line.split('\t').map { it.trim() }
        val normalized = cells.map(PdfCurrencyPolicy::columnName)
        if (normalized.any { it.startsWith("data") } && normalized.any { it in DESCRIPTION } &&
            normalized.any { it in listOf("valor", "debito", "credito") }
        ) {
            require(pending == null || normalized == header) { "As colunas do PDF mudaram em um lançamento incompleto." }
            val repeatedHeader = normalized == header
            header = normalized
            brlColumns = cells.indices.filter { PdfCurrencyPolicy.hasBrlMarker(cells[it]) }.toSet()
            dateIndex(); descriptionIndex()
            require(header.count { it in DESCRIPTION } == 1) { "Coluna de descrição ambígua no PDF." }
            require(header.count { it in listOf("data movimento", "data mov") } <= 1) { "Data de movimento ambígua no PDF." }
            require(header.count { it == "valor" } <= 1 && header.count { it == "debito" } <= 1 && header.count { it == "credito" } <= 1)
            require(header.count { it == "moeda" } <= 1) { "Coluna de moeda ambígua no PDF. Nenhum lançamento foi importado." }
            require(("debito" in header) == ("credito" in header)) { "Tabela de débito/crédito incompleta." }
            require(("valor" in header) != ("debito" in header)) { "Colunas monetárias ambíguas no PDF." }
            return Result(true, boundary = !repeatedHeader)
        }
        if (header.isEmpty()) return Result(false)
        require(cells.size == header.size) { "Linha do PDF não corresponde às colunas identificadas." }
        val date = cells[dateIndex()]
        val dateOnly = DATE_WITH_TIME.matchEntire(date)?.groupValues?.get(1) ?: date
        val description = cells[descriptionIndex()]
        val normalizedDescription = normalizePdfText(description)
        val hasAmount = cells.indices.any { header[it] in MONEY_COLUMNS && cells[it].isNotBlank() }
        if (SUMMARY.matches(normalizedDescription) &&
            ((date.isBlank() && hasAmount) || (date.isNotBlank() && normalizedDescription != "total"))) {
            return Result(true, boundary = true)
        }
        if (date.isNotBlank()) {
            require(pending == null) { "Lançamento anterior incompleto na tabela PDF." }
            require(PdfDateResolver.DATE_PREFIX.matchEntire(normalizePdfText(dateOnly)) != null) { "Data inválida na tabela PDF: $date" }
            pending = cells.toMutableList(); pendingLines = 1
        } else if (pending != null) {
            require(++pendingLines <= 6) { "Lançamento incompleto na tabela PDF." }
            cells.forEachIndexed { index, value ->
                if (value.isNotBlank()) {
                    if (index == descriptionIndex()) pending!![index] = (pending!![index] + " " + value).trim()
                    else {
                        require(pending!![index].isBlank()) { "Valor repetido ou ambíguo no PDF." }
                        pending!![index] = value
                    }
                }
            }
        } else {
            require(cells.indices.all { it == descriptionIndex() || cells[it].isBlank() }) {
                "Linha sem data ou colunas ambíguas na tabela PDF. Nenhum lançamento foi importado."
            }
            return Result(true, continuation = description.takeIf { it.isNotBlank() })
        }
        val row = requireNotNull(pending)
        val currency = header.indexOf("moeda")
        val explicitBrlValue = header.indexOf("valor") in brlColumns
        require(currency < 0 || row[currency].isBlank() || row[currency].uppercase() in listOf("R$", "BRL") ||
            (explicitBrlValue && "valor estrangeiro" in header)) {
            "Lançamento em moeda estrangeira sem valor convertido em reais."
        }
        val foreign = header.indexOf("valor estrangeiro")
        if (foreign >= 0 && row[foreign].isNotBlank()) parseAmount(row[foreign], false, false)
        val valueIndex = header.indexOf("valor")
        val amount: ParsedPdfAmount = if (valueIndex >= 0) {
            if (row[valueIndex].isBlank()) return Result(true, boundary = true)
            currencyPolicy.requireBrlValue(row[valueIndex], valueIndex in brlColumns, row.getOrNull(currency))
            val marker = header.indexOf("tipo").takeIf { it >= 0 }?.let { row[it] }.orEmpty()
            parseAmount((row[valueIndex] + " " + marker).trim(), cardBill, true)
        } else {
            val debit = row[header.indexOf("debito")]
            val credit = row[header.indexOf("credito")]
            if (debit.isBlank() && credit.isBlank()) return Result(true, boundary = true)
            fun value(raw: String, type: TransactionType): Long? {
                if (raw.isBlank() || raw == "-" || raw == "—") return null
                val column = header.indexOf(if (type == TransactionType.EXPENSE) "debito" else "credito")
                currencyPolicy.requireBrlValue(raw, column in brlColumns, row.getOrNull(currency))
                val parsed = parseAmount(raw, false, false)
                val explicit = EXPLICIT_DIRECTION.containsMatchIn(normalizePdfText(raw))
                require(!explicit || parsed.type == type) { "O sinal não corresponde à coluna de débito/crédito." }
                return parsed.cents
            }
            val debitValue = value(debit, TransactionType.EXPENSE)
            val creditValue = value(credit, TransactionType.INCOME)
            require(debitValue != 0L || creditValue != 0L) { "Lançamento zero sem indicação inequívoca de débito/crédito." }
            require(debitValue == null || debitValue == 0L || creditValue == null || creditValue == 0L) {
                "Débito e crédito simultâneos no mesmo lançamento."
            }
            when {
                debitValue != null && (debitValue > 0L || creditValue == null) -> ParsedPdfAmount(debitValue, TransactionType.EXPENSE)
                creditValue != null -> ParsedPdfAmount(creditValue, TransactionType.INCOME)
                else -> throw IllegalArgumentException("Tabela sem valor identificável.")
            }
        }
        require(row[descriptionIndex()].isNotBlank()) { "Lançamento sem descrição no PDF." }
        val balance = header.indexOf("saldo")
        if (balance >= 0 && row[balance].isNotBlank()) parseAmount(row[balance], false, false)
        // Preserve the original ordering of time/document/description text, including for old fingerprints.
        val descriptionText = row.indices.filter {
            !header[it].startsWith("data") && header[it] !in listOf("valor", "debito", "credito", "saldo", "tipo", "identificador", "valor estrangeiro", "moeda")
        }.map { row[it] }.filter { it.isNotBlank() }.joinToString(" ")
        val entry = RawStatementEntry(
            description = descriptionText,
            amount = Money(amount.cents), date = dates.resolve(DATE_WITH_TIME.matchEntire(row[dateIndex()])?.groupValues?.get(1) ?: row[dateIndex()]), type = amount.type,
            legacyInputType = amount.legacyType
        )
        pending = null
        return Result(true, entry, rowLines = pendingLines)
    }

    fun finish() { require(pending == null) { "Lançamento incompleto na tabela PDF. Nenhum lançamento foi importado." } }
    private fun dateIndex(): Int = header.indices.filter { header[it] in listOf("data movimento", "data mov") }.singleOrNull()
        ?: header.indices.filter { header[it] == "data" }.singleOrNull()
        ?: throw IllegalArgumentException("Não foi possível identificar a data do movimento no PDF.")
    private fun descriptionIndex(): Int = header.indexOfFirst { it in DESCRIPTION }.also { require(it >= 0) }

    companion object {
        private val DESCRIPTION = setOf("descricao", "historico", "estabelecimento", "lancamento")
        private val MONEY_COLUMNS = setOf("valor", "debito", "credito", "saldo", "valor estrangeiro")
        private val SUMMARY = Regex("(?:saldo(?: anterior| atual| final| (?:do )?dia| fatura anterior)?|total(?: da fatura(?: anterior)?| de compras| compras| para .+| do cartao.*)?|subtotal|valor total)")
        private val DATE_WITH_TIME = Regex("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})\\s*-\\s*\\d{2}:\\d{2}(?::\\d{2})?")
        private val EXPLICIT_DIRECTION = Regex("[+\\-(]|(?:credito|debito|cr|dr|c|d)$")
    }
}
