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
import java.util.Locale

/**
 * Parses CSV bills/statements and signed OFX transactions. The selected kind
 * controls bank CSV sign interpretation; native exports preserve cents and type.
 * OFX keeps its own signed amount convention.
 */
class CsvOfxStatementParser : StatementParserRepository {

    override suspend fun parse(fileName: String, rawContent: ByteArray, kind: StatementKind, password: String?): List<RawStatementEntry> {
        return if (fileName.substringAfterLast('.', "").lowercase() in OFX_EXTENSIONS) {
            parseOfx(decodeStatementText(rawContent, ofx = true))
        } else {
            parseCsv(decodeStatementText(rawContent, ofx = false), kind)
        }
    }

    private fun parseCsv(text: String, kind: StatementKind): List<RawStatementEntry> {
        val content = text.removePrefix("\uFEFF")
        val rows = splitCsvRecords(content, detectDelimiter(content))
            .filter { row -> row.any { it.isNotBlank() } }
        require(rows.size >= 2) { "CSV vazio ou sem linhas de transação" }

        val header = rows.first().map { it.trim().lowercase(Locale.ROOT) }
        val centsIndex = header.indexOf("valor (centavos)")
        val nativeExport = centsIndex >= 0
        require(!nativeExport || (header.size == NATIVE_EXPORT_HEADERS.size && header.toSet() == NATIVE_EXPORT_HEADERS)) {
            "CSV em centavos não corresponde ao formato exportado pelo Finances"
        }
        val dateIndex = uniqueColumn(header, "data") { it.contains("data") || it.contains("date") }
        val descriptionIndex = uniqueColumn(header, "descrição") {
            it.contains("descri") || it.contains("histor") || it.contains("memo") || it.contains("description")
        }
        val amountIndex = uniqueColumn(header, "valor") { it.contains("valor") || it.contains("amount") || it.contains("value") }
        require(setOf(dateIndex, descriptionIndex, amountIndex).size == 3) { "Colunas ambíguas no CSV." }

        return rows.drop(1).map { fields ->
            require(fields.size == header.size) {
                "Linha CSV não corresponde ao cabeçalho. Confira o separador e coloque campos com separadores entre aspas."
            }
            val amountCents = if (nativeExport) {
                fields[centsIndex].trim().let { raw ->
                    require(UNSIGNED_CENTS.matches(raw)) { "Valor em centavos inválido: $raw" }
                    raw.toLongOrNull() ?: throw IllegalArgumentException("Valor em centavos fora do limite: $raw")
                }
            } else parseAmountCents(fields[amountIndex])
            val nativeType = if (nativeExport) {
                val raw = fields[header.indexOf("tipo")].trim()
                TransactionType.entries.firstOrNull { it.name == raw }
                    ?: throw IllegalArgumentException("Tipo de transação inválido no CSV exportado: $raw")
            } else null
            RawStatementEntry(
                description = fields[descriptionIndex].trim(),
                amount = Money.fromCents(if (amountCents < 0) Math.negateExact(amountCents) else amountCents),
                date = parseCsvDate(fields[dateIndex].trim()),
                type = nativeType ?: when (kind) {
                    StatementKind.BANK_STATEMENT -> if (amountCents < 0) TransactionType.EXPENSE else TransactionType.INCOME
                    StatementKind.CARD_BILL -> if (amountCents < 0) TransactionType.INCOME else TransactionType.EXPENSE
                }
            )
        }
    }

    private fun uniqueColumn(header: List<String>, label: String, matches: (String) -> Boolean): Int {
        val candidates = header.indices.filter {
            matches(java.text.Normalizer.normalize(header[it], java.text.Normalizer.Form.NFD).replace(HEADER_DIACRITICS, ""))
        }
        require(candidates.isNotEmpty()) { "CSV não contém a coluna esperada de $label." }
        require(candidates.size == 1) { "CSV contém mais de uma coluna de $label. Use um arquivo com uma única coluna para esse campo." }
        return candidates.single()
    }

    private fun parseOfx(text: String): List<RawStatementEntry> {
        val transactionBlocks = OFX_TRANSACTION_REGEX.findAll(text).map { it.value }.toList()
        require(transactionBlocks.isNotEmpty()) { "OFX não contém nenhuma transação (STMTTRN)" }

        return transactionBlocks.map { block ->
            val amountRaw = OFX_AMOUNT_REGEX.find(block)?.groupValues?.get(1)?.trim()
                ?: throw IllegalArgumentException("Transação OFX sem TRNAMT válido")
            val amountCents = parseAmountCents(amountRaw)
            val date = OFX_DATE_REGEX.find(block)?.groupValues?.get(1)?.trim()
                ?: throw IllegalArgumentException("Transação OFX sem DTPOSTED válido")
            val memo = OFX_MEMO_REGEX.find(block)?.groupValues?.get(1)?.trim()
            val name = OFX_NAME_REGEX.find(block)?.groupValues?.get(1)?.trim()
            val legacyDescription = memo?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: "Transação sem descrição"
            val description = memo?.let(::decodeOfxTextEntities)?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: name?.let(::decodeOfxTextEntities)?.trim()?.takeIf { it.isNotBlank() }
                ?: "Transação sem descrição"

            RawStatementEntry(
                description = description,
                amount = Money.fromCents(if (amountCents < 0) Math.negateExact(amountCents) else amountCents),
                date = parseOfxDate(date),
                type = if (amountCents < 0) TransactionType.EXPENSE else TransactionType.INCOME,
                legacyOfxDescription = legacyDescription.takeIf { it != description }
            )
        }
    }

    private fun parseAmountCents(rawValue: String): Long {
        return MoneyFormatter.parse(rawValue)?.cents
            ?: throw IllegalArgumentException("Valor de transação inválido: $rawValue")
    }

    private fun parseCsvDate(rawValue: String): LocalDate {
        return runCatching { LocalDate.parse(rawValue, DateTimeFormatter.ISO_LOCAL_DATE) }
            .recoverCatching { LocalDate.parse(rawValue, BR_DATE_FORMAT) }
            .getOrElse { throw IllegalArgumentException("Data de transação inválida: $rawValue") }
    }

    private fun parseOfxDate(rawValue: String): LocalDate {
        val digitsOnly = rawValue.takeWhile { it.isDigit() }
        require(digitsOnly.length >= 8) { "Data OFX inválida: $rawValue" }
        return LocalDate.parse(digitsOnly.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE)
    }

    private fun detectDelimiter(text: String): Char {
        var insideQuotes = false
        var commas = 0
        var semicolons = 0
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '"' -> {
                    if (insideQuotes && text.getOrNull(index + 1) == '"') index++
                    else insideQuotes = !insideQuotes
                }
                '\n', '\r' -> if (!insideQuotes) break
                ',' -> if (!insideQuotes) commas++
                ';' -> if (!insideQuotes) semicolons++
            }
            index++
        }
        return if (semicolons > commas) ';' else ','
    }

    private fun splitCsvRecords(text: String, delimiter: Char): List<List<String>> {
        val records = mutableListOf<List<String>>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var insideQuotes = false
        var quoteClosed = false
        var index = 0

        fun finishField() {
            fields += field.toString()
            field.clear()
            quoteClosed = false
        }

        fun finishRecord() {
            finishField()
            records += fields.toList()
            fields.clear()
        }

        while (index < text.length) {
            val char = text[index]
            when {
                char == '"' && insideQuotes && text.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' && insideQuotes -> {
                    insideQuotes = false
                    quoteClosed = true
                }
                char == '"' -> {
                    require(field.isEmpty() && !quoteClosed) { "CSV com aspas fora do início do campo." }
                    insideQuotes = true
                }
                char == delimiter && !insideQuotes -> {
                    finishField()
                }
                (char == '\r' || char == '\n') && !insideQuotes -> {
                    finishRecord()
                    if (char == '\r' && text.getOrNull(index + 1) == '\n') index++
                }
                quoteClosed -> require(char == ' ' || char == '\t') { "CSV com texto após o fechamento das aspas." }
                else -> field.append(char)
            }
            index++
        }
        require(!insideQuotes) { "CSV com aspas não fechadas" }
        if (field.isNotEmpty() || fields.isNotEmpty() || quoteClosed) finishRecord()
        return records
    }

    private companion object {
        val NATIVE_EXPORT_HEADERS = setOf("id", "data", "descrição", "valor (centavos)", "tipo", "categoria", "observações")
        val UNSIGNED_CENTS = Regex("[0-9]+")
        val HEADER_DIACRITICS = Regex("\\p{M}+")
        val OFX_EXTENSIONS = setOf("ofx", "qfx")
        val BR_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
        // OFX is SGML-based and closing tags (e.g. </STMTTRN>) are often omitted,
        // so a transaction block ends at the next STMTTRN, the list closing tag, or EOF.
        val OFX_TRANSACTION_REGEX = Regex(
            "<STMTTRN>(.*?)(?=<STMTTRN>|</BANKTRANLIST>|\\z)",
            RegexOption.DOT_MATCHES_ALL
        )
        val OFX_AMOUNT_REGEX = Regex("<TRNAMT>([^<\\r\\n]*)")
        val OFX_DATE_REGEX = Regex("<DTPOSTED>([^<\\r\\n]*)")
        val OFX_MEMO_REGEX = Regex("<MEMO>([^<\\r\\n]*)")
        val OFX_NAME_REGEX = Regex("<NAME>([^<\\r\\n]*)")
    }
}
