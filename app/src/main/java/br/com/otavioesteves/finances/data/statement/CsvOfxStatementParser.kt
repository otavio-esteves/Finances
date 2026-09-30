package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.repository.StatementParserRepository
import br.com.otavioesteves.finances.utils.MoneyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Parses bank statements exported as CSV or OFX. Format is detected by file
 * extension; OFX/QFX files are treated as OFX, everything else as CSV.
 */
class CsvOfxStatementParser : StatementParserRepository {

    override suspend fun parse(fileName: String, rawContent: ByteArray): List<RawStatementEntry> {
        val text = rawContent.toString(Charsets.UTF_8)
        return if (fileName.substringAfterLast('.', "").lowercase() in OFX_EXTENSIONS) {
            parseOfx(text)
        } else {
            parseCsv(text)
        }
    }

    private fun parseCsv(text: String): List<RawStatementEntry> {
        val content = text.removePrefix("\uFEFF")
        val rows = splitCsvRecords(content, detectDelimiter(content))
            .filter { row -> row.any { it.isNotBlank() } }
        require(rows.size >= 2) { "CSV vazio ou sem linhas de transação" }

        val header = rows.first().map { it.trim().lowercase() }
        val dateIndex = header.indexOfFirst { it.contains("data") || it.contains("date") }
        val descriptionIndex = header.indexOfFirst {
            it.contains("descri") || it.contains("histor") || it.contains("memo") || it.contains("description")
        }
        val amountIndex = header.indexOfFirst { it.contains("valor") || it.contains("amount") || it.contains("value") }

        require(dateIndex >= 0 && descriptionIndex >= 0 && amountIndex >= 0) {
            "CSV não contém as colunas esperadas de data, descrição e valor"
        }

        return rows.drop(1).map { fields ->
            require(fields.size > maxOf(dateIndex, descriptionIndex, amountIndex)) {
                "Linha CSV com menos colunas que o cabeçalho"
            }
            val amountCents = parseAmountCents(fields[amountIndex])
            RawStatementEntry(
                description = fields[descriptionIndex].trim(),
                amount = Money.fromCents(kotlin.math.abs(amountCents)),
                date = parseCsvDate(fields[dateIndex].trim()),
                type = if (amountCents < 0) TransactionType.EXPENSE else TransactionType.INCOME
            )
        }
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
            val description = OFX_MEMO_REGEX.find(block)?.groupValues?.get(1)?.trim()
                ?: OFX_NAME_REGEX.find(block)?.groupValues?.get(1)?.trim()
                ?: "Transação sem descrição"

            RawStatementEntry(
                description = description,
                amount = Money.fromCents(kotlin.math.abs(amountCents)),
                date = parseOfxDate(date),
                type = if (amountCents < 0) TransactionType.EXPENSE else TransactionType.INCOME
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
        var index = 0

        fun finishRecord() {
            fields += field.toString()
            field.clear()
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
                char == '"' -> insideQuotes = !insideQuotes
                char == delimiter && !insideQuotes -> {
                    fields += field.toString()
                    field.clear()
                }
                (char == '\r' || char == '\n') && !insideQuotes -> {
                    finishRecord()
                    if (char == '\r' && text.getOrNull(index + 1) == '\n') index++
                }
                else -> field.append(char)
            }
            index++
        }
        require(!insideQuotes) { "CSV com aspas não fechadas" }
        if (field.isNotEmpty() || fields.isNotEmpty()) finishRecord()
        return records
    }

    private companion object {
        val OFX_EXTENSIONS = setOf("ofx", "qfx")
        val BR_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
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
