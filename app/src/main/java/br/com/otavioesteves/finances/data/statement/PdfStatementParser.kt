package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.StatementKind
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.repository.StatementParserRepository
import br.com.otavioesteves.finances.utils.MoneyFormatter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Parses dated BRL rows, including explicit-reference short dates and labelled running-balance tables. */
class PdfStatementParser(private val textExtractor: PdfTextExtractor) : StatementParserRepository {
    private val mutex = Mutex()
    override suspend fun parse(fileName: String, rawContent: ByteArray, kind: StatementKind, password: String?): List<RawStatementEntry> = mutex.withLock {
        require(rawContent.size >= 5 && rawContent.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray())) {
            "O arquivo selecionado não é um PDF válido."
        }
        currentCoroutineContext().ensureActive()
        val context = currentCoroutineContext()
        val extracted = textExtractor.extractWithMetadata(rawContent, password) { context.ensureActive() }
        currentCoroutineContext().ensureActive()
        parseText(extracted.text, kind, extracted.usedOcr).map { it.copy(extractedByOcr = extracted.usedOcr) }
    }

    internal fun parseText(text: String, kind: StatementKind = StatementKind.BANK_STATEMENT, strictUnmatchedMoney: Boolean = false): List<RawStatementEntry> {
        require(text.isNotBlank()) {
            "Não foi possível reconhecer texto neste PDF. Use uma digitalização mais nítida."
        }
        require(text.length <= MAX_TEXT_CHARS) { "O texto do PDF excede o limite de análise local." }
        val document = PdfStatementDocument(text)
        fun lines() = document.text.lineSequence().map { raw ->
            raw.replace('\u2212', '-').replace('\u2013', '-').replace('\u00a0', ' ')
                .split('\t').joinToString("\t") { it.replace(WHITESPACE, " ").trim() }
        }.filter { it.isNotBlank() }
        val dates = PdfDateResolver(lines().filterNot { PdfDateResolver.DATE_PREFIX.containsMatchIn(normalizePdfText(it)) })
        val isCardBill = kind == StatementKind.CARD_BILL || CARD_BILL_HEADER.containsMatchIn(document.text)
        val table = PdfTableParser(dates, { raw, cardBill, direction ->
            parseAmount(raw, cardBill, direction && !document.unsignedCredits)
        }, isCardBill)
        val entries = mutableListOf<RawStatementEntry>()
        var pendingRow: String? = null
        var pendingLines = 0
        var runningBalanceColumn = false

        fun invalidRow(): Nothing = throw IllegalArgumentException(
            "Algumas linhas com data e valor não foram reconhecidas no PDF. Nenhum lançamento foi importado."
        )

        lines().forEach { line ->
            val normalized = normalizePdfText(line)
            if (PAGE_FOOTER.matches(normalized)) return@forEach
            // Positioned extraction can split this label across the date, description and value cells.
            val subtotal = SUBTOTAL_ROW.matchEntire(normalized)?.groupValues?.get(1)
            if (subtotal != null && AMOUNT_TOKEN.matches(subtotal)) {
                if (pendingRow != null || table.hasPending) invalidRow()
                parseAmount(subtotal, isCardBill = false, requireDirection = false)
                return@forEach
            }
            if (TABLE_HEADER.containsMatchIn(normalized) && !line.contains('\t')) {
                runningBalanceColumn = VALUE_BALANCE_COLUMNS.containsMatchIn(normalized)
                return@forEach
            }
            if (line.contains('\t') || table.hasPending) {
                val (handled, entry) = table.process(line)
                if (handled) {
                    if (pendingRow != null) invalidRow()
                    entry?.let { entries += it }
                    return@forEach
                }
            }
            val datePrefix = PdfDateResolver.DATE_PREFIX.find(normalized)
            if (datePrefix != null) {
                if (pendingRow != null) invalidRow()
                pendingRow = line
                pendingLines = 1
            } else if (PdfDateResolver.DATE_LIKE_PREFIX.containsMatchIn(normalized)) {
                invalidRow()
            } else if (pendingRow != null) {
                pendingRow += " $line"
                pendingLines++
            } else {
                if (ANY_MONEY.containsMatchIn(line) && !SAFE_METADATA.containsMatchIn(normalized) &&
                    (strictUnmatchedMoney || DATE_INSIDE.containsMatchIn(normalized))) invalidRow()
                return@forEach
            }

            val candidate = requireNotNull(pendingRow)
            val dateMatch = PdfDateResolver.DATE_PREFIX.find(normalizePdfText(candidate)) ?: invalidRow()
            // Count date words instead of normalized characters (decomposed accents can change length).
            var dateEnd = 0
            val dateWords = dateMatch.value.count { it == ' ' } + 1
            repeat(dateWords) { index ->
                dateEnd = candidate.indexOf(' ', dateEnd).takeIf { it >= 0 } ?: candidate.length
                if (index < dateWords - 1 && dateEnd < candidate.length) dateEnd++
            }
            val body = candidate.substring(dateEnd).trim()
            val amounts = AMOUNT_TOKEN.findAll(body).toList()
            if (amounts.isEmpty()) {
                if (pendingLines >= MAX_WRAPPED_ROW_LINES) invalidRow()
                return@forEach
            }
            val description = body.substring(0, amounts.first().range.first).trim()
            if (SUMMARY_LABEL.matches(normalizePdfText(description))) {
                pendingRow = null
                return@forEach
            }
            if (FULL_DATE_IN_DESCRIPTION.containsMatchIn(description)) invalidRow()
            val expectedAmounts = if (runningBalanceColumn) 2 else 1
            if (amounts.size < expectedAmounts && pendingLines < MAX_WRAPPED_ROW_LINES) return@forEach
            if (description.isBlank() || amounts.size != expectedAmounts ||
                body.substring(amounts.last().range.last + 1).isNotBlank() ||
                amounts.zipWithNext().any { (first, second) -> body.substring(first.range.last + 1, second.range.first).isNotBlank() }
            ) invalidRow()
            val amount = parseAmount(amounts.first().value, isCardBill, requireDirection = !document.unsignedCredits)
            // Validate the balance column but never import it as a purchase or infer a transaction sign from it.
            if (runningBalanceColumn) parseAmount(amounts.last().value, isCardBill = false, requireDirection = false)
            val date = dates.resolve(dateMatch.value)
            entries += RawStatementEntry(description, Money.fromCents(amount.first), date, amount.second)
            pendingRow = null
        }
        if (pendingRow != null) invalidRow()
        table.finish()
        require(entries.isNotEmpty()) {
            "Nenhum lançamento reconhecido no PDF. Use um PDF com texto e linhas no formato data, descrição e valor."
        }
        document.validateTotals(entries)
        return entries.map { entry ->
            if (isCardBill && entry.type == TransactionType.INCOME && BILL_PAYMENT.containsMatchIn(normalizePdfText(entry.description)))
                entry.copy(type = TransactionType.TRANSFER)
            else entry
        }
    }

    /** Explicit signs and debit/credit markers must agree; conflicting notation is never silently overridden. */
    private fun parseAmount(raw: String, isCardBill: Boolean, requireDirection: Boolean): Pair<Long, TransactionType> {
        var value = normalizePdfText(raw).replace(" ", "").replace("r$", "")
        val marker = MARKER_SUFFIX.find(value)?.value
        if (marker != null) value = value.dropLast(marker.length)
        val parentheses = value.startsWith('(') && value.endsWith(')')
        if (parentheses) value = value.substring(1, value.length - 1)
        val signs = value.filter { it == '+' || it == '-' }
        require(signs.length <= 1 && !(parentheses && signs.isNotEmpty())) { "Sinais conflitantes no valor do PDF: $raw" }
        val sign = signs.singleOrNull()
        require(sign == null || value.first() == sign || value.last() == sign) { "Valor inválido no PDF: $raw" }
        value = value.trim('+', '-')
        val cents = MoneyFormatter.parse(value)?.cents ?: throw IllegalArgumentException("Valor inválido no PDF: $raw")
        val signedType = when {
            parentheses || sign == '-' -> if (isCardBill) TransactionType.INCOME else TransactionType.EXPENSE
            sign == '+' -> TransactionType.INCOME
            else -> null
        }
        val markedType = when (marker) {
            "d", "dr", "debito" -> TransactionType.EXPENSE
            "c", "cr", "credito" -> TransactionType.INCOME
            else -> null
        }
        require(signedType == null || markedType == null || signedType == markedType) { "Débito/crédito conflitante no PDF: $raw" }
        val type = markedType ?: signedType ?: when {
            isCardBill -> TransactionType.EXPENSE
            !requireDirection -> TransactionType.INCOME
            else -> throw IllegalArgumentException("PDF com valor sem sinal ou indicação de débito/crédito. Use CSV/OFX ou uma fatura identificada.")
        }
        return cents to type
    }

    companion object {
        const val MAX_TEXT_CHARS = 4 * 1024 * 1024
        private const val MAX_WRAPPED_ROW_LINES = 6
        private val WHITESPACE = Regex("\\s+")
        private val ANY_MONEY = Regex("\\d[\\d.,]*[.,]\\d{2}(?:\\D|$)")
        private val DATE_INSIDE = Regex("\\b\\d{1,2}[./-]\\d{1,2}\\b")
        private val FULL_DATE_IN_DESCRIPTION = Regex("\\b(?:\\d{1,2}[./-]\\d{1,2}[./-]\\d{4}|\\d{4}-\\d{2}-\\d{2})\\b")
        private val SAFE_METADATA = Regex("^(?:fatura|periodo|referencia|emissao|vencimento|saldo|total|valor total|pagamento minimo|limite|juros|encargos)\\b")
        private val CARD_BILL_HEADER = Regex("(?im)^\\s*(?:fatura|cart[aã]o de cr[eé]dito)\\b")
        private val TABLE_HEADER = Regex("^data\\s+.*(?:descricao|historico|lancamento|estabelecimento).*(?:valor|debito|credito)\\b")
        private val VALUE_BALANCE_COLUMNS = Regex("\\bvalor\\b.*\\bsaldo\\b(?:\\s*\\(?r\\$\\)?)?\\s*$")
        private val PAGE_FOOTER = Regex("(?:pagina\\s+\\d+\\s*(?:de|/)\\s*\\d+|\\d+\\s+de\\s+\\d+)")
        private val SUBTOTAL_ROW = Regex("^subtotal dos lancamentos\\s+(.+)$")
        private val SUMMARY_LABEL = Regex("(?:total(?: da fatura| de compras)?|saldo(?: anterior| atual| final| do dia)?|pagamento minimo|valor total|vencimento)")
        private val BILL_PAYMENT = Regex("^pagamento\\s+(?:(?:de|da)\\s+fatura\\b|em\\s+\\d{1,2}\\s+[a-z]{3}\\b)")
        private val MARKER_SUFFIX = Regex("(?:credito|debito|cr|dr|c|d)$")
        private val AMOUNT_TOKEN = Regex(
            "(?<!\\S)(?:[+-]\\s*)?(?:R\\$\\s*)?(?:[+-]\\s*)?(?:\\(\\s*)?\\d[\\d.]*,\\d{2}(?:\\s*\\))?(?:\\s*[+-])?(?:\\s*(?:credito|crédito|debito|débito|CR|DR|C|D))?(?=\\s|$)",
            RegexOption.IGNORE_CASE
        )
    }
}
