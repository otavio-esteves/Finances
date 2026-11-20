package br.com.otavioesteves.finances.data.statement

import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.StatementKind
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.repository.StatementParserRepository
import br.com.otavioesteves.finances.utils.MoneyFormatter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.math.BigDecimal
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

    override suspend fun parse(fileName: String, rawContent: ByteArray, kind: StatementKind, password: String?, referenceYear: Int?): List<RawStatementEntry> {
        val context = currentCoroutineContext()
        context.ensureActive()
        val entries = if (fileName.substringAfterLast('.', "").lowercase() in OFX_EXTENSIONS) {
            parseOfx(decodeStatementText(rawContent, ofx = true)) { context.ensureActive() }
        } else {
            parseCsv(decodeStatementText(rawContent, ofx = false), kind) { context.ensureActive() }
        }
        context.ensureActive()
        return entries
    }

    private fun parseCsv(text: String, kind: StatementKind, checkCancelled: () -> Unit): List<RawStatementEntry> {
        val content = text.removePrefix("\uFEFF")
        val rows = csvRecords(content, detectDelimiter(content, checkCancelled), checkCancelled).iterator()
        require(rows.hasNext()) { "CSV vazio ou sem linhas de transação" }

        val header = rows.next().map { it.trim().lowercase(Locale.ROOT) }
        val centsIndex = header.indexOf("valor (centavos)")
        val nativeExport = centsIndex >= 0
        val nativeHeaders = header.toSet()
        require(!nativeExport || (header.size == nativeHeaders.size &&
            (nativeHeaders == NATIVE_EXPORT_HEADERS || nativeHeaders == NATIVE_EXPORT_HEADERS + "formato finances"))) {
            "CSV em centavos não corresponde ao formato exportado pelo Finances"
        }
        val dateIndex = uniqueColumn(header, "data") { it.contains("data") || it.contains("date") }
        val descriptionIndex = uniqueColumn(header, "descrição") {
            it.contains("descri") || it.contains("histor") || it.contains("memo") || it.contains("description")
        }
        val amountIndex = uniqueColumn(header, "valor") { it.contains("valor") || it.contains("amount") || it.contains("value") }
        require(!StatementCurrency.hasForeignCurrency(header[amountIndex])) { "Coluna monetária em moeda estrangeira sem conversão em reais." }
        if (!nativeExport) StatementCurrency.requireCsvMajorUnit(header[amountIndex])
        val currencyIndices = header.indices.filter { header[it] in setOf("moeda", "currency", "curdef") }
        require(setOf(dateIndex, descriptionIndex, amountIndex).size == 3) { "Colunas ambíguas no CSV." }

        val entries = mutableListOf<RawStatementEntry>()
        while (rows.hasNext()) {
            val fields = rows.next()
            require(fields.size == header.size) {
                "Linha CSV não corresponde ao cabeçalho. Confira o separador e coloque campos com separadores entre aspas."
            }
            currencyIndices.forEach { StatementCurrency.requireBrl(fields[it]) }
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
            entries += RawStatementEntry(
                description = if (nativeExport && "formato finances" in header) {
                    require(fields[header.indexOf("formato finances")] == "2") { "Formato CSV Finances não suportado" }
                    fields[descriptionIndex].removePrefix("'")
                } else fields[descriptionIndex].trim(),
                amount = Money.fromCents(if (amountCents < 0) Math.negateExact(amountCents) else amountCents),
                date = parseCsvDate(fields[dateIndex].trim()),
                type = nativeType ?: when (kind) {
                    StatementKind.BANK_STATEMENT -> if (amountCents < 0) TransactionType.EXPENSE else TransactionType.INCOME
                    StatementKind.CARD_BILL -> if (amountCents < 0) TransactionType.INCOME else TransactionType.EXPENSE
                }
            )
        }
        require(entries.isNotEmpty()) { "CSV vazio ou sem linhas de transação" }
        return entries
    }

    private fun uniqueColumn(header: List<String>, label: String, matches: (String) -> Boolean): Int {
        val candidates = header.indices.filter {
            matches(java.text.Normalizer.normalize(header[it], java.text.Normalizer.Form.NFD).replace(HEADER_DIACRITICS, ""))
        }
        require(candidates.isNotEmpty()) { "CSV não contém a coluna esperada de $label." }
        require(candidates.size == 1) { "CSV contém mais de uma coluna de $label. Use um arquivo com uma única coluna para esse campo." }
        return candidates.single()
    }

    private fun parseOfx(text: String, checkCancelled: () -> Unit): List<RawStatementEntry> {
        val entries = ofxTransactions(text, checkCancelled).map { fields ->
            checkCancelled()
            val amountRaw = fields.amount?.trim()
                ?: throw IllegalArgumentException("Transação OFX sem TRNAMT válido")
            val amountCents = parseOfxAmountCents(amountRaw)
            val date = fields.date?.trim()
                ?: throw IllegalArgumentException("Transação OFX sem DTPOSTED válido")
            val memo = fields.memo
            val name = fields.name
            // Keep both historical parsers' first-line representations only as compatibility aliases.
            val oldMemo = memo?.substringBefore('\r')?.substringBefore('\n')?.trim()
            val oldName = name?.substringBefore('\r')?.substringBefore('\n')?.trim()
            val legacyDescription = oldMemo?.takeIf { it.isNotBlank() } ?: oldName?.takeIf { it.isNotBlank() } ?: "Transação sem descrição"
            val description = memo?.let(::decodeOfxTextEntities)?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: name?.let(::decodeOfxTextEntities)?.trim()?.takeIf { it.isNotBlank() }
                ?: "Transação sem descrição"
            // A formerly selected NAME may be invalid even when the full MEMO is valid.
            // Such a file could never have been saved by the old decoding parser.
            val legacyDecodedDescription = runCatching {
                oldMemo?.let(::decodeOfxTextEntities)?.trim()?.takeIf { it.isNotBlank() }
                    ?: oldName?.let(::decodeOfxTextEntities)?.trim()?.takeIf { it.isNotBlank() }
                    ?: "Transação sem descrição"
            }.getOrNull()

            RawStatementEntry(
                description = description,
                amount = Money.fromCents(if (amountCents < 0) Math.negateExact(amountCents) else amountCents),
                date = parseOfxDate(date),
                type = if (amountCents < 0) TransactionType.EXPENSE else TransactionType.INCOME,
                legacyOfxDescription = legacyDescription.takeIf { it != description },
                legacyDecodedOfxDescription = legacyDecodedDescription.takeIf { it != description }
            )
        }.toList()
        require(entries.isNotEmpty()) { "OFX não contém nenhuma transação (STMTTRN)" }
        return entries
    }

    private fun parseAmountCents(rawValue: String): Long {
        return MoneyFormatter.parse(rawValue)?.cents
            ?: throw IllegalArgumentException("Valor de transação inválido: $rawValue")
    }

    private fun parseOfxAmountCents(rawValue: String): Long {
        // OFX separators are decimal marks, never thousands separators.
        require(rawValue.length <= 32 && OFX_DECIMAL.matches(rawValue)) {
            "Valor OFX inválido: $rawValue"
        }
        return try {
            BigDecimal(rawValue.replace(',', '.')).movePointRight(2).longValueExact().also {
                require(it != Long.MIN_VALUE) { "Valor OFX fora do limite: $rawValue" }
            }
        } catch (error: ArithmeticException) {
            throw IllegalArgumentException("Valor OFX fora do limite ou com fração de centavo: $rawValue", error)
        }
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

    private fun detectDelimiter(text: String, checkCancelled: () -> Unit): Char {
        var insideQuotes = false
        var commas = 0
        var semicolons = 0
        var hasContent = false
        var index = 0
        var nextCancellationCheck = 0
        while (index < text.length) {
            if (index >= nextCancellationCheck) {
                checkCancelled()
                nextCancellationCheck = index + 4096
            }
            when (text[index]) {
                '"' -> {
                    if (insideQuotes && text.getOrNull(index + 1) == '"') index++
                    else insideQuotes = !insideQuotes
                }
                '\n', '\r' -> if (!insideQuotes && (hasContent || commas > 0 || semicolons > 0)) break
                ',' -> if (!insideQuotes) commas++
                ';' -> if (!insideQuotes) semicolons++
                else -> if (!text[index].isWhitespace()) hasContent = true
            }
            index++
        }
        return if (semicolons > commas) ';' else ','
    }

    /** Retain only the current row; blank records never enter an intermediate collection. */
    private fun csvRecords(text: String, delimiter: Char, checkCancelled: () -> Unit): Sequence<List<String>> = sequence {
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var insideQuotes = false
        var quoteClosed = false
        var index = 0
        var nextCancellationCheck = 0
        var nonBlankRecords = 0

        fun append(char: Char) {
            require(field.length < MAX_CSV_FIELD_BYTES) { "O campo do CSV é muito extenso. Use até 128 KiB por campo." }
            field.append(char)
        }

        fun finishField() {
            require(fields.size < MAX_CSV_COLUMNS) { "O CSV excede o limite de $MAX_CSV_COLUMNS colunas." }
            val value = field.toString()
            require(value.isEmpty() || value.toByteArray(Charsets.UTF_8).size <= MAX_CSV_FIELD_BYTES) {
                "O campo do CSV é muito extenso. Use até 128 KiB por campo."
            }
            fields += value
            field.clear()
            quoteClosed = false
        }

        suspend fun SequenceScope<List<String>>.finishRecord() {
            finishField()
            if (fields.any { it.isNotBlank() }) {
                require(++nonBlankRecords <= MAX_CSV_TRANSACTIONS + 1) {
                    "O CSV excede o limite de 50.000 lançamentos. Divida o arquivo antes de importar."
                }
                checkCancelled()
                yield(fields.toList())
            }
            fields.clear()
        }

        while (index < text.length) {
            if (index >= nextCancellationCheck) {
                checkCancelled()
                nextCancellationCheck = index + 4096
            }
            val char = text[index]
            when {
                char == '"' && insideQuotes && text.getOrNull(index + 1) == '"' -> {
                    append('"')
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
                else -> append(char)
            }
            index++
        }
        checkCancelled()
        require(!insideQuotes) { "CSV com aspas não fechadas" }
        if (field.isNotEmpty() || fields.isNotEmpty() || quoteClosed) finishRecord()
    }

    private companion object {
        const val MAX_CSV_TRANSACTIONS = 50_000
        const val MAX_CSV_COLUMNS = 64
        // Raw CSV fields may contain the protective apostrophe added by native exports.
        // Persisted descriptions still enforce their separate 64 KiB storage limit.
        const val MAX_CSV_FIELD_BYTES = 128 * 1024
        val NATIVE_EXPORT_HEADERS = setOf("id", "data", "descrição", "valor (centavos)", "tipo", "categoria", "observações")
        val UNSIGNED_CENTS = Regex("[0-9]+")
        val OFX_DECIMAL = Regex("[+-]?[0-9]+(?:[.,][0-9]+)?")
        val HEADER_DIACRITICS = Regex("\\p{M}+")
        val OFX_EXTENSIONS = setOf("ofx", "qfx")
        val BR_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
    }
}
