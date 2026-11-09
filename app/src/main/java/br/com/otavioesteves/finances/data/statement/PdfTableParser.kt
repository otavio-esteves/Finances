package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.TransactionType

/** Parses positioned cells; an empty debit cell is different from a missing/guessed column. */
internal class PdfTableParser(
    private val dates: PdfDateResolver,
    private val parseAmount: (String, Boolean, Boolean) -> Pair<Long, TransactionType>,
    private val cardBill: Boolean
) {
    private var header = emptyList<String>()
    private var pending: MutableList<String>? = null
    private var pendingLines = 0
    val hasPending get() = pending != null

    fun process(line: String): Pair<Boolean, RawStatementEntry?> {
        if (!line.contains('\t')) {
            if (pending == null) return false to null
            require(!PdfDateResolver.DATE_LIKE_PREFIX.containsMatchIn(normalizePdfText(line))) { "Data não reconhecida na tabela PDF." }
            val description = descriptionIndex()
            pending!![description] = (pending!![description] + " " + line).trim()
            require(++pendingLines <= 6) { "Lançamento incompleto na tabela PDF." }
            return true to null
        }
        val cells = line.split('\t').map { it.trim() }
        val normalized = cells.map { normalizePdfText(it) }
        if (normalized.any { it.startsWith("data") } && normalized.any { it in DESCRIPTION } &&
            normalized.any { it in listOf("valor", "debito", "credito") }
        ) {
            require(pending == null || normalized == header) { "As colunas do PDF mudaram em um lançamento incompleto." }
            header = normalized
            dateIndex(); descriptionIndex()
            require(header.count { it in DESCRIPTION } == 1) { "Coluna de descrição ambígua no PDF." }
            require(header.count { it in listOf("data movimento", "data mov") } <= 1) { "Data de movimento ambígua no PDF." }
            require(header.count { it == "valor" } <= 1 && header.count { it == "debito" } <= 1 && header.count { it == "credito" } <= 1)
            require(("debito" in header) == ("credito" in header)) { "Tabela de débito/crédito incompleta." }
            require(("valor" in header) != ("debito" in header)) { "Colunas monetárias ambíguas no PDF." }
            return true to null
        }
        if (header.isEmpty()) return false to null
        require(cells.size == header.size) { "Linha do PDF não corresponde às colunas identificadas." }
        val date = cells[dateIndex()]
        val dateOnly = DATE_WITH_TIME.matchEntire(date)?.groupValues?.get(1) ?: date
        val description = cells[descriptionIndex()]
        val normalizedDescription = normalizePdfText(description)
        if (SUMMARY.matches(normalizedDescription) && (date.isBlank() || normalizedDescription != "total")) return true to null
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
            require(cells.indices.none { header[it] in listOf("valor", "debito", "credito") && cells[it].isNotBlank() }) {
                "Lançamento monetário sem data na tabela PDF. Nenhum lançamento foi importado."
            }
            return true to null
        }
        val row = requireNotNull(pending)
        val currency = header.indexOf("moeda")
        require(currency < 0 || row[currency].isBlank() || row[currency].uppercase() in listOf("R$", "BRL")) {
            "Lançamento em moeda estrangeira sem valor convertido em reais."
        }
        val foreign = header.indexOf("valor estrangeiro")
        if (foreign >= 0 && row[foreign].isNotBlank()) parseAmount(row[foreign], false, false)
        val valueIndex = header.indexOf("valor")
        val amount: Pair<Long, TransactionType> = if (valueIndex >= 0) {
            if (row[valueIndex].isBlank()) return true to null
            val marker = header.indexOf("tipo").takeIf { it >= 0 }?.let { row[it] }.orEmpty()
            parseAmount((row[valueIndex] + " " + marker).trim(), cardBill, true)
        } else {
            val debit = row[header.indexOf("debito")]
            val credit = row[header.indexOf("credito")]
            if (debit.isBlank() && credit.isBlank()) return true to null
            fun value(raw: String, type: TransactionType): Long? {
                if (raw.isBlank() || raw == "-" || raw == "—") return null
                val parsed = parseAmount(raw, false, false)
                val explicit = EXPLICIT_DIRECTION.containsMatchIn(normalizePdfText(raw))
                require(!explicit || parsed.second == type) { "O sinal não corresponde à coluna de débito/crédito." }
                return parsed.first
            }
            val debitValue = value(debit, TransactionType.EXPENSE)
            val creditValue = value(credit, TransactionType.INCOME)
            require(debitValue != 0L || creditValue != 0L) { "Lançamento zero sem indicação inequívoca de débito/crédito." }
            require(debitValue == null || debitValue == 0L || creditValue == null || creditValue == 0L) {
                "Débito e crédito simultâneos no mesmo lançamento."
            }
            when {
                debitValue != null && (debitValue > 0L || creditValue == null) -> debitValue to TransactionType.EXPENSE
                creditValue != null -> creditValue to TransactionType.INCOME
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
            amount = Money(amount.first), date = dates.resolve(DATE_WITH_TIME.matchEntire(row[dateIndex()])?.groupValues?.get(1) ?: row[dateIndex()]), type = amount.second
        )
        pending = null
        return true to entry
    }

    fun finish() { require(pending == null) { "Lançamento incompleto na tabela PDF. Nenhum lançamento foi importado." } }
    private fun dateIndex(): Int = header.indices.filter { header[it] in listOf("data movimento", "data mov") }.singleOrNull()
        ?: header.indices.filter { header[it] == "data" }.singleOrNull()
        ?: throw IllegalArgumentException("Não foi possível identificar a data do movimento no PDF.")
    private fun descriptionIndex(): Int = header.indexOfFirst { it in DESCRIPTION }.also { require(it >= 0) }

    companion object {
        private val DESCRIPTION = setOf("descricao", "historico", "estabelecimento", "lancamento")
        private val SUMMARY = Regex("(?:saldo(?: anterior| atual| final| (?:do )?dia| fatura anterior)?|total(?: da fatura(?: anterior)?| de compras| compras| para .+| do cartao.*)?|subtotal|valor total)")
        private val DATE_WITH_TIME = Regex("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})\\s*-\\s*\\d{2}:\\d{2}(?::\\d{2})?")
        private val EXPLICIT_DIRECTION = Regex("[+\\-(]|(?:credito|debito|cr|dr|c|d)$")
    }
}
