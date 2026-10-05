package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.StatementKind
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.repository.StatementParserRepository
import br.com.otavioesteves.finances.utils.MoneyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/**
 * Supported PDF layout: a full date, description and BRL amount, possibly
 * wrapped across up to three consecutive lines. Bank statements need a sign or C/D marker;
 * unsigned amounts are expenses only when the document identifies a card bill.
 */
class PdfStatementParser(private val textExtractor: PdfTextExtractor) : StatementParserRepository {
    override suspend fun parse(fileName: String, rawContent: ByteArray, kind: StatementKind): List<RawStatementEntry> {
        require(rawContent.size >= 5 && rawContent.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray())) {
            "O arquivo selecionado não é um PDF válido."
        }
        return parseText(textExtractor.extract(rawContent), kind)
    }

    internal fun parseText(text: String, kind: StatementKind = StatementKind.BANK_STATEMENT): List<RawStatementEntry> {
        require(text.isNotBlank()) {
            "Este PDF não contém texto selecionável. PDFs digitalizados ainda não são aceitos."
        }
        val isCardBill = kind == StatementKind.CARD_BILL || CARD_BILL_HEADER.containsMatchIn(text)
        val entries = mutableListOf<RawStatementEntry>()
        val unsupportedRows = mutableListOf<String>()

        var pendingRow: String? = null
        var pendingLines = 0
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            if (DATE_PREFIX.containsMatchIn(line)) {
                pendingRow?.let { unsupportedRows += it }
                pendingRow = line
                pendingLines = 1
            } else if (SHORT_DATE_PREFIX.containsMatchIn(line)) {
                pendingRow?.let { unsupportedRows += it }
                pendingRow = null
                if (AMOUNT_IN_DESCRIPTION.containsMatchIn(line)) unsupportedRows += line
                return@forEach
            } else if (pendingRow != null) {
                pendingRow += " $line"
                pendingLines++
            } else {
                return@forEach
            }

            val candidate = pendingRow ?: return@forEach
            val match = TRANSACTION_ROW.matchEntire(candidate)
            if (match == null) {
                if (pendingLines >= MAX_WRAPPED_ROW_LINES || AMOUNT_IN_DESCRIPTION.containsMatchIn(candidate)) {
                    unsupportedRows += candidate
                    pendingRow = null
                }
                return@forEach
            }
            pendingRow = null
            val description = match.groupValues[2].trim()
            if (SUMMARY_LABEL.matches(description)) return@forEach
            if (AMOUNT_IN_DESCRIPTION.containsMatchIn(description)) {
                unsupportedRows += candidate
                return@forEach
            }

            val rawAmount = match.groupValues[3].replace('\u00a0', ' ').trim()
            val marker = match.groupValues[4].uppercase()
            val normalizedAmount = rawAmount.replace(AMOUNT_SIGN_AFTER_CURRENCY, "$2$1").removePrefix("+")
            val amount = MoneyFormatter.parse(normalizedAmount)
                ?: throw IllegalArgumentException("Valor inválido no PDF: $rawAmount")
            val type = when {
                marker == "D" || marker == "DR" -> TransactionType.EXPENSE
                marker == "C" || marker == "CR" -> TransactionType.INCOME
                normalizedAmount.startsWith("-") -> if (isCardBill) TransactionType.INCOME else TransactionType.EXPENSE
                rawAmount.contains('+') -> TransactionType.INCOME
                isCardBill -> TransactionType.EXPENSE
                else -> throw IllegalArgumentException(
                    "PDF com valor sem sinal ou indicação de débito/crédito: $description. Use CSV/OFX ou uma fatura identificada."
                )
            }
            val date = parseDate(match.groupValues[1])
            entries += RawStatementEntry(
                description = description,
                amount = Money.fromCents(if (amount.cents < 0) Math.negateExact(amount.cents) else amount.cents),
                date = date,
                type = type
            )
        }

        pendingRow?.let { unsupportedRows += it }
        require(unsupportedRows.isEmpty()) {
            "Algumas linhas com data e valor não foram reconhecidas no PDF. Nenhum lançamento foi importado."
        }
        require(entries.isNotEmpty()) {
            "Nenhum lançamento reconhecido no PDF. Use um PDF com texto e linhas no formato data, descrição e valor."
        }
        return entries
    }

    private fun parseDate(value: String): LocalDate = runCatching {
        LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
    }.recoverCatching {
        LocalDate.parse(value.replace('-', '/'), BR_DATE)
    }.getOrElse { throw IllegalArgumentException("Data inválida no PDF: $value") }

    private companion object {
        const val MAX_WRAPPED_ROW_LINES = 3
        val BR_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
        val CARD_BILL_HEADER = Regex("(?im)^\\s*(?:fatura|cart[aã]o de cr[eé]dito)\\b")
        val DATE_PREFIX = Regex("^\\d{2}[/-]\\d{2}[/-]\\d{4}\\b|^\\d{4}-\\d{2}-\\d{2}\\b")
        val SHORT_DATE_PREFIX = Regex("^\\d{1,2}[/-]\\d{1,2}\\b")
        val TRANSACTION_ROW = Regex(
            "^(\\d{2}[/-]\\d{2}[/-]\\d{4}|\\d{4}-\\d{2}-\\d{2})\\s+(.+?)\\s+((?:[+-]?\\s*R\\$\\s*)?[+-]?\\d[\\d.]*,\\d{2})\\s*(C|D|CR|DR)?$",
            RegexOption.IGNORE_CASE
        )
        val SUMMARY_LABEL = Regex(
            "(?i)(?:total(?: da fatura| de compras)?|saldo(?: anterior| atual| final)?|pagamento m[ií]nimo|valor total|vencimento)"
        )
        val AMOUNT_IN_DESCRIPTION = Regex("(?:R\\$\\s*)?[+-]?\\d[\\d.]*,\\d{2}(?:\\s|$)")
        val AMOUNT_SIGN_AFTER_CURRENCY = Regex("^(R\\$\\s*)([+-])")
    }
}
